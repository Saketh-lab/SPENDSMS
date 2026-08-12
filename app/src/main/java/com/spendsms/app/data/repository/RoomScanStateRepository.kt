package com.spendsms.app.data.repository

import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.data.room.dao.ScanStateDao
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.data.room.mapper.toEntity
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomScanStateRepository @Inject constructor(
    private val scanStateDao: ScanStateDao,
) : ScanStateRepository {

    override suspend fun findById(id: ScanId): ScanState? =
        scanStateDao.findById(id.value)?.toDomain()

    override suspend fun findActive(): ScanState? =
        scanStateDao.findActive()?.toDomain()

    override suspend fun findResumable(): ScanState? =
        scanStateDao.findResumable()?.toDomain()

    override suspend fun findLatestCompleted(): ScanState? =
        scanStateDao.findLatestCompleted()?.toDomain()

    override suspend fun save(state: ScanState): ScanState {
        scanStateDao.upsert(state.toEntity())
        return state
    }

    override suspend fun deleteAll() {
        scanStateDao.deleteAll()
    }
}
