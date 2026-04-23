package dev.bleu.usbaudiopoc.audio

import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioTrack

data class AudioFormatInfo(
    val sourceSampleRate: Int,
    val sourceBitDepth: Int,
    val outputSampleRate: Int,
    val isMatched: Boolean = sourceSampleRate == outputSampleRate
) {
    fun getStatusString(): String = if (isMatched) {
        "Matched (No resample likely)"
    } else {
        "Mismatch (Resampling likely)"
    }
}

enum class PlaybackMode {
    BIT_PERFECT,
    DSP
}

// Ensure compilation by defining the missing classes or using existing ones
data class AudioSourceMetadata(
    val sampleRate: Int,
    val channelCount: Int,
    val bitDepth: Int,
    val pcmEncoding: Int = AndroidAudioFormat.ENCODING_PCM_16BIT
)

interface AudioPipeline {
    fun initialize(sampleRate: Int, channelCount: Int, bitDepth: Int)
    fun writePCMData(buffer: ByteArray, size: Int)
    fun setVolume(volume: Float)
    fun flush()
    fun release()
    val formatInfo: AudioFormatInfo
    val bufferSize: Int
}

class BitPerfectPipeline : AudioPipeline {
    private var audioTrack: AudioTrack? = null
    override var bufferSize: Int = 0
    lateinit var currentFormatInfo: AudioFormatInfo

    override fun initialize(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        val audioFormat = when {
            bitDepth >= 24 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> 
                AndroidAudioFormat.ENCODING_PCM_24BIT_PACKED
            bitDepth >= 24 -> AndroidAudioFormat.ENCODING_PCM_FLOAT
            else -> AndroidAudioFormat.ENCODING_PCM_16BIT
        }
        
        val channelConfig = if (channelCount == 1) AndroidAudioFormat.CHANNEL_OUT_MONO else AndroidAudioFormat.CHANNEL_OUT_STEREO

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val attrBuilder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(attrBuilder.build())
            .setAudioFormat(
                AndroidAudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
        
        bufferSize = minBufferSize * 2

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        audioTrack = trackBuilder.build()
            
        currentFormatInfo = AudioFormatInfo(
            sourceSampleRate = sampleRate,
            sourceBitDepth = bitDepth,
            outputSampleRate = audioTrack?.sampleRate ?: sampleRate
        )
            
        audioTrack?.play()
    }

    override fun writePCMData(buffer: ByteArray, size: Int) {
        audioTrack?.write(buffer, 0, size, AudioTrack.WRITE_BLOCKING)
    }

    override fun setVolume(volume: Float) {
        audioTrack?.setVolume(1.0f) // Unity gain enforced for bit-perfect
    }

    override fun flush() {
        audioTrack?.pause()
        audioTrack?.flush()
    }

    override fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override val formatInfo: AudioFormatInfo
        get() = currentFormatInfo
}

// Dummy DspProcessorChain to ensure compilation
class DspProcessorChain {
    private var currentGain = 1.0f
    private val eqGainsDb = FloatArray(10) { 0f }
    fun setEqGain(band: Int, gainDb: Float) {
        if(band in 0..9) {
            eqGainsDb[band] = gainDb
        }
    }

    fun getTargetSampleRate(): Int? = null
    fun initialize(sampleRate: Int, targetSampleRate: Int, channelCount: Int) {}
    
    fun process(buffer: ByteArray, size: Int): FloatArray {
        // Basic 16-bit to Float conversion (assumes 16-bit little-endian PCM)
        val floatCount = size / 2
        val out = FloatArray(floatCount)
        for (i in 0 until floatCount) {
            val byteIndex = i * 2
            val low = buffer[byteIndex].toInt() and 0xFF
            val high = buffer[byteIndex + 1].toInt()
            val sample = (high shl 8) or low
            out[i] = (sample.toFloat() / 32768.0f) * currentGain
        }
        return out
    }
    
    fun setGain(volume: Float) {
        currentGain = volume
    }
    fun flush() {}
    fun release() {}
}

class DspPipeline(
    private val dspChain: DspProcessorChain = DspProcessorChain()
) : AudioPipeline {

    private var audioTrack: AudioTrack? = null
    override var bufferSize: Int = 0
    lateinit var currentFormatInfo: AudioFormatInfo

    override fun initialize(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        val audioFormat = AndroidAudioFormat.ENCODING_PCM_FLOAT 
        val channelConfig = if (channelCount == 1) AndroidAudioFormat.CHANNEL_OUT_MONO else AndroidAudioFormat.CHANNEL_OUT_STEREO
        
        val targetSampleRate = dspChain.getTargetSampleRate() ?: sampleRate

        val minBufferSize = AudioTrack.getMinBufferSize(targetSampleRate, channelConfig, audioFormat)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AndroidAudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(targetSampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        bufferSize = minBufferSize * 4
            
        dspChain.initialize(sampleRate, targetSampleRate, channelCount)

        currentFormatInfo = AudioFormatInfo(
            sourceSampleRate = sampleRate,
            sourceBitDepth = bitDepth,
            outputSampleRate = audioTrack?.sampleRate ?: targetSampleRate
        )

        audioTrack?.play()
    }

    override fun writePCMData(buffer: ByteArray, size: Int) {
        val dspOutput = dspChain.process(buffer, size)
        audioTrack?.write(dspOutput, 0, dspOutput.size, AudioTrack.WRITE_BLOCKING)
    }

    override fun setVolume(volume: Float) {
        audioTrack?.setVolume(volume)
        dspChain.setGain(volume)
    }

    fun setEqGain(band: Int, gainDb: Float) {
        dspChain.setEqGain(band, gainDb)
    }

    override fun flush() {
        dspChain.flush()
        audioTrack?.pause()
        audioTrack?.flush()
    }

    override fun release() {
        dspChain.release()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override val formatInfo: AudioFormatInfo
        get() = currentFormatInfo
}

class AudioEngineManager {
    private var activePipeline: AudioPipeline? = null
    private var currentMode: PlaybackMode = PlaybackMode.DSP

    var currentFormatListener: ((AudioFormatInfo) -> Unit)? = null

    fun setPlaybackMode(mode: PlaybackMode, decoderMeta: AudioSourceMetadata) {
        if (currentMode == mode && activePipeline != null) return
        
        currentMode = mode
        rebuildAudioPipeline(decoderMeta)
    }

    private fun rebuildAudioPipeline(decoderMeta: AudioSourceMetadata) {
        activePipeline?.flush()
        activePipeline?.release()
        activePipeline = null

        activePipeline = when (currentMode) {
            PlaybackMode.BIT_PERFECT -> BitPerfectPipeline()
            PlaybackMode.DSP -> DspPipeline(DspProcessorChain())
        }

        activePipeline?.initialize(
            sampleRate = decoderMeta.sampleRate,
            channelCount = decoderMeta.channelCount,
            bitDepth = decoderMeta.bitDepth
        )

        activePipeline?.formatInfo?.let {
            currentFormatListener?.invoke(it)
        }
    }

    fun onSamplesDecoded(pcmData: ByteArray, size: Int) {
        activePipeline?.writePCMData(pcmData, size)
    }

    fun handleVolumeChange(newVolume: Float) {
        if (currentMode == PlaybackMode.BIT_PERFECT) {
            showVolumeWarning()
            activePipeline?.setVolume(1.0f)
        } else {
            activePipeline?.setVolume(newVolume)
        }
    }

    private fun showVolumeWarning() {
        // "Volume control is disabled in Bit-Perfect Mode"
    }
}
