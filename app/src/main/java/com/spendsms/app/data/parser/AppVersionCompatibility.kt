package com.spendsms.app.data.parser

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compares dotted numeric app versions (major.minor.patch…).
 * Pre-release / build suffixes after `-` or `+` are ignored for compatibility.
 */
@Singleton
class AppVersionCompatibility @Inject constructor() {

    fun isCompatible(currentAppVersion: String, minimumAppVersion: String): Boolean {
        val current = parse(currentAppVersion)
        val minimum = parse(minimumAppVersion)
        if (current.isEmpty() || minimum.isEmpty()) return false
        val len = maxOf(current.size, minimum.size)
        for (i in 0 until len) {
            val c = current.getOrElse(i) { 0 }
            val m = minimum.getOrElse(i) { 0 }
            if (c > m) return true
            if (c < m) return false
        }
        return true
    }

    private fun parse(version: String): List<Int> {
        val core = version.trim().substringBefore('-').substringBefore('+')
        if (core.isBlank()) return emptyList()
        return core.split('.').map { part ->
            part.toIntOrNull() ?: return emptyList()
        }
    }
}
