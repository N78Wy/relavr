package io.relavr.sender.platform.webrtc

internal data class PcmRingBufferSnapshot(
    val bufferedBytes: Int,
    val writtenBytesSinceLastSnapshot: Long,
    val droppedBytesSinceLastSnapshot: Long,
)

internal class BoundedPcmRingBuffer(
    capacityBytes: Int,
) {
    private val buffer = ByteArray(capacityBytes)
    private var readIndex: Int = 0
    private var writeIndex: Int = 0
    private var availableBytes: Int = 0
    private var writtenBytesSinceLastSnapshot: Long = 0
    private var droppedBytesSinceLastSnapshot: Long = 0

    val capacityBytes: Int = capacityBytes

    fun write(
        source: ByteArray,
        length: Int,
    ) {
        if (length <= 0) {
            return
        }

        val bytesToWrite =
            if (length >= buffer.size) {
                droppedBytesSinceLastSnapshot += availableBytes.toLong()
                source.copyInto(buffer, 0, length - buffer.size, length)
                readIndex = 0
                writeIndex = 0
                availableBytes = buffer.size
                writtenBytesSinceLastSnapshot += buffer.size.toLong()
                return
            } else {
                length
            }

        discardOldest(maxOf(0, availableBytes + bytesToWrite - buffer.size))

        val firstChunk = minOf(bytesToWrite, buffer.size - writeIndex)
        source.copyInto(buffer, writeIndex, 0, firstChunk)
        if (firstChunk < bytesToWrite) {
            source.copyInto(buffer, 0, firstChunk, bytesToWrite)
        }
        writeIndex = (writeIndex + bytesToWrite) % buffer.size
        availableBytes += bytesToWrite
        writtenBytesSinceLastSnapshot += bytesToWrite.toLong()
    }

    fun read(
        destination: ByteArray,
        requestedBytes: Int,
    ): Int {
        val bytesToCopy = minOf(requestedBytes, availableBytes)
        if (bytesToCopy <= 0) {
            return 0
        }

        val firstChunk = minOf(bytesToCopy, buffer.size - readIndex)
        buffer.copyInto(destination, 0, readIndex, readIndex + firstChunk)
        if (firstChunk < bytesToCopy) {
            buffer.copyInto(destination, firstChunk, 0, bytesToCopy - firstChunk)
        }
        readIndex = (readIndex + bytesToCopy) % buffer.size
        availableBytes -= bytesToCopy
        return bytesToCopy
    }

    fun clear() {
        readIndex = 0
        writeIndex = 0
        availableBytes = 0
        writtenBytesSinceLastSnapshot = 0
        droppedBytesSinceLastSnapshot = 0
    }

    fun snapshotAndReset(): PcmRingBufferSnapshot {
        val snapshot =
            PcmRingBufferSnapshot(
                bufferedBytes = availableBytes,
                writtenBytesSinceLastSnapshot = writtenBytesSinceLastSnapshot,
                droppedBytesSinceLastSnapshot = droppedBytesSinceLastSnapshot,
            )
        writtenBytesSinceLastSnapshot = 0
        droppedBytesSinceLastSnapshot = 0
        return snapshot
    }

    private fun discardOldest(bytesToDiscard: Int) {
        if (bytesToDiscard <= 0) {
            return
        }
        val safeDiscard = minOf(bytesToDiscard, availableBytes)
        readIndex = (readIndex + safeDiscard) % buffer.size
        availableBytes -= safeDiscard
        droppedBytesSinceLastSnapshot += safeDiscard.toLong()
    }
}
