package com.spendsms.app.presentation.common

import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.Money
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object UiFormatters {
    private val inrFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        currency = Currency.getInstance("INR")
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }

    fun money(money: Money): String {
        val major = money.amountMinorUnits / 100.0
        return if (money.currency.code == "INR") {
            inrFormat.format(major)
        } else {
            "${money.currency.code} %.2f".format(Locale.US, major)
        }
    }

    fun dateTime(epoch: EpochMillis?): String {
        if (epoch == null) return "—"
        return dateTimeFormat.format(Date(epoch.toEpochMillis))
    }

    fun date(epoch: EpochMillis): String = dateFormat.format(Date(epoch.toEpochMillis))

    fun periodLabel(period: AnalysisPeriod): String =
        "${date(period.start)} – ${date(period.end)}"

    fun confidencePercent(value: Double): String =
        "${(value * 100).toInt()}%"
}

enum class PeriodPreset(
    val label: String,
    val days: Long,
) {
    LAST_30_DAYS("Last 30 days", 30),
    LAST_3_MONTHS("Last 3 months", 90),
    LAST_6_MONTHS("Last 6 months", 180),
    LAST_12_MONTHS("Last 12 months", 365),
}

fun PeriodPreset.toAnalysisPeriod(nowMillis: Long = System.currentTimeMillis()): AnalysisPeriod {
    val end = nowMillis
    val start = end - TimeUnit.DAYS.toMillis(days)
    return AnalysisPeriod(EpochMillis.of(start.coerceAtLeast(0L)), EpochMillis.of(end))
}

sealed interface AsyncUiState<out T> {
    data object Loading : AsyncUiState<Nothing>
    data class Ready<T>(val value: T) : AsyncUiState<T>
    data class Empty(val message: String) : AsyncUiState<Nothing>
    data class Error(val message: String) : AsyncUiState<Nothing>
}
