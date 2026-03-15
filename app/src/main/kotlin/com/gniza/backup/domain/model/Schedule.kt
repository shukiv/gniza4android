package com.gniza.backup.domain.model

data class Schedule(
    val id: Long = 0,
    val name: String = "",
    val sourceId: Long = 0,
    val serverId: Long = 0,
    val destinationPath: String = "",
    val interval: ScheduleInterval = ScheduleInterval.DAILY,
    val enabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val whileCharging: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
