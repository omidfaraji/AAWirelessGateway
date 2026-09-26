package com.nisargjhaveri.aagateway.ui.settings

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import com.nisargjhaveri.aagateway.BluetoothHandler
import com.nisargjhaveri.aagateway.WifiClientHandler
import com.nisargjhaveri.aagateway.ui.components.ActionSettingRow
import com.nisargjhaveri.aagateway.ui.components.DevicePickerDialog
import com.nisargjhaveri.aagateway.ui.components.InformationDialog
import com.nisargjhaveri.aagateway.ui.components.SettingsSection
import com.nisargjhaveri.aagateway.ui.components.SliderSettingRow
import com.nisargjhaveri.aagateway.ui.components.StatusCard
import com.nisargjhaveri.aagateway.ui.components.TextSettingDialog
import com.nisargjhaveri.aagateway.ui.components.ToggleSettingRow
import com.nisargjhaveri.aagateway.ui.components.ValueSettingRow

private data class EditableSetting(
    val key: String,
    val title: String,
    val value: String,
    val password: Boolean = false,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val trimValue: Boolean = false,
)

private data class DeviceSetting(
    val key: String,
    val title: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewaySettingsScreen(
    bluetoothHandler: BluetoothHandler,
    wifiClientHandler: WifiClientHandler,
    onGatewayRoleChanged: (Boolean) -> Unit,
    onWirelessClientRoleChanged: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var preferenceRevision by remember { mutableIntStateOf(0) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var editableSetting by remember { mutableStateOf<EditableSetting?>(null) }
    var deviceSetting by remember { mutableStateOf<DeviceSetting?>(null) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showUsbInformation by remember { mutableStateOf(false) }

    DisposableEffect(preferences) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                preferenceRevision += 1
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionRevision += 1
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state =
        remember(preferenceRevision, permissionRevision) {
            SettingsUiState.read(
                context,
                preferences,
                bluetoothHandler,
                wifiClientHandler,
            )
        }

    fun saveBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    fun saveString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun saveInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AA Wireless Gateway") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    ready = state.setupProblem == null,
                    title = if (state.setupProblem == null) "Ready to connect" else "Setup needed",
                    message =
                        state.setupProblem
                            ?: if (state.isGateway) {
                                "Connect this phone to the car's Android Auto USB port."
                            } else {
                                "The client starts when the gateway phone connects."
                            },
                )
            }

            item {
                SettingsSection(title = "This phone") {
                    ToggleSettingRow(
                        title = "USB gateway - spare phone",
                        summary =
                            "Connect this phone to the car by USB and bridge Android Auto wirelessly.",
                        checked = state.isGateway,
                        onCheckedChange = {
                            saveBoolean("is_gateway", it)
                            if (it && state.isWirelessClient) {
                                saveBoolean("is_wireless_client", false)
                                onWirelessClientRoleChanged(false)
                            }
                            onGatewayRoleChanged(it)
                        },
                    )
                    ToggleSettingRow(
                        title = "Wireless client - main phone",
                        summary =
                            "Use the older two-app method on the phone running Android Auto.",
                        checked = state.isWirelessClient,
                        onCheckedChange = {
                            saveBoolean("is_wireless_client", it)
                            if (it && state.isGateway) {
                                saveBoolean("is_gateway", false)
                                onGatewayRoleChanged(false)
                            }
                            onWirelessClientRoleChanged(it)
                        },
                    )
                }
            }

            if (state.isGateway) {
                item {
                    SettingsSection(title = "Gateway setup") {
                        ValueSettingRow(
                            title = "Android Auto phone",
                            value = state.deviceName(state.clientAddress),
                            onClick = {
                                deviceSetting =
                                    DeviceSetting(
                                        key = "client_bt_mac",
                                        title = "Select the main phone",
                                    )
                            },
                        )
                        ToggleSettingRow(
                            title = "Native wireless Android Auto",
                            summary = "Recommended - the main phone does not need this app.",
                            checked = state.nativeConnectionFlow,
                            onCheckedChange = {
                                saveBoolean("native_connection_flow", it)
                            },
                        )
                        ValueSettingRow(
                            title = "Gateway IP fallback",
                            value =
                                state.hotspotIpAddress.ifBlank {
                                    "Automatic detection"
                                },
                            onClick = {
                                editableSetting =
                                    EditableSetting(
                                        key = "hotspot_ip_address",
                                        title = "Gateway IP fallback",
                                        value = state.hotspotIpAddress,
                                        keyboardType = KeyboardType.Decimal,
                                        trimValue = true,
                                    )
                            },
                        )
                        ValueSettingRow(
                            title = "Advanced options",
                            value =
                                if (showAdvanced) {
                                    "Tap to hide"
                                } else {
                                    "Timeouts and root-only fallback"
                                },
                            onClick = { showAdvanced = !showAdvanced },
                        )
                    }
                }

                if (!state.nativeConnectionFlow) {
                    item {
                        SettingsSection(title = "Manual hotspot") {
                            ValueSettingRow(
                                title = "Hotspot name",
                                value = state.hotspotSsid,
                                onClick = {
                                    editableSetting =
                                        EditableSetting(
                                            "hotspot_ssid",
                                            "Hotspot name",
                                            state.hotspotSsid,
                                        )
                                },
                            )
                            ValueSettingRow(
                                title = "Hotspot password",
                                value = state.hotspotPassword.masked(),
                                onClick = {
                                    editableSetting =
                                        EditableSetting(
                                            "hotspot_password",
                                            "Hotspot password",
                                            state.hotspotPassword,
                                            password = true,
                                        )
                                },
                            )
                            ValueSettingRow(
                                title = "Hotspot Wi-Fi address",
                                value = state.hotspotBssid,
                                onClick = {
                                    editableSetting =
                                        EditableSetting(
                                            "hotspot_bssid",
                                            "Hotspot Wi-Fi address",
                                            state.hotspotBssid,
                                            trimValue = true,
                                        )
                                },
                            )
                        }
                    }
                }

                if (showAdvanced) {
                    item {
                        SettingsSection(title = "Advanced gateway options") {
                            ToggleSettingRow(
                                title = "Use wired Android Auto if wireless fails",
                                summary =
                                    if (state.manageUsbPermissionGranted) {
                                        "Start Android Auto on this phone after a wireless failure."
                                    } else {
                                        "Unavailable - requires privileged USB access and root."
                                    },
                                checked = state.usbFallback,
                                enabled = state.manageUsbPermissionGranted,
                                onCheckedChange = { saveBoolean("usb_fallback", it) },
                            )
                            SliderSettingRow(
                                title = "Bluetooth handshake timeout",
                                summary = "How long to wait for the main phone to answer.",
                                value = state.handshakeTimeoutSeconds,
                                valueRange = 5..60,
                                unit = "s",
                                onValueChange = {
                                    saveInt("client_handshake_timeout", it)
                                },
                            )
                            SliderSettingRow(
                                title = "Wi-Fi connection timeout",
                                summary = "How long to wait for the Android Auto connection.",
                                value = state.connectionTimeoutSeconds,
                                valueRange = 15..180,
                                unit = "s",
                                onValueChange = {
                                    saveInt("client_connection_timeout", it)
                                },
                            )
                        }
                    }
                }
            }

            if (state.isWirelessClient) {
                item {
                    SettingsSection(title = "Wireless client setup") {
                        ValueSettingRow(
                            title = "Gateway hotspot name",
                            value = state.gatewayWifiSsid,
                            onClick = {
                                editableSetting =
                                    EditableSetting(
                                        "gateway_wifi_ssid",
                                        "Gateway hotspot name",
                                        state.gatewayWifiSsid,
                                    )
                            },
                        )
                        ValueSettingRow(
                            title = "Gateway hotspot password",
                            value = state.gatewayWifiPassword.masked(),
                            onClick = {
                                editableSetting =
                                    EditableSetting(
                                        "gateway_wifi_password",
                                        "Gateway hotspot password",
                                        state.gatewayWifiPassword,
                                        password = true,
                                    )
                            },
                        )
                        ToggleSettingRow(
                            title = "Gateway hotspot MAC may change",
                            summary =
                                "Connect by hotspot name instead of requiring a fixed Wi-Fi address.",
                            checked = state.gatewayWifiAllowsInternet,
                            onCheckedChange = {
                                saveBoolean("gateway_wifi_allow_internet", it)
                            },
                        )
                        if (!state.gatewayWifiAllowsInternet) {
                            ValueSettingRow(
                                title = "Gateway hotspot Wi-Fi address",
                                value = state.gatewayWifiBssid,
                                onClick = {
                                    editableSetting =
                                        EditableSetting(
                                            "gateway_wifi_bssid",
                                            "Gateway hotspot Wi-Fi address",
                                            state.gatewayWifiBssid,
                                            trimValue = true,
                                        )
                                },
                            )
                        }
                        ValueSettingRow(
                            title = "Gateway phone",
                            value = state.deviceName(state.gatewayBluetoothAddress),
                            onClick = {
                                deviceSetting =
                                    DeviceSetting(
                                        key = "gateway_bt_mac",
                                        title = "Select the spare phone",
                                    )
                            },
                        )
                        SliderSettingRow(
                            title = "Minimum battery level",
                            summary = "Set to 0 to disable this limit.",
                            value = state.minimumBatteryLevel,
                            valueRange = 0..100,
                            unit = "%",
                            onValueChange = {
                                saveInt("connection_battery_limit", it)
                            },
                        )
                        ToggleSettingRow(
                            title = "Allow connection during battery saver",
                            summary = "Start Android Auto while this phone is conserving power.",
                            checked = state.connectInPowerSaveMode,
                            onCheckedChange = {
                                saveBoolean("connect_in_power_save_mode", it)
                            },
                        )
                    }
                }
            }

            if (state.isGateway || state.isWirelessClient) {
                item {
                    SettingsSection(title = "Permissions and pairing") {
                        if (!state.bluetoothEnabled) {
                            ActionSettingRow(
                                title = "Turn on Bluetooth",
                                summary = "Bluetooth is required to find and connect the phones.",
                                onClick = {
                                    bluetoothHandler.setEnabled {
                                        permissionRevision += 1
                                    }
                                },
                            )
                        }
                        ActionSettingRow(
                            title = "Pair a Bluetooth device",
                            summary = "Open Android settings to pair the other phone.",
                            onClick = onOpenBluetoothSettings,
                        )
                        ActionSettingRow(
                            title = "Nearby Bluetooth devices",
                            summary = "Required to communicate with the other phone.",
                            completed = state.bluetoothPermissionGranted,
                            enabled = !state.bluetoothPermissionGranted,
                            onClick = {
                                bluetoothHandler.requestConnectPermissions {
                                    permissionRevision += 1
                                }
                            },
                        )
                        ActionSettingRow(
                            title = "Nearby Wi-Fi devices",
                            summary = "Required to create or connect to the hotspot.",
                            completed = state.nearbyWifiPermissionGranted,
                            enabled = !state.nearbyWifiPermissionGranted,
                            onClick = {
                                wifiClientHandler.requestNearbyWifiPermission {
                                    permissionRevision += 1
                                }
                            },
                        )
                        if (state.isWirelessClient) {
                            ActionSettingRow(
                                title = "Location access",
                                summary =
                                    when {
                                        !state.locationPermissionGranted ->
                                            "Required to identify the connected hotspot."
                                        !state.backgroundLocationPermissionGranted ->
                                            "Select Allow all the time for background connections."
                                        else -> "Required to identify the connected hotspot."
                                    },
                                completed =
                                    state.locationPermissionGranted &&
                                        state.backgroundLocationPermissionGranted,
                                enabled =
                                    !state.locationPermissionGranted ||
                                        !state.backgroundLocationPermissionGranted,
                                onClick = {
                                    if (!state.locationPermissionGranted) {
                                        wifiClientHandler.requestLocationPermissions {
                                            permissionRevision += 1
                                        }
                                    } else {
                                        wifiClientHandler.requestBackgroundLocationPermissions {
                                            permissionRevision += 1
                                        }
                                    }
                                },
                            )
                            ActionSettingRow(
                                title = "Modify system settings",
                                summary = "Used to manage automatic Wi-Fi connections.",
                                completed = state.writeSettingsGranted,
                                enabled = !state.writeSettingsGranted,
                                onClick = onOpenWriteSettings,
                            )
                            ActionSettingRow(
                                title = "Display over other apps",
                                summary = "Allows Android Auto to start from the background.",
                                completed = state.overlayPermissionGranted,
                                enabled = !state.overlayPermissionGranted,
                                onClick = onOpenOverlaySettings,
                            )
                        }
                        if (state.isGateway) {
                            ActionSettingRow(
                                title = "Privileged USB access",
                                summary =
                                    if (state.manageUsbPermissionGranted) {
                                        "Available for wired Android Auto fallback."
                                    } else {
                                        "Optional - requires installing as a system app with root."
                                    },
                                completed = state.manageUsbPermissionGranted,
                                enabled = !state.manageUsbPermissionGranted,
                                onClick = { showUsbInformation = true },
                            )
                        }
                    }
                }
            }
        }
    }

    editableSetting?.let { setting ->
        TextSettingDialog(
            title = setting.title,
            initialValue = setting.value,
            password = setting.password,
            keyboardType = setting.keyboardType,
            onDismiss = { editableSetting = null },
            onSave = {
                saveString(setting.key, if (setting.trimValue) it.trim() else it)
                editableSetting = null
            },
        )
    }

    deviceSetting?.let { setting ->
        DevicePickerDialog(
            title = setting.title,
            devices = state.pairedDevices,
            onDismiss = { deviceSetting = null },
            onSelect = {
                saveString(setting.key, it.address)
                deviceSetting = null
            },
        )
    }

    if (showUsbInformation) {
        InformationDialog(
            title = "Privileged USB access",
            message =
                "This optional permission is only used for wired Android Auto fallback. " +
                    "Android grants it only to privileged system apps, which normally requires " +
                    "a rooted gateway phone.",
            onDismiss = { showUsbInformation = false },
        )
    }
}

private fun SettingsUiState.deviceName(address: String?): String {
    if (address.isNullOrBlank()) {
        return "Not selected"
    }
    val device = pairedDevices.firstOrNull { it.address.equals(address, ignoreCase = true) }
    return if (device?.name.isNullOrBlank()) address else "${device?.name} - $address"
}

private fun String.masked(): String {
    return if (isBlank()) "Not set" else "*".repeat(length)
}
