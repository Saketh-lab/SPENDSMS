package com.spendsms.app.domain.model

/**
 * Typed identifiers map cleanly to Step-3 TEXT primary keys without coupling to Room.
 */
@JvmInline
value class TransactionId private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "TransactionId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): TransactionId = TransactionId(value.trim())
    }
}

@JvmInline
value class CategoryId private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "CategoryId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): CategoryId = CategoryId(value.trim())
    }
}

@JvmInline
value class CorrectionId private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "CorrectionId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): CorrectionId = CorrectionId(value.trim())
    }
}

@JvmInline
value class SubscriptionId private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "SubscriptionId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): SubscriptionId = SubscriptionId(value.trim())
    }
}

@JvmInline
value class ScanId private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "ScanId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): ScanId = ScanId(value.trim())
    }
}

@JvmInline
value class MerchantKey private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "MerchantKey must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): MerchantKey = MerchantKey(value.trim())
    }
}

@JvmInline
value class TransactionFingerprint private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "TransactionFingerprint must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): TransactionFingerprint = TransactionFingerprint(value.trim())
    }
}

@JvmInline
value class ParserVersion private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "ParserVersion must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): ParserVersion = ParserVersion(value.trim())
    }
}

@JvmInline
value class RulesVersion private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "RulesVersion must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun of(value: String): RulesVersion = RulesVersion(value.trim())
    }
}
