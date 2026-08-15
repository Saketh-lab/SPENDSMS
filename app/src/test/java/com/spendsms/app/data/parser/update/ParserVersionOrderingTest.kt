package com.spendsms.app.data.parser.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParserVersionOrderingTest {

    @Test
    fun comparesNumericSuffixAcrossPrefixes() {
        assertThat(ParserVersionOrdering.isNewer("2026.08.13.1", "bundled-2026.08.12.0")).isTrue()
        assertThat(ParserVersionOrdering.isNewer("bundled-2026.08.12.0", "2026.08.13.1")).isFalse()
        assertThat(ParserVersionOrdering.compare("2026.08.12.0", "bundled-2026.08.12.0")).isEqualTo(0)
    }

    @Test
    fun rejectsEqualAsNotNewer() {
        assertThat(ParserVersionOrdering.isNewer("2026.08.13.1", "2026.08.13.1")).isFalse()
    }
}

class ParserManifestCodecTest {
    private val codec = ParserManifestCodec(
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = false
        },
    )

    @Test
    fun roundTripsManifest() {
        val json = """
            {
              "schemaVersion": 1,
              "latestParserVersion": "2026.08.13.1",
              "latestRulesVersion": "rules-2",
              "minimumAppVersion": "0.1.0",
              "packageUrl": "https://cdn.example.invalid/parser/2026.08.13.1/bundle.json",
              "packageSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
        """.trimIndent()
        val decoded = codec.decode(json)
        assertThat(decoded.latestParserVersion.value).isEqualTo("2026.08.13.1")
        val encoded = codec.encode(decoded)
        assertThat(codec.decode(encoded)).isEqualTo(decoded)
    }

    @Test
    fun rejectsHttpPackageUrl() {
        val json = """
            {
              "schemaVersion": 1,
              "latestParserVersion": "2026.08.13.1",
              "latestRulesVersion": "rules-2",
              "minimumAppVersion": "0.1.0",
              "packageUrl": "http://cdn.example.invalid/parser/bundle.json",
              "packageSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
        """.trimIndent()
        try {
            codec.decode(json)
            throw AssertionError("expected failure")
        } catch (_: ParserManifestCodecException) {
            // expected
        }
    }
}

class ParserCdnAllowListTest {
    @Test
    fun allowsHttpsOnListedHostOnly() {
        val allow = ParserCdnAllowList(setOf("cdn.example.invalid"))
        assertThat(allow.isAllowed("https://cdn.example.invalid/parser/manifest.json")).isTrue()
        assertThat(allow.isAllowed("https://evil.example/parser/manifest.json")).isFalse()
        assertThat(allow.isAllowed("http://cdn.example.invalid/parser/manifest.json")).isFalse()
    }
}
