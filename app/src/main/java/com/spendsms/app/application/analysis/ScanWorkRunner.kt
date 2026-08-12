package com.spendsms.app.application.analysis

import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.sms.SmsPermissionPort
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
) {

    suspend fun run(input: ScanWorkInput): ScanWorkOutcome {
        if (!permissionPort.hasReadSmsPermission()) {
            return ScanWorkOutcome.Failure(
                scanId = null,
                reason = "PERMISSION_DENIED",
            )
        }
        val result = scanCoordinator.startScan(
            ScanRequest(
                period = input.period,
                resumeScanId = resolveResumeScanId(input),
                batchSize = input.batchSize,
            ),
        )
        return ScanWorkOutcomeMapper.fromScanResult(result)
    }

    private suspend fun resolveResumeScanId(input: ScanWorkInput): ScanId? {
        if (input.resumeScanId != null) return input.resumeScanId
        val resumable = scanStateRepository.findResumable() ?: return null
        return if (resumable.period == input.period) resumable.id else null
    }
}
