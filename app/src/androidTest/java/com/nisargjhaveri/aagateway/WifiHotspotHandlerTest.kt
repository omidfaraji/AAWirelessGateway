package com.nisargjhaveri.aagateway

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WifiHotspotHandlerTest {
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.NEARBY_WIFI_DEVICES
            } else {
                Manifest.permission.ACCESS_FINE_LOCATION
            }
        )

    private val handler =
        WifiHotspotHandler(ApplicationProvider.getApplicationContext<Context>())

    @After
    fun tearDown() {
        Handler(Looper.getMainLooper()).post(handler::stop)
    }

    @Test
    fun startsNativeLocalOnlyHotspotWithConnectionDetails() {
        val completed = CountDownLatch(1)
        var success = false
        var hotspotInfo: WifiHotspotInfo? = null

        Handler(Looper.getMainLooper()).post {
            handler.start(
                "",
                "",
                null,
                "10.249.96.99",
                true,
            ) { started, info ->
                success = started
                hotspotInfo = info
                completed.countDown()
            }
        }

        assertTrue("Hotspot callback timed out", completed.await(20, TimeUnit.SECONDS))
        assertTrue("Local-only hotspot failed to start", success)
        assertNotNull(hotspotInfo)
        assertTrue(hotspotInfo!!.ssid.isNotBlank())
        assertTrue(hotspotInfo!!.passphrase.isNotBlank())
        assertTrue(hotspotInfo!!.bssid.isNotBlank())
        assertTrue(hotspotInfo!!.bssid != "02:00:00:00:00:00")
        assertTrue(hotspotInfo!!.ipAddress.isNotBlank())

        val hotspotInterface =
            Collections.list(NetworkInterface.getNetworkInterfaces()).firstOrNull {
                networkInterface ->
                Collections.list(networkInterface.inetAddresses).any {
                    it.hostAddress == hotspotInfo!!.ipAddress
                }
            }
        hotspotInterface?.let {
            val activeBssid = it.hotspotHardwareAddress()
            assertNotNull(activeBssid)
            assertEquals(activeBssid, hotspotInfo!!.bssid)
        }
    }
}
