package com.spendsms.app.domain.model

/**
 * Category assigned to an eligible transaction (Step-3 `categories`).
 */
data class Category(
    val id: CategoryId,
    val name: String,
    val isSystemCategory: Boolean,
    val sortOrder: Int,
    val createdAt: EpochMillis,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank" }
        require(sortOrder >= 0) { "Category sortOrder must be >= 0" }
    }
}

/**
 * Approved Phase-0 system category seed list from the PRD.
 *
 * Seeding into Room happens in a later persistence step; this object only defines
 * the domain identifiers and display names.
 */
object SystemCategories {
    val FOOD_AND_DINING: CategoryId = CategoryId.of("food_and_dining")
    val GROCERIES: CategoryId = CategoryId.of("groceries")
    val SHOPPING: CategoryId = CategoryId.of("shopping")
    val TRANSPORT: CategoryId = CategoryId.of("transport")
    val FUEL: CategoryId = CategoryId.of("fuel")
    val BILLS_AND_UTILITIES: CategoryId = CategoryId.of("bills_and_utilities")
    val ENTERTAINMENT: CategoryId = CategoryId.of("entertainment")
    val TRAVEL: CategoryId = CategoryId.of("travel")
    val HEALTH: CategoryId = CategoryId.of("health")
    val EDUCATION: CategoryId = CategoryId.of("education")
    val RENT_AND_HOUSING: CategoryId = CategoryId.of("rent_and_housing")
    val SUBSCRIPTIONS: CategoryId = CategoryId.of("subscriptions")
    val CASH_WITHDRAWAL: CategoryId = CategoryId.of("cash_withdrawal")
    val TRANSFERS: CategoryId = CategoryId.of("transfers")
    val INCOME_AND_REFUNDS: CategoryId = CategoryId.of("income_and_refunds")
    val OTHER: CategoryId = CategoryId.of("other")

    fun seed(createdAt: EpochMillis): List<Category> = listOf(
        Category(FOOD_AND_DINING, "Food and dining", true, 0, createdAt),
        Category(GROCERIES, "Groceries", true, 1, createdAt),
        Category(SHOPPING, "Shopping", true, 2, createdAt),
        Category(TRANSPORT, "Transport", true, 3, createdAt),
        Category(FUEL, "Fuel", true, 4, createdAt),
        Category(BILLS_AND_UTILITIES, "Bills and utilities", true, 5, createdAt),
        Category(ENTERTAINMENT, "Entertainment", true, 6, createdAt),
        Category(TRAVEL, "Travel", true, 7, createdAt),
        Category(HEALTH, "Health", true, 8, createdAt),
        Category(EDUCATION, "Education", true, 9, createdAt),
        Category(RENT_AND_HOUSING, "Rent and housing", true, 10, createdAt),
        Category(SUBSCRIPTIONS, "Subscriptions", true, 11, createdAt),
        Category(CASH_WITHDRAWAL, "Cash withdrawal", true, 12, createdAt),
        Category(TRANSFERS, "Transfers", true, 13, createdAt),
        Category(INCOME_AND_REFUNDS, "Income and refunds", true, 14, createdAt),
        Category(OTHER, "Other", true, 15, createdAt),
    )
}
