package com.nisargjhaveri.aagateway

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceManager
import com.nisargjhaveri.aagateway.ui.settings.GatewaySettingsScreen
import com.nisargjhaveri.aagateway.ui.theme.AAGatewayTheme

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothHandler: BluetoothHandler
    private lateinit var wifiClientHandler: WifiClientHandler

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bluetoothHandler = BluetoothHandler(this, this)
        wifiClientHandler = WifiClientHandler(this, this)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        setComponentEnabled(
            USBReceiverActivity::class.java,
            preferences.getBoolean("is_gateway", false),
        )
        setComponentEnabled(
            BluetoothReceiver::class.java,
            preferences.getBoolean("is_wireless_client", false),
        )

        setContent {
            AAGatewayTheme {
                GatewaySettingsScreen(
                    bluetoothHandler = bluetoothHandler,
                    wifiClientHandler = wifiClientHandler,
                    onGatewayRoleChanged = { enabled ->
                        setComponentEnabled(USBReceiverActivity::class.java, enabled)
                    },
                    onWirelessClientRoleChanged = { enabled ->
                        setComponentEnabled(BluetoothReceiver::class.java, enabled)
                    },
                    onOpenBluetoothSettings = {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                    onOpenWriteSettings = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:$packageName"),
                            )
                        )
                    },
                    onOpenOverlaySettings = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            )
                        )
                    },
                )
            }
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setComponentEnabled(componentClass: Class<*>, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, componentClass),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
