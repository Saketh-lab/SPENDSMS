package com.spendsms.app.platform.work

import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.spendsms.app.application.analysis.DEFAULT_SCAN_BATCH_SIZE
import com.spendsms.app.application.analysis.ScanScheduleRequest
import com.spendsms.app.application.analysis.ScanWorkInput
import com.spendsms.app.application.analysis.ScanWorkOutcome
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ScanId
import java.util.concurrent.TimeUnit

/**
 * WorkManager input/output keys and request factory for the unique SMS scan.
 *
 * Raw SMS bodies are never placed in [Data].
 */
object ScanWorkContract {
    const val UNIQUE_WORK_NAME: String = "spendsms.sms_scan"

    const val KEY_PERIOD_START: String = "period_start_millis"
    const val KEY_PERIOD_END: String = "period_end_millis"
    const val KEY_RESUME_SCAN_ID: String = "resume_scan_id"
    const val KEY_BATCH_SIZE: String = "batch_size"
    const val KEY_OUTCOME: String = "outcome"
    const val KEY_SCAN_ID: String = "scan_id"
    const val KEY_REASON: String = "reason"

    fun createRequest(request: ScanScheduleRequest): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<ScanSmsWorker>()
            .setInputData(toInputData(request.toWorkInput()))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .addTag(UNIQUE_WORK_NAME)
            .build()
    }

    fun toInputData(input: ScanWorkInput): Data {
        val builder = Data.Builder()
            .putLong(KEY_PERIOD_START, input.period.start.toEpochMillis)
            .putLong(KEY_PERIOD_END, input.period.end.toEpochMillis)
            .putInt(KEY_BATCH_SIZE, input.batchSize)
        if (input.resumeScanId != null) {
            builder.putString(KEY_RESUME_SCAN_ID, input.resumeScanId.value)
        }
        return builder.build()
    }

    fun parseInput(data: Data): ScanWorkInput? {
        val start = data.getLong(KEY_PERIOD_START, -1L)
        val end = data.getLong(KEY_PERIOD_END, -1L)
        if (start < 0L || end < 0L || start > end) return null
        val resume = data.getString(KEY_RESUME_SCAN_ID)?.takeIf { it.isNotBlank() }?.let(ScanId::of)
        val batchSize = data.getInt(KEY_BATCH_SIZE, DEFAULT_SCAN_BATCH_SIZE)
        if (batchSize <= 0) return null
        return ScanWorkInput(
            period = AnalysisPeriod(EpochMillis.of(start), EpochMillis.of(end)),
            resumeScanId = resume,
            batchSize = batchSize,
        )
    }

    fun toWorkerResult(outcome: ScanWorkOutcome): ListenableWorker.Result {
        val output = toOutputData(outcome)
        return when (outcome) {
            is ScanWorkOutcome.Success -> ListenableWorker.Result.success(output)
            is ScanWorkOutcome.Retry -> ListenableWorker.Result.retry()
            is ScanWorkOutcome.Failure -> ListenableWorker.Result.failure(output)
        }
    }

    fun toOutputData(outcome: ScanWorkOutcome): Data {
        val kind = when (outcome) {
            is ScanWorkOutcome.Success -> outcome.status
            is ScanWorkOutcome.Retry -> "RETRY"
            is ScanWorkOutcome.Failure -> "FAILURE"
        }
        return workDataOf(
            KEY_OUTCOME to kind,
            KEY_SCAN_ID to outcome.scanId?.value,
            KEY_REASON to outcome.reason,
        )
    }
}
