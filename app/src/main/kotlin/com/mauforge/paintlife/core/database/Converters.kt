package com.mauforge.paintlife.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Instants are stored as UTC epoch milliseconds (sub-millisecond precision is dropped),
 * zones as IANA ids or fixed offsets, and local dates as ISO-8601 text (sorts correctly).
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun zoneIdToString(value: ZoneId?): String? = value?.id

    @TypeConverter
    fun stringToZoneId(value: String?): ZoneId? = value?.let(ZoneId::of)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
