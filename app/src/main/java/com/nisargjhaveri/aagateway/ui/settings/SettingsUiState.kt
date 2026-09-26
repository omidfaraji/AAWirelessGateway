package com.nisargjhaveri.aagateway.ui.settings

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import com.nisargjhaveri.aagateway.BluetoothHandler
import com.nisargjhaveri.aagateway.GatewayConfiguration
import com.nisargjhaveri.aagateway.WifiClientHandler
import com.nisargjhaveri.aagateway.validationError

data class SettingsUiState(
    val isGateway: Boolean,
    val isWirelessClient: Boolean,
    val clientAddress: String?,
    val nativeConnectionFlow: Boolean,
    val hotspotIpAddress: String,
    val hotspotSsid: String,
    val hotspotPassword: String,
    val hotspotBssid: String,
    val usbFallback: Boolean,
    val handshakeTimeoutSeconds: Int,
    val connectionTimeoutSeconds: Int,
    val gatewayWifiSsid: String,
    val gatewayWifiPassword: String,
    val gatewayWifiBssid: String,
    val gatewayWifiAllowsInternet: Boolean,
    val gatewayBluetoothAddress: String?,
    val minimumBatteryLevel: Int,
    val connectInPowerSaveMode: Boolean,
    val bluetoothPermissionGranted: Boolean,
    val nearbyWifiPermissionGranted: Boolean,
    val locationPermissionGranted: Boolean,
    val backgroundLocationPermissionGranted: Boolean,
    val writeSettingsGranted: Boolean,
    val overlayPermissionGranted: Boolean,
    val manageUsbPermissionGranted: Boolean,
    val bluetoothEnabled: Boolean,
    val pairedDevices: List<BluetoothHandler.BluetoothDeviceInfo>,
    val setupProblem: String?,
) {
    companion object {
        private val macAddressPattern = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

        fun read(
            context: Context,
            preferences: SharedPreferences,
            bluetoothHandler: BluetoothHandler,
            wifiClientHandler: WifiClientHandler,
        ): SettingsUiState {
            val isGateway = preferences.getBoolean("is_gateway", false)
            val isWirelessClient = preferences.getBoolean("is_wireless_client", false)
            val bluetoothPermissionGranted = bluetoothHandler.hasConnectPermissions()
            val nearbyWifiPermissionGranted = wifiClientHandler.hasNearbyWifiPermission()
            val locationPermissionGranted = wifiClientHandler.hasLocationPermissions()
            val backgroundLocationPermissionGranted =
                wifiClientHandler.hasBackgroundLocationPermission()
            val bluetoothEnabled =
                bluetoothPermissionGranted && bluetoothHandler.isEnabled()

            val setupProblem =
                when {
                    isGateway && isWirelessClient -> "Choose only one role for this phone"
                    !isGateway && !isWirelessClient ->
                        "Choose USB gateway or wireless client"
                    !bluetoothPermissionGranted ->
                        "Grant nearby Bluetooth devices access"
                    !bluetoothEnabled -> "Turn on Bluetooth"
                    isGateway ->
                        GatewayConfiguration.from(preferences).validationError(context)
                    else ->
                        wirelessClientProblem(
                            preferences,
                            bluetoothPermissionGranted,
                            nearbyWifiPermissionGranted,
                            locationPermissionGranted,
                            backgroundLocationPermissionGranted,
                        )
                }

            return SettingsUiState(
                isGateway = isGateway,
                isWirelessClient = isWirelessClient,
                clientAddress = preferences.getString("client_bt_mac", null),
                nativeConnectionFlow =
                    preferences.getBoolean("native_connection_flow", true),
                hotspotIpAddress =
                    preferences.getString("hotspot_ip_address", "").orEmpty(),
                hotspotSsid = preferences.getString("hotspot_ssid", "").orEmpty(),
                hotspotPassword =
                    preferences.getString("hotspot_password", "").orEmpty(),
                hotspotBssid =
                    preferences.getString("hotspot_bssid", "").orEmpty(),
                usbFallback = preferences.getBoolean("usb_fallback", false),
                handshakeTimeoutSeconds =
                    preferences.getInt("client_handshake_timeout", 15),
                connectionTimeoutSeconds =
                    preferences.getInt("client_connection_timeout", 60),
                gatewayWifiSsid =
                    preferences.getString("gateway_wifi_ssid", "").orEmpty(),
                gatewayWifiPassword =
                    preferences.getString("gateway_wifi_password", "").orEmpty(),
                gatewayWifiBssid =
                    preferences.getString("gateway_wifi_bssid", "").orEmpty(),
                gatewayWifiAllowsInternet =
                    preferences.getBoolean("gateway_wifi_allow_internet", false),
                gatewayBluetoothAddress =
                    preferences.getString("gateway_bt_mac", null),
                minimumBatteryLevel =
                    preferences.getInt("connection_battery_limit", 0),
                connectInPowerSaveMode =
                    preferences.getBoolean("connect_in_power_save_mode", false),
                bluetoothPermissionGranted = bluetoothPermissionGranted,
                nearbyWifiPermissionGranted = nearbyWifiPermissionGranted,
                locationPermissionGranted = locationPermissionGranted,
                backgroundLocationPermissionGranted =
                    backgroundLocationPermissionGranted,
                writeSettingsGranted = Settings.System.canWrite(context),
                overlayPermissionGranted = Settings.canDrawOverlays(context),
                manageUsbPermissionGranted =
                    context.checkSelfPermission("android.permission.MANAGE_USB") ==
                        PackageManager.PERMISSION_GRANTED,
                bluetoothEnabled = bluetoothEnabled,
                pairedDevices =
                    if (bluetoothPermissionGranted) {
                        bluetoothHandler.getBondedDevices().sortedBy { it.name }
                    } else {
                        emptyList()
                    },
                setupProblem = setupProblem,
            )
        }

        private fun wirelessClientProblem(
            preferences: SharedPreferences,
            bluetoothPermissionGranted: Boolean,
            nearbyWifiPermissionGranted: Boolean,
            locationPermissionGranted: Boolean,
            backgroundLocationPermissionGranted: Boolean,
        ): String? {
            if (!bluetoothPermissionGranted) {
                return "Grant nearby Bluetooth devices access"
            }
            if (!nearbyWifiPermissionGranted) {
                return "Grant nearby Wi-Fi devices access"
            }
            if (!locationPermissionGranted) {
                return "Grant precise location access"
            }
            if (!backgroundLocationPermissionGranted) {
                return "Grant background location access"
            }
            if (preferences.getString("gateway_wifi_ssid", "").isNullOrBlank()) {
                return "Enter the gateway hotspot name"
            }
            if (preferences.getString("gateway_wifi_password", "").orEmpty().length !in 8..63) {
                return "Enter a gateway hotspot password between 8 and 63 characters"
            }
            val allowsInternet =
                preferences.getBoolean("gateway_wifi_allow_internet", false)
            val bssid = preferences.getString("gateway_wifi_bssid", "").orEmpty()
            if (!allowsInternet && !macAddressPattern.matches(bssid)) {
                return "Enter the gateway hotspot Wi-Fi address"
            }
            val gatewayAddress = preferences.getString("gateway_bt_mac", "").orEmpty()
            if (!macAddressPattern.matches(gatewayAddress)) {
                return "Select the paired gateway phone"
            }
            return null
        }
    }
}
