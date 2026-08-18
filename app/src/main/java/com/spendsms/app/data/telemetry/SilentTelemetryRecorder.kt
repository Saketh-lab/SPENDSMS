package com.spendsms.app.data.telemetry

import com.spendsms.app.application.port.telemetry.TelemetryEvent
import com.spendsms.app.application.port.telemetry.TelemetryPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fire-and-forget telemetry helper. Swallows all failures so product flows never block.
 */
@Singleton
class SilentTelemetryRecorder @Inject constructor(
    private val telemetryPort: TelemetryPort,
) {

    suspend fun record(events: List<TelemetryEvent>) {
        if (events.isEmpty()) return
        runCatching { telemetryPort.enqueue(events) }
    }

    suspend fun flush() {
        runCatching { telemetryPort.flush() }
    }
}
