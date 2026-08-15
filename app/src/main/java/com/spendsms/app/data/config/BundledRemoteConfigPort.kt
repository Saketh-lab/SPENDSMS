package com.spendsms.app.data.config

import com.spendsms.app.application.port.config.AppRemoteConfig
import com.spendsms.app.application.port.config.RemoteConfigPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-0 offline-capable remote config: bundled defaults until Prompt 15 wires CDN config.
 */
@Singleton
class BundledRemoteConfigPort @Inject constructor() : RemoteConfigPort {
    override suspend fun getConfig(): AppRemoteConfig = AppRemoteConfig.bundledDefaults()
}
