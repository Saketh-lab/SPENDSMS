package com.spendsms.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanStateTest {

    private val period = AnalysisPeriod(
        start = EpochMillis.of(1_000L),
        end = EpochMillis.of(2_000L),
    )

    @Test
    fun analysisPeriod_rejectsInvertedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalysisPeriod(
                start = EpochMillis.of(5L),
                end = EpochMillis.of(1L),
            )
        }
    }

    @Test
    fun runningScan_isActiveAndForbidsCompletedAt() {
        val state = ScanState(
            id = ScanId.of("scan-1"),
            period = period,
            lastProcessedMessageId = "msg-10",
            parserVersion = ParserVersion.of("2026.08.10.1"),
            status = ScanStatus.RUNNING,
            processedCount = 10,
            acceptedCount = 4,
            startedAt = EpochMillis.of(1_500L),
            completedAt = null,
            updatedAt = EpochMillis.of(1_600L),
        )
        assertThat(state.isActive).isTrue()
        assertThat(state.status).isEqualTo(ScanStatus.RUNNING)
    }

    @Test
    fun runningScan_withCompletedAt_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanState(
                id = ScanId.of("scan-1"),
                period = period,
                lastProcessedMessageId = null,
                parserVersion = ParserVersion.of("2026.08.10.1"),
                status = ScanStatus.RUNNING,
                processedCount = 0,
                acceptedCount = 0,
                startedAt = EpochMillis.of(1_500L),
                completedAt = EpochMillis.of(1_700L),
                updatedAt = EpochMillis.of(1_700L),
            )
        }
    }

    @Test
    fun completedScan_requiresCompletedAt() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanState(
                id = ScanId.of("scan-1"),
                period = period,
                lastProcessedMessageId = "msg-99",
                parserVersion = ParserVersion.of("2026.08.10.1"),
                status = ScanStatus.COMPLETED,
                processedCount = 5,
                acceptedCount = 5,
                startedAt = EpochMillis.of(1_500L),
                completedAt = null,
                updatedAt = EpochMillis.of(1_800L),
            )
        }
    }

    @Test
    fun acceptedCount_cannotExceedProcessedCount() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanState(
                id = ScanId.of("scan-1"),
                period = period,
                lastProcessedMessageId = null,
                parserVersion = ParserVersion.of("2026.08.10.1"),
                status = ScanStatus.PENDING,
                processedCount = 1,
                acceptedCount = 2,
                startedAt = EpochMillis.of(1_500L),
                completedAt = null,
                updatedAt = EpochMillis.of(1_500L),
            )
        }
    }

    @Test
    fun scanStatus_matchesStep3Vocabulary() {
        assertThat(ScanStatus.entries.map { it.name }).containsExactly(
            "PENDING",
            "RUNNING",
            "COMPLETED",
            "INTERRUPTED",
            "CANCELLED",
            "FAILED",
        ).inOrder()
    }
}

class ParserMetadataTest {

    @Test
    fun activeBundle_requiresActivatedAt() {
        assertThrows(IllegalArgumentException::class.java) {
            ParserMetadata(
                parserVersion = ParserVersion.of("2026.08.10.1"),
                rulesVersion = RulesVersion.of("rules-1"),
                schemaVersion = 1,
                checksum = "abc",
                installedAt = EpochMillis.of(10L),
                activatedAt = null,
                status = ParserBundleStatus.ACTIVE,
            )
        }
    }

    @Test
    fun schemaVersion_mustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            ParserMetadata(
                parserVersion = ParserVersion.of("2026.08.10.1"),
                rulesVersion = RulesVersion.of("rules-1"),
                schemaVersion = 0,
                checksum = "abc",
                installedAt = EpochMillis.of(10L),
                activatedAt = null,
                status = ParserBundleStatus.INSTALLED,
            )
        }
    }

    @Test
    fun installedBundle_isValidWithoutActivation() {
        val meta = ParserMetadata(
            parserVersion = ParserVersion.of("2026.08.10.1"),
            rulesVersion = RulesVersion.of("rules-1"),
            schemaVersion = 1,
            checksum = "deadbeef",
            installedAt = EpochMillis.of(10L),
            activatedAt = null,
            status = ParserBundleStatus.INSTALLED,
        )
        assertThat(meta.status).isEqualTo(ParserBundleStatus.INSTALLED)
    }

    @Test
    fun parserBundleStatus_matchesStep3Vocabulary() {
        assertThat(ParserBundleStatus.entries.map { it.name }).containsExactly(
            "INSTALLED",
            "ACTIVE",
            "ROLLBACK",
            "INVALID",
        ).inOrder()
    }
}
