package com.spendsms.app.domain.semantics

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransferStatus
import org.junit.Test

class TransactionSemanticsTest {

    private val semantics = TransactionSemantics()

    @Test
    fun debitIsGrossExpense() {
        val decision = semantics.resolve(
            parsedDirection = TransactionDirection.DEBIT,
            merchant = merchant("swiggy", "Swiggy"),
        )
        assertThat(decision.direction).isEqualTo(TransactionDirection.DEBIT)
        assertThat(decision.transferStatus).isEqualTo(TransferStatus.NONE)
        assertThat(decision.spending.role).isEqualTo(SpendingRole.GROSS_EXPENSE)
        assertThat(decision.spending.includeInGrossSpending).isTrue()
        assertThat(decision.spending.includeInNetSpending).isTrue()
        assertThat(decision.spending.excludedFromSpendingTotals).isFalse()
    }

    @Test
    fun creditIsNotSpending() {
        val decision = semantics.resolve(
            parsedDirection = TransactionDirection.CREDIT,
            merchant = null,
        )
        assertThat(decision.spending.role).isEqualTo(SpendingRole.CREDIT)
        assertThat(decision.spending.includeInCredits).isTrue()
        assertThat(decision.spending.includeInGrossSpending).isFalse()
        assertThat(decision.spending.includeInNetSpending).isFalse()
    }

    @Test
    fun refundAndReversalReduceNetViaRefundsBucket() {
        for (direction in listOf(TransactionDirection.REFUND, TransactionDirection.REVERSAL)) {
            val decision = semantics.resolve(parsedDirection = direction, merchant = merchant("amazon", "Amazon"))
            assertThat(decision.spending.role).isEqualTo(SpendingRole.REFUND)
            assertThat(decision.spending.includeInRefunds).isTrue()
            assertThat(decision.spending.includeInGrossSpending).isFalse()
            assertThat(decision.spending.includeInNetSpending).isFalse()
        }
    }

    @Test
    fun parserTransferIsSuspectedNotConfirmed() {
        val decision = semantics.resolve(
            parsedDirection = TransactionDirection.TRANSFER,
            merchant = merchant("own ac xx99", "OWN AC XX99"),
        )
        assertThat(decision.transferStatus).isEqualTo(TransferStatus.SUSPECTED)
        assertThat(decision.spending.role).isEqualTo(SpendingRole.TRANSFER)
        assertThat(decision.spending.excludedFromSpendingTotals).isTrue()
        assertThat(decision.spending.includeInGrossSpending).isFalse()
    }

    @Test
    fun ownAccountDebitIsSuspectedTransfer() {
        val decision = semantics.resolve(
            parsedDirection = TransactionDirection.DEBIT,
            merchant = merchant("own ac xx7788", "OWN AC XX7788"),
        )
        assertThat(decision.direction).isEqualTo(TransactionDirection.DEBIT)
        assertThat(decision.transferStatus).isEqualTo(TransferStatus.SUSPECTED)
        assertThat(decision.spending.excludedFromSpendingTotals).isTrue()
    }

    @Test
    fun userConfirmedTransferOverrides() {
        val decision = semantics.resolve(
            parsedDirection = TransactionDirection.DEBIT,
            merchant = merchant("swiggy", "Swiggy"),
            userTransferStatus = TransferStatus.CONFIRMED,
        )
        assertThat(decision.transferStatus).isEqualTo(TransferStatus.CONFIRMED)
        assertThat(decision.spending.role).isEqualTo(SpendingRole.TRANSFER)
    }

    @Test
    fun userCanForceDebitToCountAsSpending() {
        val decision = semantics.resolve(
            parsedDirection = TransactionDirection.TRANSFER,
            merchant = merchant("own ac", "OWN AC"),
            userDirection = TransactionDirection.DEBIT,
            userTransferStatus = TransferStatus.NONE,
        )
        assertThat(decision.direction).isEqualTo(TransactionDirection.DEBIT)
        assertThat(decision.transferStatus).isEqualTo(TransferStatus.NONE)
        assertThat(decision.spending.role).isEqualTo(SpendingRole.GROSS_EXPENSE)
    }

    @Test
    fun notATransactionExcludesAllTotals() {
        val decision = semantics.resolve(
            parsedDirection = TransactionDirection.DEBIT,
            merchant = merchant("swiggy", "Swiggy"),
            excludedByUser = true,
        )
        assertThat(decision.spending.role).isEqualTo(SpendingRole.EXCLUDED)
        assertThat(decision.spending.includeInGrossSpending).isFalse()
        assertThat(decision.spending.includeInCredits).isFalse()
        assertThat(decision.spending.includeInRefunds).isFalse()
        assertThat(decision.spending.excludedFromSpendingTotals).isTrue()
    }

    private fun merchant(key: String, display: String): Merchant =
        Merchant(MerchantKey.of(key), display, display)
}
