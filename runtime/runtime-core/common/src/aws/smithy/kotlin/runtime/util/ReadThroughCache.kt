/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * An object which caches values and allows retrieving them by key. The values expire after a time. If a value is
 * expired or absent from the cache, it will be read from a `valueLookup` parameter passed to [get] and then cached.
 *
 * A sweep operation will run prior to a [get] or [invalidate] that happens after [minimumSweepPeriod] has elapsed from
 * the last sweep (or from the initialization of the cache). This sweep will search for and remove expired entries from
 * the cache.
 *
 * Accesses to this cache are thread-safe via a mutex. All [get], [invalidate], and sweep operations will wait for prior
 * invocations to complete. Note that the [size] property is non-volatile and so may return stale information.
 *
 * @param K The type of the keys of this cache
 * @param V The type of the values of this cache
 * @param minimumSweepPeriod The minimum time between sweeps. Sweeps may occur after longer durations if no reads occur
 * after [minimumSweepPeriod] has elapsed.
 * @param clock The [Clock] to use for measuring time. Defaults to [Clock.System].
 */
@InternalApi
public class ReadThroughCache<K, V>(
    private val minimumSweepPeriod: Duration,
    private val clock: Clock = Clock.System,
) {
    private val map = mutableMapOf<K, ExpiringValue<V>>()
    private val mutex = Mutex()
    private var nextSweep = clock.now() + minimumSweepPeriod

    /**
     * Gets the value associated with this key from the cache. If the cache does not contain the value or the value is
     * expired, it will be read-through from the [valueLookup] function.
     * @param key The key for which to look up a value.
     * @param valueLookup A possibly-suspending function which returns the read-through value associated with a given
     * key. This function is invoked when the cache, for a given key, does not contain a value or the value is expired.
     */
    public suspend fun get(key: K, valueLookup: suspend (K) -> ExpiringValue<V>): V = mutex.withLock {
        if (clock.now() > nextSweep) sweep()

        val current = map[key]
        return if (current == null || current.isExpired) {
            valueLookup(key).also { map[key] = it }.value
        } else {
            current.value
        }
    }

    /**
     * Invalidates the value (if any) for the given key, removing it from the cache regardless of its expiry.
     * @param key The key for which to invalidate a value.
     */
    public suspend fun invalidate(key: K): Unit = mutex.withLock {
        map.remove(key)
        if (clock.now() > nextSweep) sweep()
    }

    private val ExpiringValue<*>.isExpired: Boolean
        get() = clock.now() >= expiresAt

    /**
     * Gets the number of values currently stored in the cache.
     */
    public val size: Int
        get() = map.size

    private fun sweep() {
        val iterator = map.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.isExpired) {
                iterator.remove()
            }
        }
        nextSweep = clock.now() + minimumSweepPeriod
    }
}
