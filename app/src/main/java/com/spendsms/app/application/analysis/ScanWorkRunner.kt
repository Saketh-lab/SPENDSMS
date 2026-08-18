package com.spendsms.app.application.analysis

import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ScanId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background entry point for SMS scans. Delegates all pipeline work to [ScanCoordinator].
 */
@Singleton
class ScanWorkRunner @Inject constructor(
    private val scanCoordinator: ScanCoordinator,
    private val scanStateRepository: ScanStateRepository,
    private val permissionPort: SmsPermissionPort,
    private val completionHandler: ScanCompletionHandler,
) {

    suspend fun run(input: ScanWorkInput): ScanWorkOutcome {
        if (!permissionPort.hasReadSmsPermission()) {
            return ScanWorkOutcome.Failure(
                scanId = null,
                reason = "PERMISSION_DENIED",
            )
        }
        val resumeId = resolveResumeScanId(input)
        if (resumeId == null) {
            val completed = scanStateRepository.findLatestCompleted()
            if (completed != null &&
                completed.period == input.period &&
                scanStateRepository.findResumable() == null
            ) {
                return ScanWorkOutcome.Success(
                    scanId = completed.id,
                    status = "COMPLETED",
                    reason = "ALREADY_COMPLETED",
                )
            }
        }
        val result = scanCoordinator.startScan(
            ScanRequest(
                period = input.period,
                resumeScanId = resumeId,
                batchSize = input.batchSize,
            ),
        )
        if (result is ScanResult.Completed) {
            completionHandler.onScanCompleted(
                period = result.state.period,
                now = result.state.completedAt ?: EpochMillis.of(System.currentTimeMillis()),
            )
        }
        return ScanWorkOutcomeMapper.fromScanResult(result)
    }

    private suspend fun resolveResumeScanId(input: ScanWorkInput): ScanId? {
        if (input.resumeScanId != null) return input.resumeScanId
        val resumable = scanStateRepository.findResumable() ?: return null
        return if (resumable.period == input.period) resumable.id else null
    }
}
