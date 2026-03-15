package com.gniza.backup.data.repository

import com.gniza.backup.data.local.dao.ScheduleDao
import com.gniza.backup.data.local.entity.ScheduleEntity
import com.gniza.backup.domain.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ScheduleRepository @Inject constructor(
    private val scheduleDao: ScheduleDao
) {
    val allSchedules: Flow<List<Schedule>> = scheduleDao.getAll().map { entities ->
        entities.map { it.toSchedule() }
    }

    fun getSchedule(id: Long): Flow<Schedule?> =
        scheduleDao.getById(id).map { it?.toSchedule() }

    suspend fun getScheduleSync(id: Long): Schedule? =
        scheduleDao.getByIdSync(id)?.toSchedule()

    suspend fun getEnabledSchedules(): List<Schedule> =
        scheduleDao.getEnabled().map { it.toSchedule() }

    suspend fun saveSchedule(schedule: Schedule): Long {
        val entity = ScheduleEntity.fromSchedule(schedule)
        return if (schedule.id == 0L) {
            scheduleDao.insert(entity)
        } else {
            scheduleDao.update(entity)
            schedule.id
        }
    }

    suspend fun deleteSchedule(schedule: Schedule) {
        scheduleDao.delete(ScheduleEntity.fromSchedule(schedule))
    }
}
