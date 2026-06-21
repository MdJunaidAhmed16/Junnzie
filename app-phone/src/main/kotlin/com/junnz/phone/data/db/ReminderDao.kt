package com.junnz.phone.data.db

import androidx.room.*
import com.junnz.phone.data.db.entities.ReminderEntity
import com.junnz.shared.domain.ReminderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = :status ORDER BY createdAtMs DESC")
    fun observeByStatus(status: ReminderStatus): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status NOT IN ('DISMISSED', 'EXPIRED') ORDER BY createdAtMs DESC")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ReminderEntity>)

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ReminderStatus)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'PENDING'")
    suspend fun pendingCount(): Int
}
