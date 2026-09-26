package com.nisargjhaveri.aagateway

import WifiInfoRequestOuterClass
import WifiStartRequestOuterClass
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.util.UUID

class AABluetoothProfileHandler(private val context: Context) {
    companion object {
        private const val LOG_TAG = "AAService"
        private const val RETRY_DELAY_MS = 500L

        private val A2DP_SOURCE_UUID = UUID.fromString("00001112-0000-1000-8000-00805F9B34FB")
        private val AA_LISTENER_UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java).adapter

    private lateinit var wifiHotspotInfo: WifiHotspotInfo
    private var connectThread: AAConnectThread? = null
    private var listenerThread: AAProfileListenerThread? = null

    fun connectDevice(
        mac: String,
        timeout: Long,
        wifiHotspotInfo: WifiHotspotInfo,
        callback: (Boolean) -> Unit,
    ) {
        cleanup()
        this.wifiHotspotInfo = wifiHotspotInfo

        if (!hasBluetoothPermission()) {
            callback(false)
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            callback(false)
            return
        }

        val device =
            try {
                adapter.getRemoteDevice(mac.uppercase())
            } catch (exception: IllegalArgumentException) {
                Log.e(LOG_TAG, "Invalid Bluetooth address", exception)
                callback(false)
                return
            }

        listenerThread = AAProfileListenerThread().also(Thread::start)
        connectThread = AAConnectThread(device, timeout, callback).also(Thread::start)
    }

    fun cleanup() {
        connectThread?.cancel()
        connectThread = null
        listenerThread?.cancel()
        listenerThread = null
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun getWifiStartRequest(): WifiStartRequestOuterClass.WifiStartRequest {
        return WifiStartRequestOuterClass.WifiStartRequest.newBuilder()
            .setIpAddress(wifiHotspotInfo.ipAddress)
            .setPort(5288)
            .build()
    }

    private fun getWifiInfoRequest(): WifiInfoRequestOuterClass.WifiInfoRequest {
        return WifiInfoRequestOuterClass.WifiInfoRequest.newBuilder()
            .setSsid(wifiHotspotInfo.ssid)
            .setKey(wifiHotspotInfo.passphrase)
            .setBssid(wifiHotspotInfo.bssid)
            .setSecurityMode(wifiHotspotInfo.securityMode)
            .setAccessPointType(wifiHotspotInfo.accessPointType)
            .build()
    }

    private inner class AAConnectThread(
        private val device: BluetoothDevice,
        private val timeoutMs: Long,
        private val callback: (Boolean) -> Unit,
    ) : Thread() {
        @Volatile private var running = true
        @Volatile private var socket: BluetoothSocket? = null

        override fun run() {
            if (!hasBluetoothPermission()) {
                callback(false)
                return
            }

            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            var connected = false

            while (running && !connected && SystemClock.elapsedRealtime() < deadline) {
                val attemptSocket =
                    try {
                        device.createRfcommSocketToServiceRecord(A2DP_SOURCE_UUID)
                    } catch (exception: IOException) {
                        Log.e(LOG_TAG, "Could not create Bluetooth socket", exception)
                        break
                    }
                socket = attemptSocket

                val timeoutAction = Runnable {
                    if (running && SystemClock.elapsedRealtime() >= deadline) {
                        runCatching { attemptSocket.close() }
                    }
                }
                mainHandler.postDelayed(
                    timeoutAction,
                    (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0),
                )

                try {
                    attemptSocket.connect()
                    connected = running
                } catch (exception: IOException) {
                    Log.d(LOG_TAG, "Bluetooth connection attempt failed: ${exception.message}")
                } finally {
                    mainHandler.removeCallbacks(timeoutAction)
                    if (!connected) {
                        runCatching { attemptSocket.close() }
                    }
                }

                if (!connected && running) {
                    try {
                        sleep(
                            RETRY_DELAY_MS.coerceAtMost(
                                (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                            )
                        )
                    } catch (_: InterruptedException) {
                        running = false
                    }
                }
            }

            if (running) {
                callback(connected)
            }
        }

        fun cancel() {
            running = false
            interrupt()
            runCatching { socket?.close() }
            socket = null
        }
    }

    private inner class AAProfileListenerThread : Thread() {
        @Volatile private var running = true
        @Volatile private var serverSocket: BluetoothServerSocket? = null
        @Volatile private var socket: BluetoothSocket? = null

        @SuppressLint("MissingPermission")
        override fun run() {
            if (!hasBluetoothPermission()) {
                return
            }

            try {
                Log.d(LOG_TAG, "Creating service record for AA listener")
                serverSocket =
                    bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                        "AA Listener",
                        AA_LISTENER_UUID,
                    )
                socket = serverSocket?.accept()
                if (!running) {
                    return
                }

                socket?.let { connectedSocket ->
                    BluetoothFrameCodec.write(
                        connectedSocket.outputStream,
                        1,
                        getWifiStartRequest().toByteArray(),
                    )
                    Log.d(LOG_TAG, "Sent WifiStartRequest")

                    val wifiInfoRequest = BluetoothFrameCodec.read(connectedSocket.inputStream)
                    if (wifiInfoRequest.type != 2) {
                        throw IOException(
                            "Expected WifiInfoRequest, got type ${wifiInfoRequest.type}"
                        )
                    }

                    BluetoothFrameCodec.write(
                        connectedSocket.outputStream,
                        3,
                        getWifiInfoRequest().toByteArray(),
                    )
                    Log.d(LOG_TAG, "Sent WifiInfoResponse")

                    val startResponse = BluetoothFrameCodec.read(connectedSocket.inputStream)
                    val connectStatus = BluetoothFrameCodec.read(connectedSocket.inputStream)
                    if (startResponse.type != 7 || connectStatus.type != 6) {
                        throw IOException(
                            "Unexpected Android Auto response types: " +
                                "${startResponse.type}, ${connectStatus.type}"
                        )
                    }
                }
            } catch (exception: IOException) {
                if (running) {
                    Log.e(LOG_TAG, "Android Auto Bluetooth handshake failed", exception)
                }
            } catch (exception: RuntimeException) {
                if (running) {
                    Log.e(LOG_TAG, "Invalid Android Auto Bluetooth frame", exception)
                }
            } finally {
                closeSockets()
            }
        }

        fun cancel() {
            running = false
            closeSockets()
            interrupt()
        }

        private fun closeSockets() {
            runCatching { socket?.close() }
            socket = null
            runCatching { serverSocket?.close() }
            serverSocket = null
        }
    }
}
