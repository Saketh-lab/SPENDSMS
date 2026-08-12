package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.PaymentMethod

/**
 * Lightweight payment-rail classification from SMS text after a declarative match.
 * Keyword signals only — not a competing template engine.
 */
object PaymentMethodClassifier {

    fun classify(body: String): PaymentMethod {
        val text = body
        return when {
            UPI.containsMatchIn(text) -> PaymentMethod.UPI
            ATM.containsMatchIn(text) -> PaymentMethod.ATM
            CARD.containsMatchIn(text) -> PaymentMethod.CARD
            NET_BANKING.containsMatchIn(text) -> PaymentMethod.NET_BANKING
            WALLET.containsMatchIn(text) -> PaymentMethod.WALLET
            BILL.containsMatchIn(text) -> PaymentMethod.BILL_PAYMENT
            else -> PaymentMethod.UNKNOWN
        }
    }

    private val UPI = Regex("""(?i)\b(upi|vpa|@oksbi|@okhdfc|@ybl|@paytm)\b""")
    private val ATM = Regex("""(?i)\b(atm|cash\s+w/?d|cash\s+withdrawal)\b""")
    private val CARD = Regex("""(?i)\b(card|pos|visa|mastercard|rupay|\*{4}\d{4}|xx\d{4})\b""")
    private val NET_BANKING = Regex("""(?i)\b(neft|imps|rtgs|netbanking|net\s+banking)\b""")
    private val WALLET = Regex("""(?i)\b(wallet|paytm\s+wallet|amazon\s+pay\s+balance)\b""")
    private val BILL = Regex("""(?i)\b(bill\s+pay(?:ment)?|bbps)\b""")
}
