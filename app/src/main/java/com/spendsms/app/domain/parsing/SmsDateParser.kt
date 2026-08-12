package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.EpochMillis
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Parses common Indian SMS date/time capture values into epoch millis (UTC).
 */
object SmsDateParser {

    fun parseOrNull(raw: String?): EpochMillis? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        for (formatter in DATE_TIME_FORMATTERS) {
            try {
                val dt = LocalDateTime.parse(value, formatter)
                return EpochMillis.of(dt.toInstant(ZoneOffset.UTC).toEpochMilli())
            } catch (_: DateTimeParseException) {
                // try next
            }
        }
        for (formatter in DATE_FORMATTERS) {
            try {
                val d = LocalDate.parse(value, formatter)
                return EpochMillis.of(
                    d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                )
            } catch (_: DateTimeParseException) {
                // try next
            }
        }
        return null
    }

    private val DATE_TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    )

    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ISO_LOCAL_DATE,
    )
}
