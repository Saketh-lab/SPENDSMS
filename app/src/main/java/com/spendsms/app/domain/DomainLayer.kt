package com.spendsms.app.domain

/**
 * Domain layer boundary.
 *
 * Owns pure business models and rules (filtering, parsing, merchant
 * normalization, deduplication, categorisation, subscriptions, dashboard
 * math, corrections). Must not depend on Android framework types, Room
 * entities, Retrofit DTOs, or AWS concepts.
 */
object DomainLayer
