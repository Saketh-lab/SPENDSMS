package com.spendsms.app.data.sms

import android.provider.Telephony
import com.spendsms.app.application.port.sms.EphemeralSmsMessage
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis

/**
 * Read-only inbox query helpers. Never used for insert/update/delete.
 */
object InboxSmsQuery {

    val PROJECTION: Array<String> = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.DATE,
        Telephony.Sms.BODY,
    )

    const val SORT_ORDER: String = Telephony.Sms._ID + " ASC"

    fun selection(period: AnalysisPeriod, afterMessageId: String?): Pair<String, Array<String>> {
        val clauses = mutableListOf(
            "${Telephony.Sms.DATE} >= ?",
            "${Telephony.Sms.DATE} <= ?",
        )
        val args = mutableListOf(
            period.start.toEpochMillis.toString(),
            period.end.toEpochMillis.toString(),
        )
        if (afterMessageId != null) {
            val cursorId = afterMessageId.toLongOrNull()
                ?: throw IllegalArgumentException("afterMessageId must be numeric")
            clauses += "${Telephony.Sms._ID} > ?"
            args += cursorId.toString()
        }
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    /**
     * Maps a provider row. Returns null when the row cannot be processed
     * (blank sender/body) so the coordinator can isolate it.
     */
    fun mapRow(
        id: String?,
        address: String?,
        dateMillis: Long?,
        body: String?,
    ): EphemeralSmsMessage? {
        val sourceId = id?.trim().orEmpty()
        val sender = address?.trim().orEmpty()
        val received = dateMillis ?: return null
        val text = body ?: return null
        if (sourceId.isEmpty() || sender.isEmpty() || text.isEmpty() || received < 0L) {
            return null
        }
        return EphemeralSmsMessage(
            sourceMessageId = sourceId,
            sender = sender,
            receivedAt = EpochMillis.of(received),
            body = text,
        )
    }
}
