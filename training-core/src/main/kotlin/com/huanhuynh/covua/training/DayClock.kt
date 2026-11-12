package com.huanhuynh.covua.training

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Mọi phép tính "ngày" đi qua đây. Ngày = epoch day theo GIỜ ĐỊA PHƯƠNG (LocalDate.toEpochDay()).
 * Test dùng [FixedDayClock].
 */
interface DayClock {
    fun today(): Long
    /** Phút trong ngày (0–1439) theo giờ địa phương. */
    fun minuteOfDay(): Int
    fun nowMillis(): Long

    companion object {
        fun dayOfWeek(epochDay: Long): DayOfWeek = LocalDate.ofEpochDay(epochDay).dayOfWeek
        /** Thứ Hai của tuần chứa [epochDay]. */
        fun weekStart(epochDay: Long): Long = epochDay - (dayOfWeek(epochDay).value - 1)
        fun dayOfMillis(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
    }
}

class SystemDayClock(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : DayClock {
    override fun today(): Long = LocalDate.now(zone()).toEpochDay()
    override fun minuteOfDay(): Int = LocalDateTime.now(zone()).let { it.hour * 60 + it.minute }
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class FixedDayClock(var day: Long, var minute: Int = 600, var millis: Long = day * 86_400_000L) : DayClock {
    override fun today() = day
    override fun minuteOfDay() = minute
    override fun nowMillis() = millis
}
