package dev.bleu.usbaudiopoc.audio

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.min

class WavStreamingSource(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : Closeable {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var channel: FileChannel? = null
    private var dataOffsetBytes: Long = 0L
    private var bytesRemaining: Long = 0L

    var format: WavFormat? = null
        private set

    fun open(): WavFormat {
        close()
        val descriptor = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Unable to open WAV source")
        val stream = FileInputStream(descriptor.fileDescriptor)
        val fileChannel = stream.channel
        parcelFileDescriptor = descriptor
        inputStream = stream
        channel = fileChannel

        val riffHeader = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        readFully(fileChannel, riffHeader, "RIFF header")
        riffHeader.flip()
        val riffId = ByteArray(4).also { riffHeader.get(it) }.decodeToString()
        val riffSize = riffHeader.int
        val waveId = ByteArray(4).also { riffHeader.get(it) }.decodeToString()
        require(riffId == "RIFF" && waveId == "WAVE") {
            "Only RIFF/WAVE PCM is supported"
        }
        Log.i(TAG, "Opened WAV container size=$riffSize for $uri")

        var parsedFormat: WavFormat? = null
        var parsedDataOffset = -1L
        var parsedDataSize = -1L
        val chunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

        while (fileChannel.position() < fileChannel.size()) {
            chunkHeader.clear()
            readFully(fileChannel, chunkHeader, "chunk header")
            chunkHeader.flip()
            val chunkId = ByteArray(4).also { chunkHeader.get(it) }.decodeToString()
            val chunkSize = chunkHeader.int.toLong() and 0xffffffffL
            val paddedChunkSize = chunkSize + (chunkSize and 1L)

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16L) {
                        throw IOException("Invalid fmt chunk size: $chunkSize")
                    }
                    val fmtBuffer = ByteBuffer.allocate(chunkSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
                    readFully(fileChannel, fmtBuffer, "fmt chunk")
                    fmtBuffer.flip()
                    val audioFormat = fmtBuffer.short.toInt() and 0xffff
                    val channels = fmtBuffer.short.toInt() and 0xffff
                    val sampleRate = fmtBuffer.int
                    val byteRate = fmtBuffer.int
                    val blockAlign = fmtBuffer.short.toInt() and 0xffff
                    val bitsPerSample = fmtBuffer.short.toInt() and 0xffff
                    if (audioFormat != WAVE_FORMAT_PCM) {
                        throw IOException("Unsupported WAV format tag: $audioFormat")
                    }
                    if (bitsPerSample != 16 && bitsPerSample != 24) {
                        throw IOException("Only 16-bit and 24-bit PCM WAV are supported")
                    }
                    parsedFormat = WavFormat(
                        sampleRate = sampleRate,
                        channels = channels,
                        bitsPerSample = bitsPerSample,
                        blockAlign = blockAlign,
                        byteRate = byteRate,
                        dataSizeBytes = 0L,
                    )
                    if ((chunkSize and 1L) == 1L) {
                        fileChannel.position(fileChannel.position() + 1L)
                    }
                }

                "data" -> {
                    parsedDataOffset = fileChannel.position()
                    parsedDataSize = chunkSize
                    fileChannel.position(parsedDataOffset)
                    break
                }

                else -> {
                    fileChannel.position(fileChannel.position() + paddedChunkSize)
                }
            }
        }

        val resolvedFormat = parsedFormat ?: throw IOException("Missing fmt chunk")
        if (parsedDataOffset < 0L || parsedDataSize < 0L) {
            throw IOException("Missing data chunk")
        }

        dataOffsetBytes = parsedDataOffset
        bytesRemaining = parsedDataSize
        format = resolvedFormat.copy(dataSizeBytes = parsedDataSize)
        Log.i(
            TAG,
            "PCM format sr=${resolvedFormat.sampleRate} ch=${resolvedFormat.channels} bits=${resolvedFormat.bitsPerSample} data=$parsedDataSize",
        )
        return format ?: error("WAV format unexpectedly null")
    }

    fun read(target: ByteArray, requestedSize: Int = target.size): Int {
        val activeChannel = channel ?: throw IllegalStateException("Source is not open")
        if (bytesRemaining <= 0L) {
            return -1
        }
        val bytesToRead = min(requestedSize.toLong(), bytesRemaining).toInt()
        val buffer = ByteBuffer.wrap(target, 0, bytesToRead)
        var totalRead = 0
        while (buffer.hasRemaining()) {
            val read = activeChannel.read(buffer)
            if (read < 0) {
                break
            }
            if (read == 0) {
                break
            }
            totalRead += read
        }
        if (totalRead > 0) {
            bytesRemaining -= totalRead.toLong()
        }
        return if (totalRead == 0) -1 else totalRead
    }

    fun resetToDataStart() {
        val activeChannel = channel ?: throw IllegalStateException("Source is not open")
        activeChannel.position(dataOffsetBytes)
        bytesRemaining = format?.dataSizeBytes ?: 0L
    }

    override fun close() {
        Log.i(TAG, "Closing WAV source for $uri")
        runCatching { channel?.close() }
        runCatching { inputStream?.close() }
        runCatching { parcelFileDescriptor?.close() }
        channel = null
        inputStream = null
        parcelFileDescriptor = null
        format = null
        bytesRemaining = 0L
        dataOffsetBytes = 0L
    }

    private fun readFully(channel: FileChannel, buffer: ByteBuffer, label: String) {
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer)
            if (read < 0) {
                throw IOException("Unexpected EOF while reading $label")
            }
        }
    }

    private companion object {
        const val TAG = "WavStreamingSource"
        const val WAVE_FORMAT_PCM = 0x0001
    }
}
