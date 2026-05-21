package ru.wildanalytics.pub.analytics.db

import androidx.room.TypeConverter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal class OffsetDateTimeConverter {

    @TypeConverter
    fun fromDate(date: OffsetDateTime): String = date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    @TypeConverter
    fun toDate(value: String): OffsetDateTime = OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
