package com.spendsms.app.platform.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.spendsms.app.application.analysis.ScanCoordinator
import com.spendsms.app.application.analysis.ScanScheduleRequest
import com.spendsms.app.application.analysis.ScanScheduleResult
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.ScanWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class WorkManagerScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanCoordinator: ScanCoordinator,
    private val scanStateRepository: ScanStateRepository,
) : ScanWorkScheduler {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override suspend fun enqueue(request: ScanScheduleRequest): ScanScheduleResult {
        val infos = withContext(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWork(ScanWorkContract.UNIQUE_WORK_NAME).get()
        }
        val active = infos.firstOrNull { !it.state.isFinished }
        if (active != null) {
            return ScanScheduleResult.AlreadyScheduled(workId = active.id.toString())
        }
        val workRequest = ScanWorkContract.createRequest(request)
        workManager.enqueueUniqueWork(
            ScanWorkContract.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
        return ScanScheduleResult.Enqueued(workId = workRequest.id.toString())
    }

    override suspend fun cancel() {
        val resumable = scanStateRepository.findResumable()
        if (resumable != null) {
            scanCoordinator.requestCancel(resumable.id)
        }
        workManager.cancelUniqueWork(ScanWorkContract.UNIQUE_WORK_NAME)
    }
}
