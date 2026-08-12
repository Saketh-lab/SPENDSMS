package com.spendsms.app.data.room

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.data.room.entity.ScanStateEntity
import com.spendsms.app.data.room.entity.SubscriptionEntity
import com.spendsms.app.data.room.entity.TransactionEntity
import com.spendsms.app.data.room.entity.UserCorrectionEntity
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.data.room.mapper.toEntity
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SpendSmsDatabaseTest {

    private lateinit var db: SpendSmsDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = SpendSmsDatabase.buildInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun onCreate_seedsSystemCategories() {
        runBlocking {
            val categories = db.categoryDao().getAll()
            assertThat(categories).hasSize(16)
            assertThat(categories.map { it.categoryId }).containsAtLeast(
                SystemCategories.GROCERIES.value,
                SystemCategories.OTHER.value,
            )
            assertThat(categories.all { it.isSystemCategory }).isTrue()
        }
    }

    @Test
    fun transaction_uniqueFingerprint_isEnforced() {
        runBlocking {
            val categoryId = SystemCategories.OTHER.value
            val first = sampleTransaction(id = "tx-1", fingerprint = "fp-same", categoryId = categoryId)
            val duplicate = sampleTransaction(id = "tx-2", fingerprint = "fp-same", categoryId = categoryId)

            db.transactionDao().insert(first)

            var failed = false
            try {
                db.transactionDao().insert(duplicate)
            } catch (_: SQLiteConstraintException) {
                failed = true
            } catch (_: Exception) {
                failed = true
            }
            assertThat(failed).isTrue()
        }
    }

    @Test
    fun correction_cascadesWhenTransactionDeleted() {
        runBlocking {
            val categoryId = SystemCategories.OTHER.value
            val tx = sampleTransaction(id = "tx-10", fingerprint = "fp-10", categoryId = categoryId)
            db.transactionDao().insert(tx)

            db.userCorrectionDao().upsert(
                UserCorrectionEntity(
                    correctionId = "c-1",
                    transactionId = "tx-10",
                    fieldName = "CATEGORY",
                    oldValue = SystemCategories.OTHER.value,
                    newValue = SystemCategories.GROCERIES.value,
                    applyToFuture = false,
                    merchantMatchKey = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            assertThat(db.userCorrectionDao().findByTransactionId("tx-10")).hasSize(1)

            db.transactionDao().deleteAll()
            assertThat(db.userCorrectionDao().findByTransactionId("tx-10")).isEmpty()
        }
    }

    @Test
    fun findInPeriod_andNear_queryBehave() {
        runBlocking {
            val categoryId = SystemCategories.OTHER.value
            db.transactionDao().insert(
                sampleTransaction(
                    id = "tx-a",
                    fingerprint = "fp-a",
                    categoryId = categoryId,
                    timestamp = 1_000L,
                    amount = 100L,
                ),
            )
            db.transactionDao().insert(
                sampleTransaction(
                    id = "tx-b",
                    fingerprint = "fp-b",
                    categoryId = categoryId,
                    timestamp = 2_000L,
                    amount = 100L,
                ),
            )
            db.transactionDao().insert(
                sampleTransaction(
                    id = "tx-c",
                    fingerprint = "fp-c",
                    categoryId = categoryId,
                    timestamp = 10_000L,
                    amount = 500L,
                ),
            )

            val inPeriod = db.transactionDao().findInPeriod(1_000L, 2_000L)
            assertThat(inPeriod.map { it.transactionId }).containsExactly("tx-b", "tx-a").inOrder()

            val near = db.transactionDao().findNear(
                amountMinorUnits = 100L,
                currency = "INR",
                windowStart = 500L,
                windowEnd = 2_500L,
            )
            assertThat(near.map { it.transactionId }).containsExactly("tx-b", "tx-a")
        }
    }

    @Test
    fun subscriptionEvidence_roundTrips() {
        runBlocking {
            val categoryId = SystemCategories.SUBSCRIPTIONS.value
            db.transactionDao().insert(
                sampleTransaction(id = "tx-s1", fingerprint = "fp-s1", categoryId = categoryId),
            )
            db.transactionDao().insert(
                sampleTransaction(id = "tx-s2", fingerprint = "fp-s2", categoryId = categoryId),
            )

            db.subscriptionDao().upsertWithEvidence(
                entity = SubscriptionEntity(
                    subscriptionId = "sub-1",
                    merchantKey = "music",
                    merchantDisplayName = "Music",
                    frequency = "MONTHLY",
                    estimatedAmountMinor = 499_00L,
                    currency = "INR",
                    lastPaymentDate = 1L,
                    estimatedNextDate = 2L,
                    confidence = 0.8,
                    status = "SUSPECTED",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
                evidenceTransactionIds = listOf("tx-s1", "tx-s2"),
            )

            assertThat(db.subscriptionDao().getEvidenceTransactionIds("sub-1"))
                .containsExactly("tx-s1", "tx-s2")
        }
    }

    @Test
    fun scanState_findActiveAndCompleted() {
        runBlocking {
            db.scanStateDao().upsert(
                ScanStateEntity(
                    scanId = "scan-1",
                    startDate = 1L,
                    endDate = 2L,
                    lastProcessedMessageId = "m1",
                    parserVersion = "2026.08.10.1",
                    status = "RUNNING",
                    processedCount = 3,
                    acceptedCount = 1,
                    startedAt = 10L,
                    completedAt = null,
                    updatedAt = 11L,
                ),
            )
            assertThat(db.scanStateDao().findActive()?.scanId).isEqualTo("scan-1")

            db.scanStateDao().upsert(
                ScanStateEntity(
                    scanId = "scan-1",
                    startDate = 1L,
                    endDate = 2L,
                    lastProcessedMessageId = "m9",
                    parserVersion = "2026.08.10.1",
                    status = "COMPLETED",
                    processedCount = 9,
                    acceptedCount = 4,
                    startedAt = 10L,
                    completedAt = 20L,
                    updatedAt = 20L,
                ),
            )
            assertThat(db.scanStateDao().findActive()).isNull()
            assertThat(db.scanStateDao().findLatestCompleted()?.scanId).isEqualTo("scan-1")
        }
    }

    @Test
    fun mapper_domainRoundTrip_throughDao() {
        runBlocking {
            val now = EpochMillis.of(50L)
            val domain = Transaction(
                id = TransactionId.of("tx-map"),
                sourceMessageHash = "hash-map",
                fingerprint = TransactionFingerprint.of("fp-map"),
                timestamp = now,
                amount = Money.ofMinorUnits(10L, CurrencyCode.INR),
                merchant = null,
                institution = null,
                maskedAccount = null,
                referenceHash = null,
                direction = TransactionDirection.CREDIT,
                paymentMethod = PaymentMethod.UNKNOWN,
                categoryId = SystemCategories.OTHER,
                confidence = Confidence.of(1.0),
                parserVersion = ParserVersion.of("v1"),
                duplicateStatus = DuplicateStatus.NONE,
                possibleDuplicateOf = null,
                transferStatus = TransferStatus.NONE,
                isUserConfirmed = true,
                createdAt = now,
                updatedAt = now,
            )
            db.transactionDao().insert(domain.toEntity())
            val restored = db.transactionDao().findById("tx-map")!!.toDomain()
            assertThat(restored).isEqualTo(domain)
        }
    }

    private fun sampleTransaction(
        id: String,
        fingerprint: String,
        categoryId: String,
        timestamp: Long = 1_000L,
        amount: Long = 100L,
    ): TransactionEntity = TransactionEntity(
        transactionId = id,
        sourceMessageHash = "hash-$id",
        transactionFingerprint = fingerprint,
        transactionTimestamp = timestamp,
        amountMinorUnits = amount,
        currency = "INR",
        merchantRawNormalized = null,
        merchantDisplayName = null,
        merchantKey = null,
        institution = null,
        maskedAccount = null,
        referenceHash = null,
        direction = "DEBIT",
        paymentMethod = "UPI",
        categoryId = categoryId,
        confidence = 0.9,
        parserVersion = "2026.08.10.1",
        duplicateStatus = "NONE",
        possibleDuplicateOf = null,
        transferStatus = "NONE",
        isUserConfirmed = false,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}
