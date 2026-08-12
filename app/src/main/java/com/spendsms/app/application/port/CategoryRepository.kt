package com.spendsms.app.application.port

import com.spendsms.app.domain.model.Category
import com.spendsms.app.domain.model.CategoryId

/**
 * Category catalogue used by categorisation and UI (Step-3 `categories`).
 */
interface CategoryRepository {

    suspend fun getAll(): List<Category>

    suspend fun findById(id: CategoryId): Category?

    /** Idempotent seed of PRD system categories on first database creation. */
    suspend fun ensureSystemCategoriesSeeded()
}
