package com.spendsms.app.application.analysis

import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ScanId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun interface ScanClock {
    fun nowMillis(): Long

    fun now(): EpochMillis = EpochMillis.of(nowMillis())
}

@Singleton
class SystemScanClock @Inject constructor() : ScanClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

fun interface ScanIdFactory {
    fun next(): ScanId
}

@Singleton
class UuidScanIdFactory @Inject constructor() : ScanIdFactory {
    override fun next(): ScanId = ScanId.of(UUID.randomUUID().toString())
}
