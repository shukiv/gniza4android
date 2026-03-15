package com.gniza.backup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gniza.backup.domain.model.Schedule
import com.gniza.backup.domain.model.ScheduleInterval

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sourceId: Long,
    val serverId: Long,
    val destinationPath: String,
    val interval: String,
    val enabled: Boolean,
    val wifiOnly: Boolean,
    val whileCharging: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toSchedule(): Schedule = Schedule(
        id = id,
        name = name,
        sourceId = sourceId,
        serverId = serverId,
        destinationPath = destinationPath,
        interval = ScheduleInterval.valueOf(interval),
        enabled = enabled,
        wifiOnly = wifiOnly,
        whileCharging = whileCharging,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromSchedule(schedule: Schedule): ScheduleEntity = ScheduleEntity(
            id = schedule.id,
            name = schedule.name,
            sourceId = schedule.sourceId,
            serverId = schedule.serverId,
            destinationPath = schedule.destinationPath,
            interval = schedule.interval.name,
            enabled = schedule.enabled,
            wifiOnly = schedule.wifiOnly,
            whileCharging = schedule.whileCharging,
            createdAt = schedule.createdAt,
            updatedAt = schedule.updatedAt
        )
    }
}
