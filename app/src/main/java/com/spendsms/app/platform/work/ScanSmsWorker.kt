package com.spendsms.app.platform.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.spendsms.app.application.analysis.ScanWorkRunner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * WorkManager adapter for the local SMS scan. All analysis is delegated to [ScanWorkRunner].
 *
 * No foreground service: approved scans are batched and checkpointed, so WorkManager
 * can stop and later resume from persisted [com.spendsms.app.domain.model.ScanState].
 */
@HiltWorker
class ScanSmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: ScanWorkRunner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val input = ScanWorkContract.parseInput(inputData)
            ?: return Result.failure(
                workDataOf(ScanWorkContract.KEY_REASON to "INVALID_INPUT"),
            )
        return try {
            ScanWorkContract.toWorkerResult(runner.run(input))
        } catch (e: CancellationException) {
            throw e
        }
    }
}
