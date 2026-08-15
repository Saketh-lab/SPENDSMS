package com.spendsms.app.data.parser.update

import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.parsing.ParserManifest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ParserManifestDto(
    val schemaVersion: Int,
    val latestParserVersion: String,
    val latestRulesVersion: String,
    val minimumAppVersion: String,
    val packageUrl: String,
    val packageSha256: String,
    val publishedAtEpochMillis: Long? = null,
)

@Singleton
class ParserManifestCodec @Inject constructor(
    private val json: Json,
) {
    fun decode(utf8: String): ParserManifest {
        val dto = try {
            json.decodeFromString(ParserManifestDto.serializer(), utf8)
        } catch (e: Exception) {
            throw ParserManifestCodecException("Malformed parser manifest JSON", e)
        }
        return try {
            ParserManifest(
                schemaVersion = dto.schemaVersion,
                latestParserVersion = ParserVersion.of(dto.latestParserVersion),
                latestRulesVersion = RulesVersion.of(dto.latestRulesVersion),
                minimumAppVersion = dto.minimumAppVersion.trim(),
                packageUrl = dto.packageUrl.trim(),
                packageSha256 = dto.packageSha256.trim().lowercase(),
                publishedAtEpochMillis = dto.publishedAtEpochMillis,
            )
        } catch (e: IllegalArgumentException) {
            throw ParserManifestCodecException(e.message ?: "Invalid parser manifest", e)
        }
    }

    fun encode(manifest: ParserManifest): String {
        val dto = ParserManifestDto(
            schemaVersion = manifest.schemaVersion,
            latestParserVersion = manifest.latestParserVersion.value,
            latestRulesVersion = manifest.latestRulesVersion.value,
            minimumAppVersion = manifest.minimumAppVersion,
            packageUrl = manifest.packageUrl,
            packageSha256 = manifest.packageSha256,
            publishedAtEpochMillis = manifest.publishedAtEpochMillis,
        )
        return json.encodeToString(ParserManifestDto.serializer(), dto)
    }
}

class ParserManifestCodecException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
