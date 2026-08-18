package com.spendsms.app.data.support

import com.spendsms.app.application.port.support.RedactedUnsupportedFormatSubmission
import com.spendsms.app.application.port.support.SupportSubmissionPort
import com.spendsms.app.application.port.support.SupportSubmissionResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only support adapter: preserves the port contract but does not call AWS.
 *
 * Callers should gate UX on [com.spendsms.app.application.port.config.AppRemoteConfig.supportSubmissionEnabled].
 */
@Singleton
class UnavailableSupportSubmissionPort @Inject constructor() : SupportSubmissionPort {

    override suspend fun submit(
        submission: RedactedUnsupportedFormatSubmission,
    ): SupportSubmissionResult = SupportSubmissionResult(
        submissionId = submission.submissionId,
        accepted = false,
        deletionScheduledAt = null,
    )
}
