package ru.wildanalytics.pub.analytics.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * A [Clock] that dynamically queries the system default timezone and time on the fly,
 * ensuring any device settings updates (timezone or time changes) are reflected immediately.
 */
internal class DynamicSystemClock : Clock() {

    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()
}
