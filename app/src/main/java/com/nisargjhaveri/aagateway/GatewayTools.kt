package com.nisargjhaveri.aagateway

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.SparseIntArray
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

data class HotspotDiagnostics(
    val active: Boolean,
    val band: String?,
    val frequencyMhz: Int?,
    val channel: Int?,
    val bssid: String?,
    val clientCount: Int?,
) {
    val summary: String
        get() {
            if (!active) {
                return "Hotspot inactive"
            }

            val radio =
                listOfNotNull(
                        band,
                        frequencyMhz?.let { "${it} MHz" },
                        channel?.let { "channel $it" },
                    )
                    .joinToString(" · ")
                    .ifBlank { "Radio details unsupported" }
            val clients =
                clientCount?.let { "$it client${if (it == 1) "" else "s"}" }
                    ?: "clients unsupported"
            return "$radio · $clients"
        }
}

data class ChargingThermalStatus(
    val charging: String,
    val thermal: String,
) {
    val summary: String
        get() = "$charging · Thermal $thermal"
}

data class GatewayDiagnostics(
    val hotspot: HotspotDiagnostics,
    val chargingThermal: ChargingThermalStatus,
) {
    companion object {
        fun read(context: Context): GatewayDiagnostics {
            val applicationContext = context.applicationContext
            return GatewayDiagnostics(
                hotspot = HotspotDiagnosticsReader.read(applicationContext),
                chargingThermal = ChargingThermalStatusReader.read(applicationContext),
            )
        }
    }
}

object GatewayRecovery {
    fun restart(context: Context): String {
        val applicationContext = context.applicationContext
        applicationContext.stopService(Intent(applicationContext, AAGatewayService::class.java))
        applicationContext.stopService(
            Intent(applicationContext, AAWirelessClientService::class.java)
        )

        val accessory =
            runCatching {
                applicationContext
                    .getSystemService(UsbManager::class.java)
                    ?.accessoryList
                    ?.firstOrNull()
            }
                .getOrNull() ?: return "Gateway session stopped. Reconnect USB to start it again."

        Handler(Looper.getMainLooper())
            .postDelayed(
                {
                    runCatching {
                        ContextCompat.startForegroundService(
                            applicationContext,
                            Intent(applicationContext, AAGatewayService::class.java).apply {
                                putExtra(UsbManager.EXTRA_ACCESSORY, accessory)
                            },
                        )
                    }
                },
                350L,
            )
        return "Gateway restart requested."
    }
}

object GatewayDiagnosticsExporter {
    fun createShareIntent(context: Context, diagnostics: GatewayDiagnostics): Intent {
        val applicationContext = context.applicationContext
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(applicationContext.cacheDir, "gateway-diagnostics-$timestamp.txt")
        file.writeText(
            buildString {
                appendLine("AA Wireless Gateway diagnostics")
                appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Hotspot active: ${diagnostics.hotspot.active}")
                appendLine("Hotspot band: ${diagnostics.hotspot.band ?: "Unsupported"}")
                appendLine(
                    "Hotspot frequency: ${
                        diagnostics.hotspot.frequencyMhz?.let { "$it MHz" } ?: "Unsupported"
                    }"
                )
                appendLine(
                    "Hotspot channel: ${
                        diagnostics.hotspot.channel?.toString() ?: "Unsupported"
                    }"
                )
                appendLine("Hotspot BSSID: ${diagnostics.hotspot.bssid ?: "Unsupported"}")
                appendLine(
                    "Hotspot clients: ${
                        diagnostics.hotspot.clientCount?.toString() ?: "Unsupported"
                    }"
                )
                appendLine("Charging: ${diagnostics.chargingThermal.charging}")
                appendLine("Thermal: ${diagnostics.chargingThermal.thermal}")
                appendLine()
                appendLine("AAService logcat:")
                appendLine(readLogcat())
            }
        )

        val uri =
            FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                file,
            )
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AA Wireless Gateway diagnostics")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share gateway diagnostics",
        )
    }

    private fun readLogcat(): String {
        val process =
            runCatching {
                ProcessBuilder("logcat", "-d", "-t", "300", "-s", "AAService:*")
                    .redirectErrorStream(true)
                    .start()
            }
                .getOrNull() ?: return "Logcat is unavailable."
        return try {
            process.inputStream
                .bufferedReader()
                .use { it.readText() }
                .ifBlank { "No AAService entries found." }
        } finally {
            process.destroy()
        }
    }
}

private object HotspotDiagnosticsReader {
    private val hotspotInterfacePattern = Regex("^(ap|swlan|wlan)\\d+$", RegexOption.IGNORE_CASE)

    fun read(context: Context): HotspotDiagnostics {
        val interfaceInfo = findHotspotInterface(context) ?: return inactive()
        val softApDetails = readSoftApDetails(context.getSystemService(WifiManager::class.java))
        return HotspotDiagnostics(
            active = true,
            band = softApDetails.band,
            frequencyMhz =
                softApDetails.channel?.let {
                    frequencyForChannel(it, softApDetails.band)
                },
            channel = softApDetails.channel,
            bssid = interfaceInfo.hardwareAddress ?: softApDetails.bssid,
            clientCount = readClientCount(interfaceInfo.name),
        )
    }

    private fun inactive() =
        HotspotDiagnostics(
            active = false,
            band = null,
            frequencyMhz = null,
            channel = null,
            bssid = null,
            clientCount = null,
        )

    private data class InterfaceInfo(
        val name: String,
        val hardwareAddress: String?,
    )

    private fun findHotspotInterface(context: Context): InterfaceInfo? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val clientInterfaces =
            connectivityManager
                ?.allNetworks
                .orEmpty()
                .mapNotNull { connectivityManager?.getLinkProperties(it)?.interfaceName }
                .toSet()

        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { networkInterface ->
                    networkInterface.isUp &&
                        !networkInterface.isLoopback &&
                        networkInterface.name !in clientInterfaces &&
                        hotspotInterfacePattern.matches(networkInterface.name) &&
                        Collections.list(networkInterface.inetAddresses).any {
                            it is Inet4Address && it.isSiteLocalAddress
                        }
                }
                .map { networkInterface ->
                    InterfaceInfo(
                        networkInterface.name,
                        networkInterface.hardwareAddress?.joinToString(":") {
                            "%02X".format(Locale.US, it)
                        },
                    )
                }
                .firstOrNull()
        }
            .getOrNull()
    }

    private data class SoftApDetails(
        val band: String?,
        val channel: Int?,
        val bssid: String?,
    )

    private fun readSoftApDetails(wifiManager: WifiManager?): SoftApDetails {
        if (wifiManager == null) {
            return SoftApDetails(null, null, null)
        }
        val configuration =
            runCatching {
                wifiManager.javaClass.methods
                    .firstOrNull {
                        it.name == "getSoftApConfiguration" && it.parameterTypes.isEmpty()
                    }
                    ?.invoke(wifiManager)
            }
                .getOrNull() ?: return SoftApDetails(null, null, null)

        val bandValue = invokeInt(configuration, "getBand")
        val channel = invokeChannels(configuration)?.firstChannel()
        return SoftApDetails(
            band = bandName(bandValue, channel),
            channel = channel,
            bssid = invokeString(configuration, "getBssid"),
        )
    }

    private fun invokeInt(target: Any, methodName: String): Int? = runCatching {
        target.javaClass.methods
            .firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
            ?.invoke(target) as? Int
    }
        .getOrNull()

    private fun invokeString(target: Any, methodName: String): String? = runCatching {
        target.javaClass.methods
            .firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
            ?.invoke(target)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }
        .getOrNull()

    private fun invokeChannels(target: Any): SparseIntArray? = runCatching {
        target.javaClass.methods
            .firstOrNull { it.name == "getChannels" && it.parameterTypes.isEmpty() }
            ?.invoke(target) as? SparseIntArray
    }
        .getOrNull()

    private fun SparseIntArray.firstChannel(): Int? {
        for (index in 0 until size()) {
            val channel = valueAt(index)
            if (channel > 0) {
                return channel
            }
        }
        return null
    }

    private fun bandName(band: Int?, channel: Int?): String? {
        return when {
            band != null && band and 1 != 0 -> "2.4 GHz"
            band != null && band and 2 != 0 -> "5 GHz"
            band != null && band and 4 != 0 -> "6 GHz"
            channel != null && channel <= 14 -> "2.4 GHz"
            channel != null -> "5 GHz"
            else -> null
        }
    }

    private fun frequencyForChannel(channel: Int, band: String?): Int {
        return when {
            channel == 14 -> 2484
            channel <= 14 -> 2407 + channel * 5
            band == "6 GHz" -> 5950 + channel * 5
            channel in 15..233 -> 5000 + channel * 5
            else -> 0
        }
    }

    private fun readClientCount(interfaceName: String): Int? {
        val arpFile = File("/proc/net/arp")
        if (!arpFile.canRead()) {
            return null
        }
        return runCatching {
            arpFile.readLines().drop(1).count { line ->
                val fields = line.trim().split(Regex("\\s+"))
                fields.size >= 6 &&
                    fields[2].toIntOrNull(16)?.and(2) == 2 &&
                    fields[3] != "00:00:00:00:00:00" &&
                    fields[5] == interfaceName
            }
        }
            .getOrNull()
    }
}

private object ChargingThermalStatusReader {
    fun read(context: Context): ChargingThermalStatus {
        val batteryIntent =
            context.registerReceiver(
                null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage =
            if (level >= 0 && scale > 0) {
                "${(level * 100 / scale.coerceAtLeast(1))}%"
            } else {
                readSysfs("/sys/class/power_supply/battery/capacity")?.let { "$it%" }
                    ?: "Unsupported"
            }
        val status =
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let {
                when (it) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                    else -> "Unknown"
                }
            } ?: readSysfs("/sys/class/power_supply/battery/status") ?: "Unsupported"
        val thermal =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                thermalStatus(
                    context.getSystemService(PowerManager::class.java)?.currentThermalStatus
                )
            } else {
                "Unsupported"
            }
        return ChargingThermalStatus("$status $percentage", thermal)
    }

    private fun thermalStatus(status: Int?): String {
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unsupported"
        }
    }

    private fun readSysfs(path: String): String? {
        val file = File(path)
        return if (file.isFile && file.canRead()) {
            runCatching { file.readText().trim().takeIf(String::isNotBlank) }.getOrNull()
        } else {
            null
        }
    }
}
