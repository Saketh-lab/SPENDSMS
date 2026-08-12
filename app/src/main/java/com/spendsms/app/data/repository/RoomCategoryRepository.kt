package com.spendsms.app.data.repository

import com.spendsms.app.application.port.CategoryRepository
import com.spendsms.app.data.room.dao.CategoryDao
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.data.room.mapper.toEntity
import com.spendsms.app.domain.model.Category
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.SystemCategories
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override suspend fun getAll(): List<Category> =
        categoryDao.getAll().map { it.toDomain() }

    override suspend fun findById(id: CategoryId): Category? =
        categoryDao.findById(id.value)?.toDomain()

    override suspend fun ensureSystemCategoriesSeeded() {
        if (categoryDao.count() > 0) return
        val createdAt = EpochMillis.of(System.currentTimeMillis())
        categoryDao.insertAll(SystemCategories.seed(createdAt).map { it.toEntity() })
    }
}
