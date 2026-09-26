package com.nisargjhaveri.aagateway

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

data class WifiHotspotInfo(
    val ssid: String,
    val passphrase: String,
    val bssid: String,
    val ipAddress: String,
    val securityMode: WifiInfoRequestOuterClass.SecurityMode,
    val accessPointType: WifiInfoRequestOuterClass.AccessPointType,
)

class WifiHotspotHandler(context: Context) {
    companion object {
        private const val LOG_TAG = "AAService"
        private const val HOTSPOT_START_TIMEOUT_MS = 15_000L
        private const val INTERFACE_POLL_INTERVAL_MS = 250L
        private const val TETHERING_WIFI = 0
    }

    private val context = context.applicationContext
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }
    private val wifiManager: WifiManager by lazy {
        context.getSystemService(WifiManager::class.java)
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var localOnlyHotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var legacyHotspotStarted = false
    private var startCompleted = AtomicBoolean()

    fun start(
        configuredSsid: String,
        configuredPassphrase: String,
        configuredBssid: String?,
        useNativeConnectionFlow: Boolean,
        callback: (success: Boolean, wifiHotspotInfo: WifiHotspotInfo?) -> Unit,
    ) {
        stop()
        startCompleted = AtomicBoolean(false)

        if (useNativeConnectionFlow) {
            startLocalOnlyHotspot(callback)
        } else {
            startConfiguredTethering(
                configuredSsid,
                configuredPassphrase,
                configuredBssid,
                callback,
            )
        }
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        localOnlyHotspotReservation?.close()
        localOnlyHotspotReservation = null

        if (legacyHotspotStarted) {
            stopConfiguredTethering()
            legacyHotspotStarted = false
        }
    }

    private fun startLocalOnlyHotspot(
        callback: (success: Boolean, wifiHotspotInfo: WifiHotspotInfo?) -> Unit
    ) {
        try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        localOnlyHotspotReservation = reservation
                        waitForHotspotInterface(
                            credentialsFrom(reservation),
                            System.currentTimeMillis() + HOTSPOT_START_TIMEOUT_MS,
                            callback,
                        )
                    }

                    override fun onStopped() {
                        localOnlyHotspotReservation = null
                    }

                    override fun onFailed(reason: Int) {
                        Log.e(LOG_TAG, "Local-only hotspot failed with reason $reason")
                        completeStart(callback, null)
                    }
                },
                mainHandler,
            )
        } catch (exception: SecurityException) {
            Log.e(LOG_TAG, "Missing permission to start local-only hotspot", exception)
            completeStart(callback, null)
        } catch (exception: RuntimeException) {
            Log.e(LOG_TAG, "Could not start local-only hotspot", exception)
            completeStart(callback, null)
        }
    }

    private data class HotspotCredentials(
        val ssid: String,
        val passphrase: String,
        val bssid: String?,
    )

    @Suppress("DEPRECATION")
    private fun credentialsFrom(
        reservation: WifiManager.LocalOnlyHotspotReservation
    ): HotspotCredentials {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val configuration = reservation.softApConfiguration
            HotspotCredentials(
                configuration.ssid.orEmpty(),
                configuration.passphrase.orEmpty(),
                configuration.bssid?.toString(),
            )
        } else {
            val configuration: WifiConfiguration =
                reservation.wifiConfiguration
                    ?: return HotspotCredentials("", "", null)
            HotspotCredentials(
                configuration.SSID.orEmpty(),
                configuration.preSharedKey.orEmpty(),
                configuration.BSSID,
            )
        }
    }

    private fun waitForHotspotInterface(
        credentials: HotspotCredentials,
        deadlineMs: Long,
        callback: (success: Boolean, wifiHotspotInfo: WifiHotspotInfo?) -> Unit,
    ) {
        val endpoint = findHotspotEndpoint()
        val bssid = credentials.bssid ?: endpoint?.hardwareAddress

        if (
            credentials.ssid.isNotBlank() &&
                credentials.passphrase.isNotBlank() &&
                bssid != null &&
                endpoint != null
        ) {
            completeStart(
                callback,
                WifiHotspotInfo(
                    ssid = credentials.ssid,
                    passphrase = credentials.passphrase,
                    bssid = bssid,
                    ipAddress = endpoint.ipAddress,
                    securityMode = WifiInfoRequestOuterClass.SecurityMode.WPA2_PERSONAL,
                    accessPointType = WifiInfoRequestOuterClass.AccessPointType.DYNAMIC,
                ),
            )
            return
        }

        if (System.currentTimeMillis() >= deadlineMs) {
            Log.e(LOG_TAG, "Timed out waiting for local-only hotspot interface")
            completeStart(callback, null)
            return
        }

        mainHandler.postDelayed(
            { waitForHotspotInterface(credentials, deadlineMs, callback) },
            INTERFACE_POLL_INTERVAL_MS,
        )
    }

    private data class HotspotEndpoint(val ipAddress: String, val hardwareAddress: String?)

    private fun findHotspotEndpoint(): HotspotEndpoint? {
        val clientInterfaces =
            connectivityManager.allNetworks
                .filter {
                    connectivityManager
                        .getNetworkCapabilities(it)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
                .mapNotNull { connectivityManager.getLinkProperties(it)?.interfaceName }
                .toSet()

        return Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback && it.name !in clientInterfaces }
            .sortedByDescending {
                it.name.startsWith("ap") ||
                    it.name.startsWith("swlan") ||
                    it.name.startsWith("wlan")
            }
            .mapNotNull { networkInterface ->
                val address =
                    Collections.list(networkInterface.inetAddresses).firstOrNull {
                        it is Inet4Address && it.isSiteLocalAddress
                    } ?: return@mapNotNull null
                val hardwareAddress =
                    networkInterface.hardwareAddress?.joinToString(":") {
                        "%02x".format(it.toInt() and 0xff)
                    }
                HotspotEndpoint(address.hostAddress.orEmpty(), hardwareAddress)
            }
            .firstOrNull()
    }

    private fun completeStart(
        callback: (success: Boolean, wifiHotspotInfo: WifiHotspotInfo?) -> Unit,
        info: WifiHotspotInfo?,
    ) {
        if (startCompleted.compareAndSet(false, true)) {
            callback(info != null, info)
        }
    }

    private fun startConfiguredTethering(
        ssid: String,
        passphrase: String,
        bssid: String?,
        callback: (success: Boolean, wifiHotspotInfo: WifiHotspotInfo?) -> Unit,
    ) {
        if (ssid.isBlank() || passphrase.isBlank()) {
            completeStart(callback, null)
            return
        }

        findHotspotEndpoint()?.let {
            legacyHotspotStarted = true
            completeStart(callback, configuredHotspotInfo(ssid, passphrase, bssid, it))
            return
        }

        val callbackClass = getOnStartTetheringCallbackClass()
        if (callbackClass == null) {
            completeStart(callback, null)
            return
        }

        try {
            val proxy =
                com.android.dx.stock.ProxyBuilder.forClass(callbackClass)
                    .dexCache(context.codeCacheDir)
                    .handler { proxy, method, args ->
                        when (method.name) {
                            "onTetheringStarted" -> {
                                legacyHotspotStarted = true
                                waitForConfiguredHotspot(
                                    ssid,
                                    passphrase,
                                    bssid,
                                    System.currentTimeMillis() + HOTSPOT_START_TIMEOUT_MS,
                                    callback,
                                )
                            }
                            "onTetheringFailed" -> completeStart(callback, null)
                            else -> com.android.dx.stock.ProxyBuilder.callSuper(proxy, method, args)
                        }
                        null
                    }
                    .build()

            val method =
                connectivityManager.javaClass.getDeclaredMethod(
                    "startTethering",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    callbackClass,
                    Handler::class.java,
                )
            method.invoke(connectivityManager, TETHERING_WIFI, false, proxy, mainHandler)
        } catch (exception: ReflectiveOperationException) {
            Log.e(LOG_TAG, "Configured tethering is unavailable", exception)
            completeStart(callback, null)
        } catch (exception: RuntimeException) {
            Log.e(LOG_TAG, "Could not start configured tethering", exception)
            completeStart(callback, null)
        }
    }

    private fun waitForConfiguredHotspot(
        ssid: String,
        passphrase: String,
        bssid: String?,
        deadlineMs: Long,
        callback: (success: Boolean, wifiHotspotInfo: WifiHotspotInfo?) -> Unit,
    ) {
        findHotspotEndpoint()?.let {
            completeStart(callback, configuredHotspotInfo(ssid, passphrase, bssid, it))
            return
        }

        if (System.currentTimeMillis() >= deadlineMs) {
            Log.e(LOG_TAG, "Timed out waiting for configured hotspot interface")
            completeStart(callback, null)
            return
        }

        mainHandler.postDelayed(
            { waitForConfiguredHotspot(ssid, passphrase, bssid, deadlineMs, callback) },
            INTERFACE_POLL_INTERVAL_MS,
        )
    }

    private fun configuredHotspotInfo(
        ssid: String,
        passphrase: String,
        bssid: String?,
        endpoint: HotspotEndpoint,
    ): WifiHotspotInfo? {
        val resolvedBssid = endpoint.hardwareAddress ?: bssid ?: return null
        return WifiHotspotInfo(
            ssid = ssid,
            passphrase = passphrase,
            bssid = resolvedBssid,
            ipAddress = endpoint.ipAddress,
            securityMode = WifiInfoRequestOuterClass.SecurityMode.WPA2_PERSONAL,
            accessPointType = WifiInfoRequestOuterClass.AccessPointType.DYNAMIC,
        )
    }

    @SuppressLint("PrivateApi")
    private fun getOnStartTetheringCallbackClass(): Class<*>? {
        return try {
            Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback")
        } catch (exception: ClassNotFoundException) {
            Log.e(LOG_TAG, "Configured tethering callback is unavailable", exception)
            null
        }
    }

    private fun stopConfiguredTethering() {
        try {
            val method: Method =
                connectivityManager.javaClass.getDeclaredMethod(
                    "stopTethering",
                    Int::class.javaPrimitiveType,
                )
            method.invoke(connectivityManager, TETHERING_WIFI)
        } catch (exception: ReflectiveOperationException) {
            Log.e(LOG_TAG, "Could not stop configured tethering", exception)
        } catch (exception: RuntimeException) {
            Log.e(LOG_TAG, "Could not stop configured tethering", exception)
        }
    }
}
