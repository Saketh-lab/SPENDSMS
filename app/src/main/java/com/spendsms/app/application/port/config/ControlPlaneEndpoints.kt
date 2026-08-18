package com.spendsms.app.application.port.config

/**
 * Runtime view of whether AWS/CDN control-plane endpoints are configured.
 */
interface ControlPlaneEndpoints {
    val isApiConfigured: Boolean
    val isParserCdnConfigured: Boolean
    val isLocalOnlyMode: Boolean
}
