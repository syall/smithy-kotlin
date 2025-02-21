/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.ClientException

/**
 * LogMode represents the logging mode of SDK clients. The mode is backed by a bit-field where each
 * bit is a flag (mode) that describes the logging behavior for one or more client components.
 *
 * Example: Setting log mode to enable logging of requests and responses
 * ```
 * val mode = LogMode.LogRequest + LogMode.LogResponse
 * ```
 */
public sealed class LogMode(private val mask: Int) {
    /**
     * The default logging mode which does not opt-in to anything
     */
    public object Default : LogMode(0x00) {
        override fun toString(): String = "Default"
    }

    /**
     * Log the request details, e.g. url, headers, etc.
     */
    public object LogRequest : LogMode(0x01) {
        override fun toString(): String = "LogRequest"
    }

    /**
     * Log the request details as well as the body if possible
     */
    public object LogRequestWithBody : LogMode(0x02) {
        override fun toString(): String = "LogRequestWithBody"
    }

    /**
     * Log the response details, e.g. status, headers, etc
     */
    public object LogResponse : LogMode(0x04) {
        override fun toString(): String = "LogResponse"
    }

    /**
     * Log the response details as well as the body if possible
     */
    public object LogResponseWithBody : LogMode(0x08) {
        override fun toString(): String = "LogResponseWithBody"
    }

    internal class Composite(mask: Int) : LogMode(mask)

    public operator fun plus(mode: LogMode): LogMode = Composite(mask or mode.mask)
    public operator fun minus(mode: LogMode): LogMode = Composite(mask and mode.mask.inv())

    public override fun equals(other: Any?): Boolean = (other is LogMode) && (mask == other.mask)

    /**
     * Test if a particular [LogMode] is enabled
     */
    public fun isEnabled(mode: LogMode): Boolean = mask and mode.mask != 0

    public companion object {
        /**
         * Get a list of all modes
         */
        public fun allModes(): List<LogMode> = listOf(
            LogRequest,
            LogRequestWithBody,
            LogResponse,
            LogResponseWithBody,
        )

        /**
         * Parse a [LogMode] from a String
         * @return the parsed LogMode
         * @throws ClientException if the LogMode could not be parsed
         */
        public fun fromString(string: String): LogMode =
            string
                .trim()
                .split("|")
                .map { logModeString ->
                    allModes().firstOrNull { logMode -> logModeString.equals(logMode.toString(), ignoreCase = true) }
                        ?: throw ClientException("Log mode $logModeString is not supported, should be one or more of: ${allModes().joinToString(", ")}")
                }
                .reduce { acc, logMode -> acc + logMode }
    }

    override fun toString(): String =
        allModes()
            .filter { isEnabled(it) }
            .joinToString("|")
}
