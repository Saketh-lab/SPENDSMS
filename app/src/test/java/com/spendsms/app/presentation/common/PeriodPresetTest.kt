package com.spendsms.app.presentation.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PeriodPresetTest {

    @Test
    fun presets_produceInclusiveForwardPeriods() {
        val now = 1_700_000_000_000L
        val period = PeriodPreset.LAST_30_DAYS.toAnalysisPeriod(now)
        assertThat(period.end.toEpochMillis).isEqualTo(now)
        assertThat(period.start.toEpochMillis).isLessThan(period.end.toEpochMillis)
        assertThat(period.end.toEpochMillis - period.start.toEpochMillis)
            .isEqualTo(30L * 24L * 60L * 60L * 1_000L)
    }
}
