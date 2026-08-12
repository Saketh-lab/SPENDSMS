package com.spendsms.app.domain.merchant

import com.spendsms.app.domain.model.MerchantKey

/**
 * Phase-0 bundled merchant aliases (data only). Remote alias packs are out of scope.
 */
object BundledMerchantAliases {

    val ALL: List<MerchantAlias> = listOf(
        alias("swiggy", "Swiggy", "swiggy", "swiggy.com"),
        alias("zomato", "Zomato", "zomato", "zomato.com"),
        alias("amazon", "Amazon", "amazon", "amazon.in", "amzn"),
        alias("flipkart", "Flipkart", "flipkart", "fkrt"),
        alias("uber", "Uber", "uber", "uber.com"),
        alias("ola", "Ola", "ola", "olacabs"),
        alias("netflix", "Netflix", "netflix"),
        alias("spotify", "Spotify", "spotify"),
        alias("bigbasket", "BigBasket", "bigbasket", "bbnow"),
        alias("blinkit", "Blinkit", "blinkit", "grofers"),
    )

    val byToken: Map<String, MerchantAlias> =
        ALL.flatMap { alias -> alias.tokens.map { token -> token to alias } }.toMap()

    private fun alias(key: String, display: String, vararg tokens: String): MerchantAlias =
        MerchantAlias(
            key = MerchantKey.of(key),
            displayName = display,
            tokens = tokens.map { it.lowercase() }.toSet() + key,
        )
}
