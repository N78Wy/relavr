package io.relavr.sender.platform.webrtc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedPcmRingBufferTest {
    @Test
    fun `writes beyond capacity discard the oldest bytes`() {
        val buffer = BoundedPcmRingBuffer(capacityBytes = 8)

        buffer.write(byteArrayOf(1, 2, 3, 4, 5, 6), 6)
        buffer.write(byteArrayOf(7, 8, 9, 10), 4)

        val output = ByteArray(8)
        val bytesRead = buffer.read(output, output.size)

        assertEquals(8, bytesRead)
        assertArrayEquals(byteArrayOf(3, 4, 5, 6, 7, 8, 9, 10), output)
    }

    @Test
    fun `snapshot reports buffered written and dropped bytes`() {
        val buffer = BoundedPcmRingBuffer(capacityBytes = 8)

        buffer.write(byteArrayOf(1, 2, 3, 4, 5, 6), 6)
        buffer.write(byteArrayOf(7, 8, 9, 10), 4)

        val snapshot = buffer.snapshotAndReset()

        assertEquals(8, snapshot.bufferedBytes)
        assertEquals(10L, snapshot.writtenBytesSinceLastSnapshot)
        assertEquals(2L, snapshot.droppedBytesSinceLastSnapshot)
    }
}
