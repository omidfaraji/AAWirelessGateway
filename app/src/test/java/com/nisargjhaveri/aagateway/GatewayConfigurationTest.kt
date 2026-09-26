package com.nisargjhaveri.aagateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayConfigurationTest {
    @Test
    fun acceptsNativeConfigurationWithoutStaticHotspotCredentials() {
        val configuration = configuration(nativeConnectionFlow = true)

        assertNull(
            configuration.validationError(
                bluetoothPermissionGranted = true,
                nearbyWifiPermissionGranted = true,
            )
        )
    }

    @Test
    fun requiresClientDevice() {
        val configuration = configuration(clientAddress = null)

        assertEquals(
            "Select a paired client Bluetooth device",
            configuration.validationError(
                bluetoothPermissionGranted = true,
                nearbyWifiPermissionGranted = true,
            ),
        )
    }

    @Test
    fun requiresNearbyWifiPermissionForNativeMode() {
        val configuration = configuration(nativeConnectionFlow = true)

        assertEquals(
            "Grant the nearby Wi-Fi devices permission",
            configuration.validationError(
                bluetoothPermissionGranted = true,
                nearbyWifiPermissionGranted = false,
            ),
        )
    }

    @Test
    fun requiresLocationPermissionsForNativeModeWhenRequested() {
        val configuration = configuration(nativeConnectionFlow = true)

        assertEquals(
            "Grant precise location access",
            configuration.validationError(
                bluetoothPermissionGranted = true,
                nearbyWifiPermissionGranted = true,
                locationPermissionGranted = false,
            ),
        )
        assertEquals(
            "Grant background location access",
            configuration.validationError(
                bluetoothPermissionGranted = true,
                nearbyWifiPermissionGranted = true,
                locationPermissionGranted = true,
                backgroundLocationPermissionGranted = false,
            ),
        )
    }

    @Test
    fun validatesLegacyHotspotCredentials() {
        val configuration =
            configuration(
                nativeConnectionFlow = false,
                hotspotSsid = "",
                hotspotPassphrase = "short",
            )

        assertEquals(
            "Enter a hotspot name between 1 and 32 bytes",
            configuration.validationError(
                bluetoothPermissionGranted = true,
                nearbyWifiPermissionGranted = false,
            ),
        )
    }

    @Test
    fun rejectsInvalidHotspotGatewayAddress() {
        val configuration =
            configuration(
                nativeConnectionFlow = true,
                hotspotIpAddress = "not-an-address",
            )

        assertEquals(
            "Enter a valid hotspot gateway IPv4 address",
            configuration.validationError(
                bluetoothPermissionGranted = true,
                nearbyWifiPermissionGranted = true,
            ),
        )
    }

    private fun configuration(
        clientAddress: String? = "80:39:8C:23:85:9F",
        nativeConnectionFlow: Boolean = true,
        hotspotSsid: String = "",
        hotspotPassphrase: String = "",
        hotspotIpAddress: String? = null,
    ): GatewayConfiguration {
        return GatewayConfiguration(
            clientAddress = clientAddress,
            nativeConnectionFlow = nativeConnectionFlow,
            hotspotSsid = hotspotSsid,
            hotspotPassphrase = hotspotPassphrase,
            hotspotBssid = null,
            hotspotIpAddress = hotspotIpAddress,
            clientHandshakeTimeoutSeconds = 15,
            clientConnectionTimeoutSeconds = 60,
        )
    }
}
