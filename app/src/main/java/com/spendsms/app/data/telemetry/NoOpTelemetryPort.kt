package com.spendsms.app.data.telemetry

import com.spendsms.app.application.port.telemetry.TelemetryEvent
import com.spendsms.app.application.port.telemetry.TelemetryPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only telemetry adapter: accepts events but never transmits them.
 */
@Singleton
class NoOpTelemetryPort @Inject constructor() : TelemetryPort {

    override suspend fun enqueue(events: List<TelemetryEvent>) = Unit

    override suspend fun flush() = Unit
}
