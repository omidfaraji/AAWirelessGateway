package com.nisargjhaveri.aagateway

import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.preference.PreferenceManager

class USBReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usbreceiver)
    }

    override fun onResume() {
        super.onResume()

        if (intent.action?.equals(UsbManager.ACTION_USB_ACCESSORY_ATTACHED) == true) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)

            if (preferences.getBoolean("is_gateway", false)) {
                val configuration = GatewayConfiguration.from(preferences)
                configuration.validationError(this)?.let { error ->
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    return
                }

                val accessory =
                    IntentCompat.getParcelableExtra(
                        intent,
                        UsbManager.EXTRA_ACCESSORY,
                        UsbAccessory::class.java,
                    )

                accessory?.also { usbAccessory ->
                    val i = Intent(this, AAGatewayService::class.java)
                    i.putExtra(UsbManager.EXTRA_ACCESSORY, usbAccessory)
                    this.startForegroundService(i)
                }
            }
            else {
                this.startActivity(Intent(this, MainActivity::class.java))
            }
        }

        finish()
    }
}