package com.junnz.phone.data.db

import androidx.room.TypeConverter
import com.junnz.shared.domain.ReminderStatus

class Converters {
    @TypeConverter
    fun fromStatus(status: ReminderStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)
}
