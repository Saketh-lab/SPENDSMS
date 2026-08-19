package com.spendsms.app.data.sms

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import org.junit.Test

class InboxSmsQueryTest {

    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(5_000L))

    @Test
    fun selection_includesInclusiveDateBounds() {
        val (sql, args) = InboxSmsQuery.selection(period, afterMessageId = null)
        assertThat(sql).contains("date >= ?")
        assertThat(sql).contains("date <= ?")
        assertThat(args.toList()).containsExactly("1000", "5000")
        assertThat(sql).doesNotContain("_id")
    }

    @Test
    fun selection_appendsNumericCursor() {
        val (sql, args) = InboxSmsQuery.selection(period, afterMessageId = "42")
        assertThat(sql).contains("_id > ?")
        assertThat(args.toList()).containsExactly("1000", "5000", "42")
    }

    @Test
    fun mapRow_rejectsBlankSenderOrBody() {
        assertThat(InboxSmsQuery.mapRow("1", "", 1L, "hello")).isNull()
        assertThat(InboxSmsQuery.mapRow("1", "BANK", 1L, "")).isNull()
        assertThat(InboxSmsQuery.mapRow("1", "BANK", 1L, null)).isNull()
    }

    @Test
    fun mapRow_doesNotPutBodyInToString() {
        val message = InboxSmsQuery.mapRow("9", "EX-BANK", 1_000L, "SECRET_SMS_BODY")
        assertThat(message).isNotNull()
        assertThat(message!!.toString()).doesNotContain("SECRET_SMS_BODY")
        assertThat(message.toString()).doesNotContain("EX-BANK")
        assertThat(message.toString()).contains("body=***")
        assertThat(message.toString()).contains("sender=***")
    }
}
