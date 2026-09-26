package com.nisargjhaveri.aagateway

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BluetoothFrameCodecTest {
    @Test
    fun roundTripsFrame() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        BluetoothFrameCodec.write(output, 3, payload)
        val frame = BluetoothFrameCodec.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(3, frame.type)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun readsFragmentedInput() {
        val payload = ByteArray(256) { it.toByte() }
        val output = ByteArrayOutputStream()
        BluetoothFrameCodec.write(output, 7, payload)

        val frame = BluetoothFrameCodec.read(FragmentedInputStream(output.toByteArray()))

        assertEquals(7, frame.type)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun rejectsOversizedPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothFrameCodec.write(
                ByteArrayOutputStream(),
                1,
                ByteArray(65_536),
            )
        }
    }

    private class FragmentedInputStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)

        override fun read(): Int = delegate.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return delegate.read(buffer, offset, length.coerceAtMost(1))
        }
    }
}
