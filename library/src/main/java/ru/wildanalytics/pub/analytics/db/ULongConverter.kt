package ru.wildanalytics.pub.analytics.db

import androidx.room.TypeConverter

internal class ULongConverter {

    @TypeConverter
    fun fromULong(value: ULong): Long = value.toLong()

    @TypeConverter
    fun toULong(value: Long): ULong = value.toULong()
}
