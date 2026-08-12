package com.spendsms.app.domain.categorisation

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransferStatus
import org.junit.Test

class CategorisationEngineTest {

    private val engine = CategorisationEngine()

    @Test
    fun userCorrectionBeatsMerchantRule() {
        val id = engine.categorise(
            merchant = merchant("swiggy", "Swiggy"),
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.UPI,
            transferStatus = TransferStatus.NONE,
            userCategoryId = SystemCategories.ENTERTAINMENT,
        )
        assertThat(id).isEqualTo(SystemCategories.ENTERTAINMENT)
    }

    @Test
    fun exactMerchantRule_swiggyIsFood() {
        val id = engine.categorise(
            merchant = merchant("swiggy", "Swiggy"),
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.UPI,
            transferStatus = TransferStatus.NONE,
        )
        assertThat(id).isEqualTo(SystemCategories.FOOD_AND_DINING)
    }

    @Test
    fun keywordRule_unknownRestaurant() {
        val id = engine.categorise(
            merchant = merchant("cafe mocha", "Cafe Mocha"),
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.CARD,
            transferStatus = TransferStatus.NONE,
        )
        assertThat(id).isEqualTo(SystemCategories.FOOD_AND_DINING)
    }

    @Test
    fun unknownMerchantFallsBackToOther() {
        val id = engine.categorise(
            merchant = merchant("local kirana 12", "LOCAL KIRANA-12"),
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.UPI,
            transferStatus = TransferStatus.NONE,
        )
        assertThat(id).isEqualTo(SystemCategories.OTHER)
    }

    @Test
    fun nullMerchantDebitFallsBackToOther() {
        val id = engine.categorise(
            merchant = null,
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.UNKNOWN,
            transferStatus = TransferStatus.NONE,
        )
        assertThat(id).isEqualTo(SystemCategories.OTHER)
    }

    @Test
    fun creditAndRefundMapToIncomeAndRefunds() {
        assertThat(
            engine.categorise(
                merchant = merchant("swiggy", "Swiggy"),
                direction = TransactionDirection.CREDIT,
                paymentMethod = PaymentMethod.UPI,
                transferStatus = TransferStatus.NONE,
            ),
        ).isEqualTo(SystemCategories.INCOME_AND_REFUNDS)
        assertThat(
            engine.categorise(
                merchant = merchant("amazon", "Amazon"),
                direction = TransactionDirection.REFUND,
                paymentMethod = PaymentMethod.CARD,
                transferStatus = TransferStatus.NONE,
            ),
        ).isEqualTo(SystemCategories.INCOME_AND_REFUNDS)
        assertThat(
            engine.categorise(
                merchant = merchant("amazon", "Amazon"),
                direction = TransactionDirection.REVERSAL,
                paymentMethod = PaymentMethod.CARD,
                transferStatus = TransferStatus.NONE,
            ),
        ).isEqualTo(SystemCategories.INCOME_AND_REFUNDS)
    }

    @Test
    fun transferDirectionAndStatusMapToTransfers() {
        assertThat(
            engine.categorise(
                merchant = merchant("own ac", "OWN AC"),
                direction = TransactionDirection.TRANSFER,
                paymentMethod = PaymentMethod.NET_BANKING,
                transferStatus = TransferStatus.SUSPECTED,
            ),
        ).isEqualTo(SystemCategories.TRANSFERS)
        assertThat(
            engine.categorise(
                merchant = merchant("own ac", "OWN AC"),
                direction = TransactionDirection.DEBIT,
                paymentMethod = PaymentMethod.UPI,
                transferStatus = TransferStatus.SUSPECTED,
            ),
        ).isEqualTo(SystemCategories.TRANSFERS)
    }

    @Test
    fun atmDebitMapsToCashWithdrawal() {
        val id = engine.categorise(
            merchant = merchant("atm", "ATM"),
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.ATM,
            transferStatus = TransferStatus.NONE,
        )
        assertThat(id).isEqualTo(SystemCategories.CASH_WITHDRAWAL)
    }

    @Test
    fun invalidUserCategoryIsIgnored() {
        val id = engine.categorise(
            merchant = merchant("swiggy", "Swiggy"),
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.UPI,
            transferStatus = TransferStatus.NONE,
            userCategoryId = com.spendsms.app.domain.model.CategoryId.of("not_a_system_category"),
        )
        assertThat(id).isEqualTo(SystemCategories.FOOD_AND_DINING)
    }

    private fun merchant(key: String, display: String): Merchant =
        Merchant(MerchantKey.of(key), display, display)
}
