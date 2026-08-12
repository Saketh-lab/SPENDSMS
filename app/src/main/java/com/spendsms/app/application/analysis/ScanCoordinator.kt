package com.spendsms.app.application.analysis

import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserBundleManager
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.sms.EphemeralSmsMessage
import com.spendsms.app.application.port.sms.SmsAccessException
import com.spendsms.app.application.port.sms.SmsBatchQuery
import com.spendsms.app.application.port.sms.SmsMessageSource
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.domain.model.ScanStatus
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/**
 * Foreground local scan orchestration (Step-3 Scan Coordinator).
 *
 * Pipeline: read → filter/parse → classify → deduplicate/persist → checkpoint.
 * Raw SMS bodies stay in memory for the current message only.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    private val smsSource: SmsMessageSource,
    private val permissionPort: SmsPermissionPort,
    private val parserBundleManager: ParserBundleManager,
    private val parsingPipeline: MessageParsingPipeline,
    private val classificationService: TransactionClassificationService,
    private val duplicateDetectionService: DuplicateDetectionService,
    private val scanStateRepository: ScanStateRepository,
    private val scanIdFactory: ScanIdFactory,
    private val clock: ScanClock,
) {
    private val cancelRequested: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun requestCancel(scanId: ScanId) {
        cancelRequested += scanId.value
    }

    suspend fun startScan(
        request: ScanRequest,
        onProgress: ScanProgressListener? = null,
    ): ScanResult {
        val existingActive = scanStateRepository.findActive()
        if (request.resumeScanId == null && existingActive != null) {
            return if (existingActive.period == request.period) {
                runScan(existingActive, request.batchSize, onProgress)
            } else {
                ScanResult.Failed(
                    state = existingActive,
                    reason = ScanFailureReason.SCAN_ALREADY_ACTIVE,
                    detail = "An active scan already exists",
                )
            }
        }
        if (request.resumeScanId != null) {
            val existing = scanStateRepository.findById(request.resumeScanId)
                ?: return ScanResult.Failed(
                    state = null,
                    reason = ScanFailureReason.NOT_RESUMABLE,
                    detail = "Scan ${request.resumeScanId.value} not found",
                )
            if (existing.status == ScanStatus.COMPLETED ||
                existing.status == ScanStatus.CANCELLED ||
                existing.status == ScanStatus.FAILED
            ) {
                return ScanResult.Failed(
                    state = existing,
                    reason = ScanFailureReason.NOT_RESUMABLE,
                    detail = "Scan is ${existing.status} and cannot resume",
                )
            }
            return runScan(existing, request.batchSize, onProgress)
        }

        if (!permissionPort.hasReadSmsPermission()) {
            return ScanResult.Failed(
                state = null,
                reason = ScanFailureReason.PERMISSION_DENIED,
                detail = "READ_SMS permission is not granted",
            )
        }

        val rules = loadRules()
            ?: return ScanResult.Failed(
                state = null,
                reason = ScanFailureReason.PARSER_UNAVAILABLE,
                detail = "Active parser rules are unavailable",
            )

        val now = clock.now()
        val created = ScanState(
            id = scanIdFactory.next(),
            period = request.period,
            lastProcessedMessageId = null,
            parserVersion = rules.parserVersion,
            status = ScanStatus.PENDING,
            processedCount = 0,
            acceptedCount = 0,
            startedAt = now,
            completedAt = null,
            updatedAt = now,
        )
        scanStateRepository.save(created)
        onProgress?.onProgress(created)
        return runScan(created, request.batchSize, onProgress, rules)
    }

    private suspend fun runScan(
        initial: ScanState,
        batchSize: Int,
        onProgress: ScanProgressListener?,
        preloadedRules: DeclarativeParserRules? = null,
    ): ScanResult {
        cancelRequested.remove(initial.id.value)
        var isolatedFailures = 0
        var state = initial
        try {
            if (!permissionPort.hasReadSmsPermission()) {
                return failOrInterrupt(
                    state = state,
                    deniedAtStart = initial.status == ScanStatus.PENDING &&
                        initial.processedCount == 0,
                )
            }
            val rules = preloadedRules ?: loadRules()
                ?: return persistFailed(state, ScanFailureReason.PARSER_UNAVAILABLE, "Parser rules unavailable")

            state = persist(
                state.copy(
                    status = ScanStatus.RUNNING,
                    parserVersion = rules.parserVersion,
                    completedAt = null,
                    updatedAt = clock.now(),
                ),
                onProgress,
            )

            while (currentCoroutineContext().isActive) {
                if (cancelRequested.contains(state.id.value)) {
                    return persistCancelled(state, isolatedFailures, onProgress)
                }
                currentCoroutineContext().ensureActive()
                if (!permissionPort.hasReadSmsPermission()) {
                    return persistInterrupted(
                        state = state,
                        isolatedFailures = isolatedFailures,
                        reason = ScanInterruptReason.PERMISSION_REVOKED,
                        onProgress = onProgress,
                    )
                }

                val batch = try {
                    smsSource.readBatch(
                        SmsBatchQuery(
                            period = state.period,
                            afterMessageId = state.lastProcessedMessageId,
                            limit = batchSize,
                        ),
                    )
                } catch (e: SmsAccessException.PermissionDenied) {
                    return persistInterrupted(
                        state, isolatedFailures, ScanInterruptReason.PERMISSION_REVOKED, onProgress,
                    )
                } catch (e: SmsAccessException.PermissionRevoked) {
                    return persistInterrupted(
                        state, isolatedFailures, ScanInterruptReason.PERMISSION_REVOKED, onProgress,
                    )
                } catch (e: SmsAccessException.ProviderError) {
                    return persistInterrupted(
                        state, isolatedFailures, ScanInterruptReason.PROVIDER_ERROR, onProgress,
                    )
                }

                var processed = state.processedCount
                var accepted = state.acceptedCount
                var lastId = state.lastProcessedMessageId
                for (message in batch.messages) {
                    if (cancelRequested.contains(state.id.value)) {
                        state = persist(
                            state.copy(
                                processedCount = processed,
                                acceptedCount = accepted,
                                lastProcessedMessageId = lastId,
                                updatedAt = clock.now(),
                            ),
                            onProgress,
                        )
                        return persistCancelled(state, isolatedFailures, onProgress)
                    }
                    currentCoroutineContext().ensureActive()
                    val outcome = processOne(message, rules)
                    processed += 1
                    if (outcome == MessageOutcome.ACCEPTED) accepted += 1
                    if (outcome == MessageOutcome.ISOLATED_FAILURE) isolatedFailures += 1
                    lastId = message.sourceMessageId
                }
                if (batch.nextAfterMessageId != null) {
                    lastId = batch.nextAfterMessageId
                }
                state = persist(
                    state.copy(
                        processedCount = processed,
                        acceptedCount = accepted,
                        lastProcessedMessageId = lastId,
                        updatedAt = clock.now(),
                    ),
                    onProgress,
                )
                if (batch.exhausted) break
                if (batch.messages.isEmpty() && batch.nextAfterMessageId == null) break
            }

            if (cancelRequested.contains(state.id.value)) {
                return persistCancelled(state, isolatedFailures, onProgress)
            }
            val completed = persist(
                state.copy(
                    status = ScanStatus.COMPLETED,
                    completedAt = clock.now(),
                    updatedAt = clock.now(),
                ),
                onProgress,
            )
            return ScanResult.Completed(completed, isolatedFailures)
        } catch (e: CancellationException) {
            persistInterrupted(
                state = state,
                isolatedFailures = isolatedFailures,
                reason = ScanInterruptReason.COROUTINE_CANCELLED,
                onProgress = onProgress,
            )
            throw e
        } catch (e: Exception) {
            return persistFailed(
                state = state,
                reason = ScanFailureReason.UNEXPECTED,
                detail = e::class.java.simpleName,
            )
        } finally {
            cancelRequested.remove(state.id.value)
        }
    }

    private suspend fun processOne(
        message: EphemeralSmsMessage,
        rules: DeclarativeParserRules,
    ): MessageOutcome {
        return try {
            when (val parsed = parsingPipeline.process(message, rules)) {
                MessagePipelineResult.Rejected -> MessageOutcome.REJECTED
                is MessagePipelineResult.ParseFailed -> MessageOutcome.REJECTED
                is MessagePipelineResult.Parsed -> {
                    val classified = classificationService.classify(parsed.candidate)
                    duplicateDetectionService.resolve(classified, clock.now())
                    MessageOutcome.ACCEPTED
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            MessageOutcome.ISOLATED_FAILURE
        }
    }

    private suspend fun loadRules(): DeclarativeParserRules? {
        return when (val activation = parserBundleManager.ensureActiveOrFallbackToBundled()) {
            is ParserBundleActivationResult.Activated -> activation.rules
            is ParserBundleActivationResult.Failed -> parserBundleManager.loadActiveRules()
        }
    }

    private suspend fun persist(state: ScanState, onProgress: ScanProgressListener?): ScanState {
        val saved = scanStateRepository.save(state)
        onProgress?.onProgress(saved)
        return saved
    }

    private suspend fun persistCancelled(
        state: ScanState,
        isolatedFailures: Int,
        onProgress: ScanProgressListener?,
    ): ScanResult.Cancelled {
        val now = clock.now()
        val saved = persist(
            state.copy(
                status = ScanStatus.CANCELLED,
                completedAt = now,
                updatedAt = now,
            ),
            onProgress,
        )
        return ScanResult.Cancelled(saved, isolatedFailures)
    }

    private suspend fun persistInterrupted(
        state: ScanState,
        isolatedFailures: Int,
        reason: ScanInterruptReason,
        onProgress: ScanProgressListener?,
    ): ScanResult.Interrupted {
        val saved = persist(
            state.copy(
                status = ScanStatus.INTERRUPTED,
                updatedAt = clock.now(),
            ),
            onProgress,
        )
        return ScanResult.Interrupted(saved, isolatedFailures, reason)
    }

    private suspend fun persistFailed(
        state: ScanState,
        reason: ScanFailureReason,
        detail: String,
    ): ScanResult.Failed {
        val now = clock.now()
        val saved = scanStateRepository.save(
            state.copy(
                status = ScanStatus.FAILED,
                completedAt = now,
                updatedAt = now,
            ),
        )
        return ScanResult.Failed(saved, reason, detail)
    }

    private suspend fun failOrInterrupt(state: ScanState, deniedAtStart: Boolean): ScanResult {
        return if (deniedAtStart) {
            persistFailed(state, ScanFailureReason.PERMISSION_DENIED, "READ_SMS permission is not granted")
        } else {
            persistInterrupted(state, 0, ScanInterruptReason.PERMISSION_REVOKED, null)
        }
    }

    private enum class MessageOutcome {
        ACCEPTED,
        REJECTED,
        ISOLATED_FAILURE,
    }
}
