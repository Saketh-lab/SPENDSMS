package com.spendsms.app.domain.categorisation

import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.SystemCategories

/**
 * Bundled exact-merchant and keyword category maps (data only).
 */
object BundledCategoryRules {

    val exactMerchant: Map<String, CategoryId> = mapOf(
        "swiggy" to SystemCategories.FOOD_AND_DINING,
        "zomato" to SystemCategories.FOOD_AND_DINING,
        "uber" to SystemCategories.TRANSPORT,
        "ola" to SystemCategories.TRANSPORT,
        "amazon" to SystemCategories.SHOPPING,
        "flipkart" to SystemCategories.SHOPPING,
        "netflix" to SystemCategories.SUBSCRIPTIONS,
        "spotify" to SystemCategories.SUBSCRIPTIONS,
        "bigbasket" to SystemCategories.GROCERIES,
        "blinkit" to SystemCategories.GROCERIES,
    )

    val keywords: List<Pair<Regex, CategoryId>> = listOf(
        keyword("""\b(swiggy|zomato|restaurant|cafe|dining|food)\b""", SystemCategories.FOOD_AND_DINING),
        keyword("""\b(grocery|groceries|supermarket|dmart|bigbasket|blinkit|instamart)\b""", SystemCategories.GROCERIES),
        keyword("""\b(amazon|flipkart|myntra|ajio|shopping)\b""", SystemCategories.SHOPPING),
        keyword("""\b(uber|ola|rapido|metro|cab|taxi)\b""", SystemCategories.TRANSPORT),
        keyword("""\b(petrol|diesel|fuel|iocl|hpcl|bpcl|shell)\b""", SystemCategories.FUEL),
        keyword("""\b(electricity|broadband|airtel|jio|bsnl|vodafone|bbps|utility)\b""", SystemCategories.BILLS_AND_UTILITIES),
        keyword("""\b(bookmyshow|pvr|hotstar|disney|entertainment)\b""", SystemCategories.ENTERTAINMENT),
        keyword("""\b(makemytrip|goibibo|indigo|airindia|hotel|flight)\b""", SystemCategories.TRAVEL),
        keyword("""\b(pharmacy|apollo|1mg|practo|hospital|clinic)\b""", SystemCategories.HEALTH),
        keyword("""\b(byju|unacademy|coursera|udemy|tuition)\b""", SystemCategories.EDUCATION),
        keyword("""\b(rent|housing|landlord)\b""", SystemCategories.RENT_AND_HOUSING),
        keyword("""\b(netflix|spotify|subscription|mandate)\b""", SystemCategories.SUBSCRIPTIONS),
    )

    private fun keyword(pattern: String, category: CategoryId): Pair<Regex, CategoryId> =
        Regex(pattern, RegexOption.IGNORE_CASE) to category
}
