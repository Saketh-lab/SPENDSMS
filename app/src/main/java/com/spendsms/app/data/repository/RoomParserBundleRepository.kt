package com.spendsms.app.data.repository

import android.content.Context
import com.spendsms.app.application.port.ParserBundleRepository
import com.spendsms.app.application.port.ParserRulesDocument
import com.spendsms.app.data.room.dao.ParserMetadataDao
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.data.room.mapper.toEntity
import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser metadata in Room; declarative rule packages as files under no-backup storage.
 */
@Singleton
class RoomParserBundleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parserMetadataDao: ParserMetadataDao,
) : ParserBundleRepository {

    private val rulesDir: File
        get() = File(context.applicationContext.noBackupFilesDir, RULES_DIR).also { it.mkdirs() }

    override suspend fun findActiveMetadata(): ParserMetadata? =
        parserMetadataDao.findActive()?.toDomain()

    override suspend fun findMetadata(version: ParserVersion): ParserMetadata? =
        parserMetadataDao.findByVersion(version.value)?.toDomain()

    override suspend fun listInstalled(): List<ParserMetadata> =
        parserMetadataDao.listInstalled().map { it.toDomain() }

    override suspend fun install(
        metadata: ParserMetadata,
        rulesDocument: ParserRulesDocument,
    ) {
        val target = rulesFile(metadata.parserVersion)
        target.writeText(rulesDocument.utf8Json)
        parserMetadataDao.upsert(metadata.toEntity())
    }

    override suspend fun activate(version: ParserVersion) {
        require(rulesFile(version).exists()) {
            "Cannot activate parser ${version.value}: rules document missing"
        }
        parserMetadataDao.activate(
            parserVersion = version.value,
            activatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun markRollback(version: ParserVersion) {
        parserMetadataDao.markRollback(version.value)
    }

    override suspend fun markInvalid(version: ParserVersion) {
        parserMetadataDao.markInvalid(version.value)
    }

    override suspend fun loadActiveRulesDocument(): ParserRulesDocument? {
        val active = findActiveMetadata() ?: return null
        return loadRulesDocument(active.parserVersion)
    }

    override suspend fun loadRulesDocument(version: ParserVersion): ParserRulesDocument? {
        val file = rulesFile(version)
        if (!file.exists()) return null
        return ParserRulesDocument.of(file.readText())
    }

    private fun rulesFile(version: ParserVersion): File =
        File(rulesDir, "${version.value}.json")

    companion object {
        private const val RULES_DIR = "parser_rules"
    }
}
