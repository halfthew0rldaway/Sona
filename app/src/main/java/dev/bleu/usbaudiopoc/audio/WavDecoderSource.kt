package dev.bleu.usbaudiopoc.audio

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavDecoderSource(
    private val context: Context,
    private val uri: Uri,
) : AudioSource {

    private var pfd: ParcelFileDescriptor? = null
    private var stream: FileInputStream? = null
    private var channel: java.nio.channels.FileChannel? = null
    private var _format: AudioFormat? = null
    val format: AudioFormat? get() = _format

    private var dataSize: Long = 0
    private var dataStartOffset: Long = 0
    private var currentDataOffset: Long = 0

    override fun open(): AudioFormat {
        close()
        pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Cannot open $uri")
        stream = FileInputStream(pfd!!.fileDescriptor)
        channel = stream!!.channel

        val headerBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        readFully(headerBuf, 12)

        if (headerBuf.getInt(0) != 0x46464952) throw IllegalStateException("Not a RIFF file") // RIFF
        if (headerBuf.getInt(8) != 0x45564157) throw IllegalStateException("Not a WAVE file") // WAVE

        var channels = 2
        var sampleRate = 44100
        var bitsPerSample = 16
        var foundData = false

        while (true) {
            val chunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            val read = channel!!.read(chunkHeader)
            if (read < 8) break
            chunkHeader.flip()
            
            val chunkId = chunkHeader.getInt()
            val chunkSize = chunkHeader.getInt()

            if (chunkId == 0x20746D66) { // 'fmt '
                val fmtData = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                readFully(fmtData, chunkSize)
                
                // format type = fmtData.getShort(0)
                channels = fmtData.getShort(2).toInt()
                sampleRate = fmtData.getInt(4)
                bitsPerSample = fmtData.getShort(14).toInt()
            } else if (chunkId == 0x61746164) { // 'data'
                dataSize = chunkSize.toLong()
                dataStartOffset = channel!!.position()
                currentDataOffset = 0
                foundData = true
                break
            } else {
                channel!!.position(channel!!.position() + chunkSize.toLong())
            }
        }

        if (!foundData) throw IllegalStateException("No data chunk found in WAV")

        val durationUs = (dataSize * 1000000L) / (sampleRate * channels * (bitsPerSample / 8))

        _format = AudioFormat(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            durationMs = durationUs / 1000,
            mimeType = "audio/wav",
            pcmEncoding = if (bitsPerSample == 32) android.media.AudioFormat.ENCODING_PCM_FLOAT else android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        return _format!!
    }

    private fun readFully(buffer: ByteBuffer, amount: Int) {
        var read = 0
        while (read < amount) {
            val r = channel?.read(buffer) ?: -1
            if (r < 0) throw IllegalStateException("Unexpected EOF parsing WAV")
            read += r
        }
    }

    override fun read(target: ByteArray, size: Int): Int {
        val ch = channel ?: return -1
        if (currentDataOffset >= dataSize) return -1
        val toRead = minOf(size.toLong(), dataSize - currentDataOffset).toInt()
        
        val buf = ByteBuffer.wrap(target, 0, toRead)
        val read = ch.read(buf)
        if (read > 0) currentDataOffset += read
        return if (read < 0) -1 else read
    }

    override fun seekTo(positionMs: Long) {
        val fmt = _format ?: return
        val bytesPerMs = (fmt.sampleRate * fmt.channels * (fmt.bitsPerSample / 8)) / 1000
        val targetOffset = positionMs * bytesPerMs
        
        currentDataOffset = targetOffset.coerceIn(0, dataSize)
        channel?.position(dataStartOffset + currentDataOffset)
    }

    override fun getCurrentPositionMs(): Long {
        val fmt = _format ?: return 0
        val bytesPerMs = (fmt.sampleRate * fmt.channels * (fmt.bitsPerSample / 8)) / 1000
        if (bytesPerMs == 0) return 0
        return currentDataOffset / bytesPerMs
    }

    override fun close() {
        runCatching { stream?.close() }
        runCatching { pfd?.close() }
        stream = null
        pfd = null
        channel = null
        currentDataOffset = 0
    }
}
