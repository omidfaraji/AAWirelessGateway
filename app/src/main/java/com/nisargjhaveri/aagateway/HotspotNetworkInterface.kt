package com.nisargjhaveri.aagateway

import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Collections

fun NetworkInterface.hotspotHardwareAddress(): String? {
    hardwareAddress?.let { address ->
        return address.joinToString(":") { "%02x".format(it.toInt() and 0xff) }
    }

    val address =
        Collections.list(inetAddresses)
            .filterIsInstance<Inet6Address>()
            .firstOrNull {
                val bytes = it.address
                it.isLinkLocalAddress &&
                    bytes[11] == 0xff.toByte() &&
                    bytes[12] == 0xfe.toByte()
            }
            ?.address
            ?: return null
    val macAddress =
        byteArrayOf(
            (address[8].toInt() xor 0x02).toByte(),
            address[9],
            address[10],
            address[13],
            address[14],
            address[15],
        )
    return macAddress.joinToString(":") { "%02x".format(it.toInt() and 0xff) }
}
