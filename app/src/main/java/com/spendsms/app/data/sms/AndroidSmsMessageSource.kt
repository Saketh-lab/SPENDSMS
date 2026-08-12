package com.spendsms.app.data.sms

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Telephony
import com.spendsms.app.application.port.sms.EphemeralSmsMessage
import com.spendsms.app.application.port.sms.SmsAccessException
import com.spendsms.app.application.port.sms.SmsBatch
import com.spendsms.app.application.port.sms.SmsBatchQuery
import com.spendsms.app.application.port.sms.SmsMessageSource
import com.spendsms.app.application.port.sms.SmsPermissionPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Read-only [Telephony.Sms.Inbox] access. Never modifies, sends, or marks messages.
 */
@Singleton
class AndroidSmsMessageSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionPort: SmsPermissionPort,
) : SmsMessageSource {

    override suspend fun readBatch(query: SmsBatchQuery): SmsBatch {
        currentCoroutineContext().ensureActive()
        if (!permissionPort.hasReadSmsPermission()) {
            throw SmsAccessException.PermissionDenied()
        }
        val (selection, args) = try {
            InboxSmsQuery.selection(query.period, query.afterMessageId)
        } catch (e: IllegalArgumentException) {
            throw SmsAccessException.ProviderError("Invalid SMS batch cursor", e)
        }
        return queryInbox(context.contentResolver, selection, args, query.limit)
    }

    private suspend fun queryInbox(
        resolver: ContentResolver,
        selection: String,
        args: Array<String>,
        limit: Int,
    ): SmsBatch {
        val signal = CancellationSignal()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { signal.cancel() }
            try {
                if (!permissionPort.hasReadSmsPermission()) {
                    cont.resumeWithException(SmsAccessException.PermissionRevoked())
                    return@suspendCancellableCoroutine
                }
                val extras = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, InboxSmsQuery.SORT_ORDER)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                }
                val cursor = resolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    InboxSmsQuery.PROJECTION,
                    extras,
                    signal,
                )
                if (cursor == null) {
                    cont.resumeWithException(
                        SmsAccessException.ProviderError("SMS provider returned null cursor"),
                    )
                    return@suspendCancellableCoroutine
                }
                cursor.use { rows ->
                    val messages = ArrayList<EphemeralSmsMessage>(limit)
                    val idIndex = rows.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val addressIndex = rows.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val dateIndex = rows.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val bodyIndex = rows.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    var lastSeenId: String? = null
                    while (rows.moveToNext()) {
                        val id = rows.getString(idIndex)
                        if (!id.isNullOrBlank()) lastSeenId = id
                        val mapped = InboxSmsQuery.mapRow(
                            id = id,
                            address = rows.getString(addressIndex),
                            dateMillis = if (rows.isNull(dateIndex)) null else rows.getLong(dateIndex),
                            body = rows.getString(bodyIndex),
                        )
                        if (mapped != null) {
                            messages += mapped
                        }
                    }
                    cont.resume(
                        SmsBatch(
                            messages = messages,
                            nextAfterMessageId = messages.lastOrNull()?.sourceMessageId ?: lastSeenId,
                            exhausted = rows.count < limit,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                cont.resumeWithException(SmsAccessException.PermissionRevoked(cause = e))
            } catch (e: SmsAccessException) {
                cont.resumeWithException(e)
            } catch (e: Exception) {
                cont.resumeWithException(
                    SmsAccessException.ProviderError("SMS provider query failed", e),
                )
            }
        }
    }
}
