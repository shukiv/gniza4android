package com.gniza.backup.domain.model

data class Snapshot(
    val name: String,
    val isPartial: Boolean = false,
    val isLatest: Boolean = false
)
