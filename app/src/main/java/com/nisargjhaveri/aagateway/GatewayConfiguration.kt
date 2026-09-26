package com.nisargjhaveri.aagateway

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build

data class GatewayConfiguration(
    val clientAddress: String?,
    val nativeConnectionFlow: Boolean,
    val hotspotSsid: String,
    val hotspotPassphrase: String,
    val hotspotBssid: String?,
    val hotspotIpAddress: String?,
    val clientHandshakeTimeoutSeconds: Int,
    val clientConnectionTimeoutSeconds: Int,
) {
    companion object {
        private val MAC_ADDRESS_PATTERN =
            Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

        fun from(preferences: SharedPreferences): GatewayConfiguration {
            return GatewayConfiguration(
                clientAddress = preferences.getString("client_bt_mac", null),
                nativeConnectionFlow =
                    preferences.getBoolean("native_connection_flow", true),
                hotspotSsid = preferences.getString("hotspot_ssid", "").orEmpty(),
                hotspotPassphrase =
                    preferences.getString("hotspot_password", "").orEmpty(),
                hotspotBssid =
                    preferences.getString("hotspot_bssid", null)?.ifBlank { null },
                hotspotIpAddress =
                    preferences.getString("hotspot_ip_address", null)?.ifBlank { null },
                clientHandshakeTimeoutSeconds =
                    preferences.getInt("client_handshake_timeout", 15),
                clientConnectionTimeoutSeconds =
                    preferences.getInt("client_connection_timeout", 60),
            )
        }
    }

    fun validationError(
        bluetoothPermissionGranted: Boolean,
        nearbyWifiPermissionGranted: Boolean,
    ): String? {
        if (clientAddress == null || !MAC_ADDRESS_PATTERN.matches(clientAddress)) {
            return "Select a paired client Bluetooth device"
        }
        if (!bluetoothPermissionGranted) {
            return "Grant the nearby devices Bluetooth permission"
        }
        if (nativeConnectionFlow && !nearbyWifiPermissionGranted) {
            return "Grant the nearby Wi-Fi devices permission"
        }
        if (!nativeConnectionFlow) {
            if (hotspotSsid.toByteArray(Charsets.UTF_8).size !in 1..32) {
                return "Enter a hotspot name between 1 and 32 bytes"
            }
            if (hotspotPassphrase.length !in 8..63) {
                return "Enter a hotspot password between 8 and 63 characters"
            }
            if (hotspotBssid != null && !MAC_ADDRESS_PATTERN.matches(hotspotBssid)) {
                return "Enter a valid hotspot BSSID"
            }
        }
        if (hotspotIpAddress != null && !isIpv4Address(hotspotIpAddress)) {
            return "Enter a valid hotspot gateway IPv4 address"
        }
        if (clientHandshakeTimeoutSeconds !in 5..60) {
            return "Set the client handshake timeout between 5 and 60 seconds"
        }
        if (clientConnectionTimeoutSeconds !in 15..180) {
            return "Set the client connection timeout between 15 and 180 seconds"
        }
        return null
    }

    private fun isIpv4Address(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 &&
            octets.all { octet ->
                octet.isNotEmpty() &&
                    octet.all(Char::isDigit) &&
                    octet.toIntOrNull() in 0..255
            }
    }
}

fun GatewayConfiguration.validationError(context: Context): String? {
    val bluetoothPermissionGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    val nearbyWifiPermissionGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
    return validationError(bluetoothPermissionGranted, nearbyWifiPermissionGranted)
}
