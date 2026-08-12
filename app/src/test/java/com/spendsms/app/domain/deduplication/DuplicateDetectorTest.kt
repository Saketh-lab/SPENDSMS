package com.spendsms.app.domain.deduplication

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionCandidate
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.ClassifiedTransaction
import com.spendsms.app.domain.semantics.SpendingInclusion
import com.spendsms.app.domain.semantics.SpendingRole
import org.junit.Test

class TransactionFingerprintFactoryTest {

    private val policy = DuplicatePolicy.Phase0
    private val factory = TransactionFingerprintFactory(policy)

    @Test
    fun sameNormalizedInputs_produceStableFingerprint() {
        val first = factory.fingerprint(classified(timestamp = 1_700_000_000_000L))
        val second = factory.fingerprint(classified(timestamp = 1_700_000_000_000L))
        assertThat(first).isEqualTo(second)
        assertThat(first.value).hasLength(64)
    }

    @Test
    fun fingerprint_doesNotContainRawSmsOrMerchantDisplay() {
        val classified = classified(merchantKey = "swiggy", merchantDisplay = "Swiggy Secret")
        val fp = factory.fingerprint(classified)
        assertThat(fp.value).doesNotContain("Swiggy")
        assertThat(fp.value).doesNotContain("SECRET")
        assertThat(fp.value).doesNotContain("sms")
    }

    @Test
    fun amountOrDirectionChange_changesFingerprint() {
        val base = factory.fingerprint(classified())
        val otherAmount = factory.fingerprint(classified(amount = 200_00L))
        val otherDirection = factory.fingerprint(classified(direction = TransactionDirection.CREDIT))
        assertThat(otherAmount).isNotEqualTo(base)
        assertThat(otherDirection).isNotEqualTo(base)
    }

    @Test
    fun sameReference_collapsesAcrossInstitutionAndTime() {
        val bank = classified(
            institution = "Example Bank",
            timestamp = 1_700_000_000_000L,
            referenceHash = "ref-upi-1",
        )
        val upi = classified(
            institution = "Demo UPI Bank",
            timestamp = 1_700_000_000_000L + 20L * 60L * 1_000L,
            referenceHash = "ref-upi-1",
        )
        assertThat(factory.fingerprint(bank)).isEqualTo(factory.fingerprint(upi))
    }

    @Test
    fun withoutReference_sameFiveMinuteBucket_sameFingerprint() {
        val bucket = policy.fingerprintBucketMillis
        val a = classified(timestamp = bucket, referenceHash = null)
        val b = classified(timestamp = bucket + bucket - 1L, referenceHash = null)
        assertThat(factory.fingerprint(a)).isEqualTo(factory.fingerprint(b))
    }

    @Test
    fun withoutReference_adjacentBuckets_differ() {
        val bucket = policy.fingerprintBucketMillis
        val a = classified(timestamp = bucket - 1L, referenceHash = null)
        val b = classified(timestamp = bucket, referenceHash = null)
        assertThat(factory.fingerprint(a)).isNotEqualTo(factory.fingerprint(b))
    }

    @Test
    fun withoutReference_merchantChange_changesFingerprint() {
        val a = classified(merchantKey = "swiggy", referenceHash = null)
        val b = classified(merchantKey = "zomato", referenceHash = null)
        assertThat(factory.fingerprint(a)).isNotEqualTo(factory.fingerprint(b))
    }
}

class DuplicateDetectorTest {

    private val policy = DuplicatePolicy.Phase0
    private val factory = TransactionFingerprintFactory(policy)
    private val detector = DuplicateDetector(factory, policy)

    @Test
    fun sourceHashRescan_reusesExisting() {
        val classified = classified(sourceHash = "sms-1")
        val existing = stored(id = "tx-1", sourceHash = "sms-1", fingerprint = "other-fp")
        val result = detector.evaluate(
            classified = classified,
            existingBySourceHash = existing,
            existingByFingerprint = null,
            nearby = emptyList(),
        )
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.ReuseExisting(existing))
    }

    @Test
    fun exactFingerprint_reusesExisting() {
        val classified = classified()
        val fp = factory.fingerprint(classified)
        val existing = stored(id = "tx-1", fingerprint = fp.value)
        val result = detector.evaluate(
            classified = classified,
            existingBySourceHash = null,
            existingByFingerprint = existing,
            nearby = listOf(existing),
        )
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.ReuseExisting(existing))
        assertThat(result.fingerprint).isEqualTo(fp)
    }

    @Test
    fun nearDuplicate_sameMerchantAmountWithinWindow_isSuspected() {
        val firstTime = policy.fingerprintBucketMillis
        val secondTime = firstTime + policy.fingerprintBucketMillis
        require(secondTime <= firstTime + policy.nearWindowMillis)
        val incoming = classified(timestamp = secondTime, referenceHash = null)
        val existingFp = factory.fingerprint(classified(timestamp = firstTime, referenceHash = null))
        val existing = stored(
            id = "tx-1",
            fingerprint = existingFp.value,
            timestamp = firstTime,
        )
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
        )
        val outcome = result.outcome as DuplicateOutcome.InsertNearDuplicate
        assertThat(outcome.possibleDuplicateOf.value).isEqualTo("tx-1")
        assertThat(outcome.status).isEqualTo(DuplicateStatus.SUSPECTED)
        assertThat(result.fingerprint).isNotEqualTo(existing.fingerprint)
    }

    @Test
    fun legitimateRepeat_outsideNearWindow_isUnique() {
        val firstTime = 1_000L
        val secondTime = firstTime + policy.nearWindowMillis + 1L
        val incoming = classified(timestamp = secondTime, referenceHash = null)
        val existing = stored(
            id = "tx-1",
            fingerprint = "fp-old",
            timestamp = firstTime,
        )
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
        )
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.InsertUnique)
    }

    @Test
    fun differentReferenceHashes_areUniqueEvenIfSameAmountAndTime() {
        val incoming = classified(referenceHash = "ref-b")
        val existing = stored(id = "tx-1", fingerprint = "fp-a", referenceHash = "ref-a")
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
        )
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.InsertUnique)
    }

    @Test
    fun missingReference_canStillBeSuspectedNearDuplicate() {
        val firstTime = policy.fingerprintBucketMillis
        val secondTime = firstTime + policy.fingerprintBucketMillis
        val incoming = classified(timestamp = secondTime, referenceHash = null)
        val existing = stored(
            id = "tx-1",
            fingerprint = factory.fingerprint(classified(timestamp = firstTime, referenceHash = "ref-only-on-bank")).value,
            timestamp = firstTime,
            referenceHash = "ref-only-on-bank",
        )
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
        )
        assertThat(result.outcome).isInstanceOf(DuplicateOutcome.InsertNearDuplicate::class.java)
    }

    @Test
    fun differentMerchants_sameAmountAndTime_areUnique() {
        val incoming = classified(merchantKey = "swiggy")
        val existing = stored(
            id = "tx-1",
            fingerprint = "fp-z",
            merchantKey = "zomato",
        )
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
        )
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.InsertUnique)
    }

    @Test
    fun differentDirection_isUnique() {
        val incoming = classified(direction = TransactionDirection.DEBIT)
        val existing = stored(
            id = "tx-1",
            fingerprint = "fp-c",
            direction = TransactionDirection.CREDIT,
        )
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
        )
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.InsertUnique)
    }

    @Test
    fun differentMaskedAccounts_areUnique() {
        val incoming = classified(maskedAccount = "XX1111")
        val existing = stored(id = "tx-1", fingerprint = "fp-m", maskedAccount = "XX9999")
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
        )
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.InsertUnique)
    }

    @Test
    fun multipleNearMatches_marksSuspectedOfClosest_doesNotMerge() {
        val incomingTime = 10L * 60L * 1_000L
        val incoming = classified(timestamp = incomingTime, referenceHash = null)
        val closer = stored(id = "tx-close", fingerprint = "fp-1", timestamp = incomingTime - 1_000L)
        val farther = stored(id = "tx-far", fingerprint = "fp-2", timestamp = incomingTime - 5_000L)
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(farther, closer),
        )
        val outcome = result.outcome as DuplicateOutcome.InsertNearDuplicate
        assertThat(outcome.possibleDuplicateOf.value).isEqualTo("tx-close")
        assertThat(outcome.status).isEqualTo(DuplicateStatus.SUSPECTED)
    }

    @Test
    fun userOverrideNone_skipsNearDuplicateFlag() {
        val firstTime = policy.fingerprintBucketMillis
        val secondTime = firstTime + policy.fingerprintBucketMillis
        val incoming = classified(timestamp = secondTime, referenceHash = null)
        val existing = stored(
            id = "tx-1",
            fingerprint = factory.fingerprint(classified(timestamp = firstTime, referenceHash = null)).value,
            timestamp = firstTime,
        )
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
            userDuplicateOverride = DuplicateStatus.NONE,
        )
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.InsertUnique)
    }

    @Test
    fun timestampWindowEdge_inclusive() {
        val firstTime = 1_000L
        val edge = firstTime + policy.nearWindowMillis
        val incoming = classified(timestamp = edge, referenceHash = null)
        val existing = stored(id = "tx-1", fingerprint = "fp-edge", timestamp = firstTime)
        val result = detector.evaluate(
            classified = incoming,
            existingBySourceHash = null,
            existingByFingerprint = null,
            nearby = listOf(existing),
        )
        assertThat(result.outcome).isInstanceOf(DuplicateOutcome.InsertNearDuplicate::class.java)
    }
}

internal fun classified(
    sourceHash: String = "sms-hash-1",
    amount: Long = 100_00L,
    timestamp: Long = 1_700_000_000_000L,
    merchantKey: String = "swiggy",
    merchantDisplay: String = "Swiggy",
    institution: String? = "Example Bank",
    maskedAccount: String? = "XX1234",
    direction: TransactionDirection = TransactionDirection.DEBIT,
    referenceHash: String? = null,
): ClassifiedTransaction {
    val merchant = Merchant(
        key = MerchantKey.of(merchantKey),
        displayName = merchantDisplay,
        rawNormalized = merchantDisplay,
    )
    return ClassifiedTransaction(
        candidate = TransactionCandidate(
            sourceMessageHash = sourceHash,
            amount = Money.ofMinorUnits(amount, CurrencyCode.INR),
            transactionTimestamp = EpochMillis.of(timestamp),
            merchantRaw = merchantDisplay,
            institution = institution,
            maskedAccount = maskedAccount,
            direction = direction,
            paymentMethod = PaymentMethod.UPI,
            referenceHash = referenceHash,
            confidence = Confidence.of(0.9),
            parserVersion = ParserVersion.of("bundled-2026.08.12.0"),
        ),
        merchant = merchant,
        categoryId = SystemCategories.FOOD_AND_DINING,
        direction = direction,
        transferStatus = TransferStatus.NONE,
        spending = SpendingInclusion.of(SpendingRole.GROSS_EXPENSE),
    )
}

internal fun stored(
    id: String,
    fingerprint: String,
    sourceHash: String = "hash-$id",
    timestamp: Long = 1_700_000_000_000L,
    merchantKey: String = "swiggy",
    referenceHash: String? = null,
    maskedAccount: String? = "XX1234",
    direction: TransactionDirection = TransactionDirection.DEBIT,
    amount: Long = 100_00L,
): Transaction {
    val now = EpochMillis.of(timestamp)
    return Transaction(
        id = TransactionId.of(id),
        sourceMessageHash = sourceHash,
        fingerprint = TransactionFingerprint.of(fingerprint),
        timestamp = now,
        amount = Money.ofMinorUnits(amount, CurrencyCode.INR),
        merchant = Merchant(MerchantKey.of(merchantKey), merchantKey, merchantKey),
        institution = "Example Bank",
        maskedAccount = maskedAccount,
        referenceHash = referenceHash,
        direction = direction,
        paymentMethod = PaymentMethod.UPI,
        categoryId = CategoryId.of("food_and_dining"),
        confidence = Confidence.of(0.9),
        parserVersion = ParserVersion.of("bundled-2026.08.12.0"),
        duplicateStatus = DuplicateStatus.NONE,
        possibleDuplicateOf = null,
        transferStatus = TransferStatus.NONE,
        isUserConfirmed = false,
        createdAt = now,
        updatedAt = now,
    )
}
