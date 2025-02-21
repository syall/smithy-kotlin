/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util.text

import io.kotest.matchers.maps.shouldContain
import kotlin.test.*

data class EscapeTest(val input: String, val expected: String, val formUrlEncode: Boolean = false)

class TextTest {
    @Test
    fun urlValuesEncodeCorrectly() {
        val nonEncodedCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
        val encodedCharactersInput = "\t\n\r !\"#$%&'()*+,/:;<=>?@[\\]^`{|}"
        val encodedCharactersOutput =
            "%09%0A%0D%20%21%22%23%24%25%26%27%28%29%2A%2B%2C%2F%3A%3B%3C%3D%3E%3F%40%5B%5C%5D%5E%60%7B%7C%7D"

        val tests: List<EscapeTest> = listOf(
            EscapeTest("", ""),
            EscapeTest("abc", "abc"),
            EscapeTest("a b", "a%20b"),
            EscapeTest("a b", "a+b", formUrlEncode = true),
            EscapeTest("10%", "10%25"),
            EscapeTest(nonEncodedCharacters, nonEncodedCharacters),
            EscapeTest(encodedCharactersInput, encodedCharactersOutput),
        )

        for (test in tests) {
            val actual = test.input.urlEncodeComponent(test.formUrlEncode)
            assertEquals(test.expected, actual, "expected ${test.expected}; got: $actual")
        }
    }

    @Test
    fun formDataValuesEncodeCorrectly() {
        val nonEncodedCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_."
        val encodedCharactersInput = "\t\n\r !\"#$%&'()+,/:;<=>?@[\\]^`{|}"
        val encodedCharactersOutput =
            "%09%0A%0D+%21%22%23%24%25%26%27%28%29%2B%2C%2F%3A%3B%3C%3D%3E%3F%40%5B%5C%5D%5E%60%7B%7C%7D"

        val tests: List<EscapeTest> = listOf(
            EscapeTest("", ""),
            EscapeTest("a b", "a+b"),
            EscapeTest(nonEncodedCharacters, nonEncodedCharacters),
            EscapeTest(encodedCharactersInput, encodedCharactersOutput),
        )

        for (test in tests) {
            val actual = test.input.urlEncodeComponent(true)
            assertEquals(test.expected, actual, "expected ${test.expected}; got: $actual")
        }
    }

    @Test
    fun urlPathValuesEncodeCorrectly() {
        val urlPath = "/wikipedia/en/6/61/Purdue_University_\u2013seal.svg"
        assertEquals("/wikipedia/en/6/61/Purdue_University_%E2%80%93seal.svg", urlPath.encodeUrlPath())
        assertEquals("/kotlin/Tue,%2029%20Apr%202014%2018:30:38%20GMT", "/kotlin/Tue, 29 Apr 2014 18:30:38 GMT".encodeUrlPath())
    }

    @Test
    fun respectsAlreadyEncodedUrls() {
        val urlPath = "/wikipedia/en/6/61/Purdue_University_%E2%80%93seal.svg"
        assertEquals("/wikipedia/en/6/61/Purdue_University_%E2%80%93seal.svg", urlPath.encodeUrlPath())
    }

    @Test
    fun testNormalizePathSegments() {
        fun assertNormalize(unnormalized: String, expected: String) {
            val actual = unnormalized.normalizePathSegments(String::uppercase)
            assertEquals(expected, actual, "Unexpected normalization for `$unnormalized`")
        }

        val tests = mapOf(
            "" to "/",
            "/" to "/",
            "foo" to "/FOO",
            "/foo" to "/FOO",
            "foo/" to "/FOO/",
            "/foo/" to "/FOO/",
            "/a/b/c" to "/A/B/C",
            "/a/b/../c" to "/A/C",
            "/a/./c" to "/A/C",
            "/./" to "/",
            "/a/b/./../c" to "/A/C",
            "/a/b/c/d/../e/../../f/../../../g" to "/G",
            "//a//b//c//" to "/A/B/C/",
        )
        tests.forEach { (unnormalized, expected) -> assertNormalize(unnormalized, expected) }
    }

    @Test
    fun testNormalizePathSegmentsError() {
        assertFailsWith(IllegalArgumentException::class) {
            "/a/b/../../..".normalizePathSegments { it }
        }
    }

    @Test
    fun utf8UrlPathValuesEncodeCorrectly() {
        val swissAndGerman = "\u0047\u0072\u00fc\u0065\u007a\u0069\u005f\u007a\u00e4\u006d\u00e4"
        val russian = "\u0412\u0441\u0435\u043c\u005f\u043f\u0440\u0438\u0432\u0435\u0442"
        val japanese = "\u3053\u3093\u306b\u3061\u306f"
        assertEquals("%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", russian.encodeUrlPath())
        assertEquals("Gr%C3%BCezi_z%C3%A4m%C3%A4", swissAndGerman.encodeUrlPath())
        assertEquals("%E3%81%93%E3%82%93%E3%81%AB%E3%81%A1%E3%81%AF", japanese.encodeUrlPath())
    }

    @Test
    fun splitQueryStringIntoParts() {
        val query = "foo=baz&bar=quux&foo=qux&a="
        val actual = query.splitAsQueryString()
        val expected = mapOf(
            "foo" to listOf("baz", "qux"),
            "bar" to listOf("quux"),
            "a" to listOf(""),
        )

        expected.entries.forEach { entry ->
            actual.shouldContain(entry.key, entry.value)
        }

        val queryNoEquals = "abc=cde&noequalssign"
        val actualNoEquals = queryNoEquals.splitAsQueryString()
        val expectedNoEquals = mapOf(
            "abc" to listOf("cde"),
            "noequalssign" to listOf(""),
        )
        expectedNoEquals.entries.forEach { entry ->
            actualNoEquals.shouldContain(entry.key, entry.value)
        }

        val queryValueWithEquals = "foo=bar&baz=quux=="
        val actualValueWithEquals = queryValueWithEquals.splitAsQueryString()
        val expectedValueWithEquals = mapOf(
            "foo" to listOf("bar"),
            "baz" to listOf("quux=="),
        )
        expectedValueWithEquals.entries.forEach { entry ->
            actualValueWithEquals.shouldContain(entry.key, entry.value)
        }
    }

    @Test
    fun decodeUrlComponent() {
        val component = "a%3Bb+c%7Ed%20e%2Bf+g%3D%E1%88%B4"
        val expected = "a;b+c~d e+f+g=ሴ"
        assertEquals(expected, component.urlDecodeComponent())
    }

    @Test
    fun decodeUrlComponentWithFormUrl() {
        val component = "a%3Bb+c%7Ed%20e%2Bf+g%3D%E1%88%B4"
        val expected = "a;b c~d e+f g=ሴ"
        assertEquals(expected, component.urlDecodeComponent(true))
    }

    @Test
    fun decodeUrlComponentInvalidSequence() {
        val component = "%20%&'%%%f"
        val expected = " %&'%%%f" // Only the %20 was valid
        assertEquals(expected, component.urlDecodeComponent())
    }

    @Test
    fun reencodeUrlComponent() {
        val component = "ሴ%E1%88%B4"
        val expected = "%E1%88%B4%E1%88%B4"
        assertEquals(expected, component.urlReencodeComponent())
    }
}
