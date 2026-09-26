package com.nisargjhaveri.aagateway

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WifiHotspotHandlerTest {
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.NEARBY_WIFI_DEVICES)

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
            handler.start("", "", null, "10.249.96.99", true) { started, info ->
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
        assertTrue(hotspotInfo!!.ipAddress == "10.249.96.99")
    }
}
