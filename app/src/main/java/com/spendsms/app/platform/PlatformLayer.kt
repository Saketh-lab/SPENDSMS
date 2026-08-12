package com.spendsms.app.platform

/**
 * Android / platform infrastructure boundary.
 *
 * Hosts permissions helpers, security/Keystore wrappers, WorkManager wiring,
 * and network allow-list utilities. Presentation and domain must not reach
 * into platform implementation details directly when a use-case/repository
 * boundary exists.
 */
object PlatformLayer
