package com.nisargjhaveri.aagateway

import android.Manifest
import android.content.ActivityNotFoundException
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import java.io.IOException
import java.util.*

class BluetoothHandler (context: Context, activityResultCaller: ActivityResultCaller?) {
    data class BluetoothDeviceInfo(val address: String, val name: String?)

    private val mContext = context
    private val mActivityResultCaller = activityResultCaller

    private var mBluetoothAdapter: BluetoothAdapter? = null

    private var mEnableBluetoothCallback: ((Boolean) -> Unit)? = null
    private var mEnableBluetoothLauncher = mActivityResultCaller?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val callback = mEnableBluetoothCallback
        mEnableBluetoothCallback = null

        if (it.resultCode == Activity.RESULT_OK) {
            callback?.invoke(true)
        }
        else {
            callback?.invoke(false)
        }
    }

    private var mRequestPermissionsCallback: ((Boolean) -> Unit)? = null
    private val mRequestPermissionsLauncher = mActivityResultCaller?.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        val callback = mRequestPermissionsCallback
        mRequestPermissionsCallback = null

        if (isGranted) {
            callback?.invoke(true)
        }
        else {
            callback?.invoke(false)
        }
    }


    init {
        val bluetoothManager: BluetoothManager = mContext.getSystemService(BluetoothManager::class.java)
        mBluetoothAdapter = bluetoothManager.adapter
    }

    fun isSupported(): Boolean {
        return mBluetoothAdapter != null
    }

    fun isEnabled(): Boolean {
        return runCatching { mBluetoothAdapter?.isEnabled ?: false }.getOrDefault(false)
    }

    fun setEnabled(callback: ((success: Boolean) -> Unit)?) {
        if (!hasConnectPermissions()) {
            callback?.invoke(false)
            return
        }

        val launcher = mEnableBluetoothLauncher
        if (launcher == null) {
            callback?.invoke(false)
            return
        }

        mEnableBluetoothCallback = callback
        try {
            launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: ActivityNotFoundException) {
            mEnableBluetoothCallback = null
            callback?.invoke(false)
        } catch (_: SecurityException) {
            mEnableBluetoothCallback = null
            callback?.invoke(false)
        }
    }

    fun hasConnectPermissions(): Boolean {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
                || mContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun requestConnectPermissions(callback: ((success: Boolean) -> Unit)?) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            callback?.invoke(true)
            return
        }

        val launcher = mRequestPermissionsLauncher
        if (launcher == null) {
            callback?.invoke(false)
            return
        }

        mRequestPermissionsCallback = callback
        try {
            launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } catch (_: IllegalStateException) {
            mRequestPermissionsCallback = null
            callback?.invoke(false)
        }
    }

    fun connectDevice(mac: String, log: (String) -> Unit) {
        mBluetoothAdapter?.let { adapter ->
            val device = adapter.getRemoteDevice(mac.uppercase())
            ConnectThread(device, log).start()
        }
    }

    fun getBondedDevices(): List<BluetoothDeviceInfo> {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && mContext.checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return listOf()
        }

        return runCatching {
            mBluetoothAdapter?.bondedDevices?.map {
                BluetoothDeviceInfo(it.address, it.name)
            }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private inner class ConnectThread(device: BluetoothDevice, val log: (String) -> Unit) : Thread() {
        var mmSocket: BluetoothSocket? = null
        var mDevice = device

        override fun run() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && mContext.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            mmSocket = mDevice.createRfcommSocketToServiceRecord(UUID.fromString("2b12becb-c5c0-4370-b19c-0917c72e852c"))

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket.
                try {
                    socket.connect()
                    log("BT Connection successful")
                } catch (e: IOException) {
                    log("BT Connection failed")
                }
            }

            mmSocket?.close()
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                log("Could not close the client socket")
            }
        }
    }

}
