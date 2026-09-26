package com.nisargjhaveri.aagateway

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal object BluetoothFrameCodec {
    private const val MAX_PAYLOAD_SIZE = 65_535

    data class Frame(val type: Int, val payload: ByteArray)

    fun read(inputStream: InputStream): Frame {
        val input = DataInputStream(inputStream)
        val payloadLength = input.readUnsignedShort()
        val type = input.readUnsignedShort()
        require(payloadLength <= MAX_PAYLOAD_SIZE) {
            "Bluetooth frame payload is too large: $payloadLength"
        }

        return Frame(type, ByteArray(payloadLength).also(input::readFully))
    }

    fun write(outputStream: OutputStream, type: Int, payload: ByteArray) {
        require(type in 0..65_535) { "Bluetooth frame type is out of range: $type" }
        require(payload.size <= MAX_PAYLOAD_SIZE) {
            "Bluetooth frame payload is too large: ${payload.size}"
        }

        DataOutputStream(outputStream).apply {
            writeShort(payload.size)
            writeShort(type)
            write(payload)
            flush()
        }
    }
}
