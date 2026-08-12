package com.spendsms.app.application

/**
 * Application / use-case layer boundary.
 *
 * Owns orchestration contracts (ports) for analysis, corrections, deletion,
 * parser updates, and control-plane integrations. Depends on domain types only.
 * Implementations live in the data/platform layers.
 */
object ApplicationLayer
