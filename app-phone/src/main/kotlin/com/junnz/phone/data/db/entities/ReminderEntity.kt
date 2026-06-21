package com.junnz.phone.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.junnz.shared.domain.ReminderStatus

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val text: String,
    val rawTranscript: String,
    val createdAtMs: Long,
    val status: ReminderStatus,
    val tagsJson: String,   // JSON array
    val triggersJson: String, // JSON array of Trigger
    val priority: Int = 0,
)
