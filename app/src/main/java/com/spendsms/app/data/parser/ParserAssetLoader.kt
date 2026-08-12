package com.spendsms.app.data.parser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParserAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun readText(assetPath: String): String {
        try {
            return context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw IllegalStateException("Missing parser asset: $assetPath", e)
        }
    }
}
