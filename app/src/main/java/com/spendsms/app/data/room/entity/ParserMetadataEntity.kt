package com.spendsms.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parser_metadata")
data class ParserMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "parser_version")
    val parserVersion: String,
    @ColumnInfo(name = "rules_version")
    val rulesVersion: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "checksum")
    val checksum: String,
    @ColumnInfo(name = "installed_at")
    val installedAt: Long,
    @ColumnInfo(name = "activated_at")
    val activatedAt: Long?,
    @ColumnInfo(name = "status")
    val status: String,
)
