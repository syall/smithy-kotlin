/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.assertBalancedBracesAndParens
import software.amazon.smithy.kotlin.codegen.test.formatForTest
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import kotlin.test.*

// this is a simplified ruleset test to verify that the core constructs are generated properly
// the services ship with test cases that are far more comprehensive which actually test the BEHAVIOR of this generated code
class DefaultEndpointProviderGeneratorTest {
    private val generatedClass: String

    init {
        val rules = createTestRuleSet(
            """
                {
                    "version": "1.1",
                    "parameters": {
                        "ResourceId": {
                            "type": "string",
                            "required": true
                        },
                        "BazName": {
                            "type": "string",
                            "required": false
                        },
                        "QuxName": {
                            "type": "string",
                            "required": false
                        }
                    },
                    "rules": [
                        {
                            "documentation": "basic condition",
                            "type": "endpoint",
                            "conditions": [
                                {
                                    "fn": "isSet",
                                    "argv": [
                                        {"ref": "BazName"}
                                    ]
                                },
                                {
                                    "fn": "stringEquals",
                                    "argv": [
                                        {"ref": "BazName"},
                                        "gov"
                                    ]
                                }
                            ],
                            "endpoint": {
                                "url": "https://basic.condition"
                            }
                        },
                        {
                            "documentation": "assignment condition",
                            "type": "endpoint",
                            "conditions": [
                                {
                                    "fn": "substring",
                                    "argv": [ {"ref": "ResourceId"}, 0, 4, false ],
                                    "assign": "resourceIdPrefix"
                                },
                                {
                                    "fn": "stringEquals",
                                    "argv": [
                                        {"ref": "resourceIdPrefix"},
                                        "gov.{ResourceId}"
                                    ]
                                }
                            ],
                            "endpoint": {
                                "url": "https://assignment.condition"
                            }
                        },
                        {
                            "documentation": "throw exception if bad value",
                            "type": "error",
                            "conditions": [
                                {
                                    "fn": "isSet",
                                    "argv": [
                                        {"ref": "BazName"}
                                    ]
                                },
                                {
                                    "fn": "stringEquals",
                                    "argv": [
                                        {"ref": "BazName"},
                                        "invalid"
                                    ]
                                }
                            ],
                            "error": "invalid BazName value"
                        },
                        {
                            "documentation": "fallback to global endpoint",
                            "type": "endpoint",
                            "conditions": [],
                            "endpoint": {
                                "url": "https://global.api",
                                "properties": {
                                    "foo": "bar",
                                    "fooInt": 7,
                                    "fooBoolean": true,
                                    "fooObject": {
                                        "fooObjectFoo": "bar"
                                    },
                                    "fooArray": [
                                        "\"fooArrayBar\""
                                    ]
                                },
                                "headers": {
                                    "fooheader": ["barheader"]
                                }
                            }
                        }
                    ]
                }
            """,
        )

        val paramsSymbol = buildSymbol {
            name = "EndpointParameters"
            namespace = TestModelDefault.NAMESPACE
        }
        val interfaceSymbol = buildSymbol {
            name = "EndpointProvider"
            namespace = TestModelDefault.NAMESPACE
        }
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val defaultSymbol = buildSymbol {
            name = "DefaultEndpointProvider"
            namespace = TestModelDefault.NAMESPACE
        }
        val settings = KotlinSettings(
            service = ShapeId.from("com.test#Test"),
            pkg = KotlinSettings.PackageSettings("name", "version"),
            sdkId = "testSdkId",
        )

        DefaultEndpointProviderGenerator(writer, rules, defaultSymbol, interfaceSymbol, paramsSymbol, settings).render()
        generatedClass = writer.toString()
    }

    private fun createTestRuleSet(json: String): EndpointRuleSet =
        EndpointRuleSet.fromNode(Node.parse(json))

    @Test
    fun testBalancedSyntax() {
        generatedClass.assertBalancedBracesAndParens()
    }

    @Test
    fun testBasicCondition() {
        val expected = """
            if (
                params.bazName != null &&
                params.bazName == "gov"
            ) {
                return Endpoint(
                    Url.parse("https://basic.condition", UrlDecoding.DecodeAll - UrlDecoding.DecodePath),
                )
            }
        """.formatForTest(indent = "        ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testAssignmentCondition() {
        val expected = """
            run {
                val resourceIdPrefix = substring(params.resourceId, 0, 4, false)
                if (
                    resourceIdPrefix != null &&
                    resourceIdPrefix == "gov.${'$'}{params.resourceId}"
                ) {
                    return Endpoint(
                        Url.parse("https://assignment.condition", UrlDecoding.DecodeAll - UrlDecoding.DecodePath),
                    )
                }
            }
        """.formatForTest(indent = "        ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testException() {
        val expected = """
            if (
                params.bazName != null &&
                params.bazName == "invalid"
            ) {
                throw EndpointProviderException("invalid BazName value")
            }
        """.formatForTest(indent = "        ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testEndpointFields() {
        val expected = """
            return Endpoint(
                Url.parse("https://global.api", UrlDecoding.DecodeAll - UrlDecoding.DecodePath),
                headers = Headers {
                    append("fooheader", "barheader")
                },
                attributes = attributesOf {
                    AttributeKey("foo") to "bar"
                    AttributeKey("fooInt") to 7
                    AttributeKey("fooBoolean") to true
                    AttributeKey("fooObject") to buildDocument {
                        "fooObjectFoo" to "bar"
                    }
                    AttributeKey("fooArray") to listOf(
                        "\"fooArrayBar\"",
                    )
                },
            )
        """.formatForTest(indent = "        ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }
}
