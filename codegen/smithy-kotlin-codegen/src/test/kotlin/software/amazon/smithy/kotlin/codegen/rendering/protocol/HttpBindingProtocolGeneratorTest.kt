/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.matchers.string.shouldNotContain
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.kotlin.codegen.core.RUNTIME_ROOT_NS
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import kotlin.test.Test
import kotlin.test.assertTrue

// NOTE: protocol conformance is mostly handled by the protocol tests suite
class HttpBindingProtocolGeneratorTest {
    private val defaultModel = loadModelFromResource("http-binding-protocol-generator-test.smithy")
    private fun getSerdeFileContents(filename: String, testModel: Model = defaultModel): String {
        val (ctx, manifest, generator) = testModel.newTestContext()
        generator.generateProtocolClient(ctx)
        ctx.delegator.flushWriters()
        return getSerdeFileContents(manifest, filename)
    }

    private fun getSerdeFileContents(manifest: MockManifest, filename: String): String = manifest
        .expectFileString("src/main/kotlin/com/test/serde/$filename")

    @Test
    fun itCreatesSerializeTransformsInCorrectPackage() {
        val (ctx, manifest, generator) = defaultModel.newTestContext()
        generator.generateProtocolClient(ctx)
        ctx.delegator.flushWriters()
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/serde/SmokeTestOperationSerializer.kt"))
    }

    @Test
    fun itCreatesSmokeTestRequestSerializer() {
        val contents = getSerdeFileContents("SmokeTestOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val label1 = "\${input.label1}" // workaround for raw strings not being able to contain escapes
        val expectedContents = """
internal class SmokeTestOperationSerializer: HttpSerialize<SmokeTestRequest> {
    override suspend fun serialize(context: ExecutionContext, input: SmokeTestRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            val pathSegments = listOf<String>(
                "smoketest",
                "$label1".encodeLabel(),
                "foo",
            )
            path = pathSegments.joinToString(separator = "/", prefix = "/")
            parameters {
                if (input.query1 != null) append("Query1", input.query1)
            }
        }

        builder.headers {
            if (input.header1?.isNotEmpty() == true) append("X-Header1", input.header1)
            if (input.header2?.isNotEmpty() == true) append("X-Header2", input.header2)
        }

        val payload = serializeSmokeTestOperationBody(context, input)
        builder.body = HttpBody.fromBytes(payload)
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "application/json")
        }
        return builder
    }
}
"""
        // NOTE: SmokeTestRequest$payload3 is a struct itself, the Serializer interface handles this if the type
        // implements `SdkSerializable`
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itSerializesExplicitStringPayloads() {
        val contents = getSerdeFileContents("ExplicitStringOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class ExplicitStringOperationSerializer: HttpSerialize<ExplicitStringRequest> {
    override suspend fun serialize(context: ExecutionContext, input: ExplicitStringRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/string"
        }

        if (input.payload1 != null) {
            builder.body = HttpBody.fromBytes(input.payload1.encodeToByteArray())
        }
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "text/plain")
        }
        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itSerializesExplicitBlobPayloads() {
        val contents = getSerdeFileContents("ExplicitBlobOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class ExplicitBlobOperationSerializer: HttpSerialize<ExplicitBlobRequest> {
    override suspend fun serialize(context: ExecutionContext, input: ExplicitBlobRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/blob"
        }

        if (input.payload1 != null) {
            builder.body = HttpBody.fromBytes(input.payload1)
        }
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "application/octet-stream")
        }
        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itSerializesExplicitStreamingBlobPayloads() {
        val contents = getSerdeFileContents("ExplicitBlobStreamOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class ExplicitBlobStreamOperationSerializer: HttpSerialize<ExplicitBlobStreamRequest> {
    override suspend fun serialize(context: ExecutionContext, input: ExplicitBlobStreamRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/blobstream"
        }

        if (input.payload1 != null) {
            builder.body = input.payload1.toHttpBody()
        }
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "application/octet-stream")
        }
        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itSerializesExplicitStructPayloads() {
        val contents = getSerdeFileContents("ExplicitStructOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class ExplicitStructOperationSerializer: HttpSerialize<ExplicitStructRequest> {
    override suspend fun serialize(context: ExecutionContext, input: ExplicitStructRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/struct"
        }

        if (input.payload1 != null) {
            val payload = serializeNested2Payload(input.payload1)
            builder.body = HttpBody.fromBytes(payload)
        }
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "application/json")
        }
        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itSerializesExplicitDocumentPayloads() {
        val contents = getSerdeFileContents("ExplicitDocumentOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class ExplicitDocumentOperationSerializer: HttpSerialize<ExplicitDocumentRequest> {
    override suspend fun serialize(context: ExecutionContext, input: ExplicitDocumentRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/document"
        }

        if (input.payload1 != null) {
            val payload = serializeDocumentPayload(input.payload1)
            builder.body = HttpBody.fromBytes(payload)
        }
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "application/json")
        }
        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itSerializesOperationInputsWithEnums() {
        val contents = getSerdeFileContents("EnumInputOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class EnumInputOperationSerializer: HttpSerialize<EnumInputRequest> {
    override suspend fun serialize(context: ExecutionContext, input: EnumInputRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/enum"
        }

        builder.headers {
            if (input.enumHeader != null) append("X-EnumHeader", input.enumHeader.value)
        }

        val payload = serializeEnumInputOperationBody(context, input)
        builder.body = HttpBody.fromBytes(payload)
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "application/json")
        }
        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itSerializesOperationInputsWithTimestamps() {
        val contents = getSerdeFileContents("TimestampInputOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val tsLabel = "\${input.tsLabel.format(TimestampFormat.ISO_8601)}" // workaround for raw strings not being able to contain escapes
        val expectedContents = """
internal class TimestampInputOperationSerializer: HttpSerialize<TimestampInputRequest> {
    override suspend fun serialize(context: ExecutionContext, input: TimestampInputRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            val pathSegments = listOf<String>(
                "input",
                "timestamp",
                "$tsLabel".encodeLabel(),
            )
            path = pathSegments.joinToString(separator = "/", prefix = "/")
            parameters {
                if (input.queryTimestamp != null) append("qtime", input.queryTimestamp.format(TimestampFormat.ISO_8601))
                if (input.queryTimestampList?.isNotEmpty() == true) appendAll("qtimeList", input.queryTimestampList.map { it.format(TimestampFormat.ISO_8601) })
            }
        }

        builder.headers {
            if (input.headerDateTime != null) append("X-DateTime", input.headerDateTime.format(TimestampFormat.ISO_8601))
            if (input.headerEpoch != null) append("X-Epoch", input.headerEpoch.format(TimestampFormat.EPOCH_SECONDS))
            if (input.headerHttpDate != null) append("X-Date", input.headerHttpDate.format(TimestampFormat.RFC_5322))
        }

        val payload = serializeTimestampInputOperationBody(context, input)
        builder.body = HttpBody.fromBytes(payload)
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "application/json")
        }
        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
        contents.shouldContainOnlyOnce("import $RUNTIME_ROOT_NS.time.TimestampFormat")
    }

    @Test
    fun itCreatesBlobInputRequestSerializer() {
        // base64 encoding is protocol dependent. The mock protocol generator is based on
        // json protocol though which does encode to base64
        val contents = getSerdeFileContents("BlobInputOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class BlobInputOperationSerializer: HttpSerialize<BlobInputRequest> {
    override suspend fun serialize(context: ExecutionContext, input: BlobInputRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/blob"
        }

        builder.headers {
            if (input.headerMediaType?.isNotEmpty() == true) append("X-Blob", input.headerMediaType.encodeBase64())
        }

        val payload = serializeBlobInputOperationBody(context, input)
        builder.body = HttpBody.fromBytes(payload)
        if (builder.body !is HttpBody.Empty) {
            builder.headers.setMissing("Content-Type", "application/json")
        }
        return builder
    }
}
"""
        // NOTE: SmokeTestRequest$payload3 is a struct itself, the Serializer interface handles this if the type
        // implements `SdkSerializable`
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itHandlesQueryStringLiterals() {
        val contents = getSerdeFileContents("ConstantQueryStringOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val label1 = "\${input.hello}" // workaround for raw strings not being able to contain escapes
        val expectedContents = """
internal class ConstantQueryStringOperationSerializer: HttpSerialize<ConstantQueryStringRequest> {
    override suspend fun serialize(context: ExecutionContext, input: ConstantQueryStringRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.GET

        builder.url {
            val pathSegments = listOf<String>(
                "ConstantQueryString",
                "$label1".encodeLabel(),
            )
            path = pathSegments.joinToString(separator = "/", prefix = "/")
            parameters {
                append("foo", "bar")
                append("hello", "")
            }
        }

        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itCreatesSmokeTestResponseDeserializer() {
        val contents = getSerdeFileContents("SmokeTestOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class SmokeTestOperationDeserializer: HttpDeserialize<SmokeTestResponse> {

    override suspend fun deserialize(context: ExecutionContext, call: HttpCall): SmokeTestResponse {
        val response = call.response
        if (!response.status.isSuccess()) {
            throwSmokeTestError(context, call)
        }
        val builder = SmokeTestResponse.Builder()

        builder.intHeader = response.headers["X-Header2"]?.toInt()
        builder.strHeader = response.headers["X-Header1"]
        builder.tsListHeader = response.headers.getAll("X-Header3")?.flatMap(::splitHttpDateHeaderListValues)?.map { Instant.fromRfc5322(it) }

        val payload = response.body.readAll()
        if (payload != null) {
            deserializeSmokeTestOperationBody(builder, payload)
        }
        builder.correctErrors()
        return builder.build()
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itDeserializesPrefixHeaders() {
        val contents = getSerdeFileContents("PrefixHeadersOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
        val keysForMember1 = response.headers.names().filter { it.startsWith("X-Foo-") }
        if (keysForMember1.isNotEmpty()) {
            val map = mutableMapOf<String, String>()
            for (hdrKey in keysForMember1) {
                val el = response.headers[hdrKey] ?: continue
                val key = hdrKey.removePrefix("X-Foo-")
                map[key] = el
            }
            builder.member1 = map
        } else {
            builder.member1 = emptyMap()
        }
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itDeserializesPrimitiveHeaders() {
        val contents = getSerdeFileContents("PrimitiveShapesOperationOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
        builder.hBool = response.headers["X-d"]?.toBoolean() ?: false
        builder.hFloat = response.headers["X-c"]?.toFloat() ?: 0f
        builder.hInt = response.headers["X-a"]?.toInt() ?: 0
        builder.hLong = response.headers["X-b"]?.toLong() ?: 0L
        builder.hRequiredInt = response.headers["X-required"]?.toInt() ?: 0
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itDeserializesExplicitStringPayloads() {
        val contents = getSerdeFileContents("ExplicitStringOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
        val contents = response.body.readAll()?.decodeToString()
        builder.payload1 = contents
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun itDeserializesExplicitEnumPayloads() {
        val contents = getSerdeFileContents("ExplicitEnumOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
        val contents = response.body.readAll()?.decodeToString()
        builder.payload1 = contents?.let { MyEnum.fromValue(it) }
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun itDeserializesExplicitBlobPayloads() {
        val contents = getSerdeFileContents("ExplicitBlobOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
        builder.payload1 = response.body.readAll()
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun itDeserializesExplicitStreamingBlobPayloads() {
        val contents = getSerdeFileContents("ExplicitBlobStreamOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
        builder.payload1 = response.body.toByteStream()
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun itDeserializesExplicitStructPayloads() {
        val contents = getSerdeFileContents("ExplicitStructOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
        val payload = response.body.readAll()
        if (payload != null) {
            builder.payload1 = deserializeNested2Payload(payload)
        }
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itDeserializesExplicitDocumentPayloads() {
        val contents = getSerdeFileContents("ExplicitDocumentOperationDeserializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
        val payload = response.body.readAll()
        if (payload != null) {
            builder.payload1 = deserializeDocumentPayload(payload)
        }
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun itLeavesOffContentType() {
        // GET/HEAD/TRACE/OPTIONS/CONNECT shouldn't specify content-type
        val contents = getSerdeFileContents("ConstantQueryStringOperationSerializer.kt")
        contents.shouldNotContain("Content-Type")
    }

    @Test
    fun itEscapesUriLiterals() {
        // https://github.com/awslabs/smithy-kotlin/issues/65
        // https://github.com/awslabs/smithy-kotlin/issues/395
        val uri = "/test/\$LATEST"
        val model = """
            @http(method: "PUT", uri: "$uri", code: 200)
            operation Foo{ }
        """.prependNamespaceAndService(operations = listOf("Foo"))
            .toSmithyModel()

        val contents = getSerdeFileContents("FooOperationSerializer.kt", model)

        val latest = "\\\$LATEST"
        val expected = """
            path = "/test/$latest"
        """
        contents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun itValidatesRequiredAndNonBlankUriBindings() {
        val model = """
            @http(method: "POST", uri: "/foo/{bar}/{baz}")
            operation Foo {
                input: FooRequest
            }

            @input
            structure FooRequest {
                @required
                @length(min: 3)
                @httpLabel
                bar: String,

                @httpLabel
                @required
                baz: Integer,

                @httpPayload
                qux: String,

                @required
                @httpQuery("quux")
                quux: Boolean,

                @httpQuery("corge")
                corge: String,

                @required
                @length(min: 0)
                @httpQuery("grault")
                grault: String,

                @required
                @length(min: 3)
                @httpQuery("garply")
                garply: String
            }
            
        """.prependNamespaceAndService(operations = listOf("Foo"))
            .toSmithyModel()

        val contents = getSerdeFileContents("FooOperationSerializer.kt", model)

        val label1 = "\${input.bar}"
        val label2 = "\${input.baz}"
        val quux = "\${input.quux}"
        val expected = """
            requireNotNull(input.bar) { "bar is bound to the URI and must not be null" }
            require(input.bar?.isNotBlank() == true) { "bar is bound to the URI and must be a non-blank value" }
            requireNotNull(input.baz) { "baz is bound to the URI and must not be null" }
            val pathSegments = listOf<String>(
                "foo",
                "$label1".encodeLabel(),
                "$label2".encodeLabel(),
            )
            path = pathSegments.joinToString(separator = "/", prefix = "/")
            parameters {
                require(input.garply?.isNotBlank() == true) { "garply is bound to the URI and must be a non-blank value" }
                if (input.corge != null) append("corge", input.corge)
                if (input.garply != null) append("garply", input.garply)
                if (input.grault != null) append("grault", input.grault)
                if (input.quux != null) append("quux", "$quux")
            }
        """

        contents.shouldContainOnlyOnceWithDiff(expected)
    }
}
