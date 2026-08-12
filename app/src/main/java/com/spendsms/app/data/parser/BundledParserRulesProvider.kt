package com.spendsms.app.data.parser

import com.spendsms.app.domain.parsing.SignedParserBundle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the APK-bundled signed default parser package (offline fallback).
 */
@Singleton
class BundledParserRulesProvider @Inject constructor(
    private val assetLoader: ParserAssetLoader,
    private val codec: ParserRulesCodec,
) {
    fun loadSignedBundleJson(): String = assetLoader.readText(BUNDLED_BUNDLE_ASSET)

    fun loadSignedBundle(): SignedParserBundle =
        codec.decodeSignedBundle(loadSignedBundleJson())

    fun loadRulesUtf8(): String = assetLoader.readText(BUNDLED_RULES_ASSET)

    companion object {
        const val BUNDLED_RULES_ASSET = "parser/default_rules.json"
        const val BUNDLED_BUNDLE_ASSET = "parser/default_bundle.json"
    }
}
