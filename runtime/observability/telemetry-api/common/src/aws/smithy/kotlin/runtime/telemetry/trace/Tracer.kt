/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * Entry point for creating [TraceSpan] instances.
 */
public interface Tracer {
    /**
     * Creates a new span and makes it active in the current [Context].
     *
     * @param name the name of the span
     * @param parentContext the parent context to use for this span, if not set it is
     * implementation defined.
     */
    public fun createSpan(
        name: String,
        initialAttributes: Attributes = emptyAttributes(),
        spanKind: SpanKind = SpanKind.INTERNAL,
        parentContext: Context? = null,
    ): TraceSpan
}
