package ru.wildanalytics.pub.analytics.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import kotlin.math.ceil

internal interface DeviceHardwareInfoProvider {
    val cpuFreqGhz: Double
    val totalRamGb: Int
    val processorName: String?
}

internal class DeviceHardwareInfoProviderImpl(
    private val context: Context,
) : DeviceHardwareInfoProvider {

    override val cpuFreqGhz: Double by lazy { readCpuMaxFreqGhz() }

    override val totalRamGb: Int by lazy { readTotalRamGb() }

    override val processorName: String? by lazy { readProcessorName() }

    private fun readCpuMaxFreqGhz(): Double = try {
        File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            .bufferedReader()
            .use { reader -> reader.readLine() }
            .toDouble() / KHZ_TO_GHZ_DIVISOR
    } catch (_: Exception) {
        0.0
    }

    private fun readTotalRamGb(): Int = try {
        val memoryInBytes = getMemorySizeInBytesFromAndroidSource()
            ?: getMemorySizeInBytesFromLinuxSource()
            ?: 0L
        ceil(memoryInBytes.toDouble() / GIGABYTE_IN_BYTES).toInt()
    } catch (_: Exception) {
        0
    }

    private fun getMemorySizeInBytesFromAndroidSource(): Long? = try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        memoryInfo.totalMem
    } catch (_: Exception) {
        null
    }

    /**
     * Линус Торвальдс в 2005 году захардкодил значения переменных и размер в килобайтах (kB) в файле `fs/proc/meminfo.c`, с тех пор
     * никаких изменений не было. Поэтому в функции используются самые простые проверки и преобразования строк.
     *
     * Из всего файла нам нужна одна строка:
     * > MemTotal:        XXXXXXX kB
     *
     * https://unix.stackexchange.com/a/199491
     */
    private fun getMemorySizeInBytesFromLinuxSource(): Long? = try {
        File("/proc/meminfo")
            .bufferedReader()
            .useLines { lines ->
                lines.filter { it.startsWith("MemTotal") }
                    .map {
                        it.substringAfter(':')
                            .trim()
                            .substringBefore(' ')
                            .toLong() * 1024L
                    }
                    .firstOrNull()
            }
    } catch (_: Exception) {
        null
    }

    private fun readProcessorName(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL
    } else {
        null
    }
        ?: Build.HARDWARE

    private companion object {
        private const val KHZ_TO_GHZ_DIVISOR = 1_000_000
        private const val GIGABYTE_IN_BYTES = 1024L * 1024L * 1024L
    }
}
