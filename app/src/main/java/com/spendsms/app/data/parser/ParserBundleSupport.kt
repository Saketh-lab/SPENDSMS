package com.spendsms.app.data.parser

import com.spendsms.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

fun interface AppVersionProvider {
    fun appVersionName(): String
}

@Singleton
class BuildConfigAppVersionProvider @Inject constructor() : AppVersionProvider {
    override fun appVersionName(): String = BuildConfig.VERSION_NAME
}

fun interface ParserBundleClock {
    fun nowMillis(): Long
}

@Singleton
class SystemParserBundleClock @Inject constructor() : ParserBundleClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
