package io.github.miuzarte.scrcpyforandroid.services

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeWavRecorder(
    private val output: RecordingTarget,
    private val inputCodec: Codec,
) {
    private val stream = output.outputStream()
    private val decoderBufferInfo = MediaCodec.BufferInfo()
    private var decoder: MediaCodec? = null
    private var released = false
    private var decoderPrepared = inputCodec == Codec.RAW
    private var decoderInputEnded = false
    private var bytesWritten = 0L
    private var reusablePcmBuffer = ByteArray(0)

    init {
        stream.write(ByteArray(WAV_HEADER_SIZE))
    }

    fun feedPacket(data: ByteArray, ptsUs: Long, isConfig: Boolean) {
        if (released) return
        if (isConfig) {
            if (inputCodec != Codec.RAW) {
                prepareDecoder(data)
            }
            return
        }

        if (inputCodec == Codec.RAW) {
            writePcm(data, data.size)
            return
        }

        if (!decoderPrepared) return
        val currentDecoder = decoder ?: return
        val inputIndex = currentDecoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex < 0) {
            drainDecoder()
            return
        }
        val inputBuffer = currentDecoder.getInputBuffer(inputIndex) ?: return
        inputBuffer.clear()
        inputBuffer.put(data)
        currentDecoder.queueInputBuffer(inputIndex, 0, data.size, ptsUs, 0)
        drainDecoder()
    }

    fun release() {
        if (released) return
        released = true
        runCatching {
            signalEndOfStream()
            drainDecoder()
        }.onFailure {
            Log.w(TAG, "release(): final drain failed", it)
        }
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        runCatching {
            stream.channel.position(0)
            stream.write(wavHeader(bytesWritten))
            stream.flush()
        }
        runCatching { stream.close() }
        output.finish(true)
        decoder = null
    }

    private fun prepareDecoder(config: ByteArray) {
        if (decoderPrepared || released) return
        runCatching {
            val format = when (inputCodec) {
                Codec.OPUS -> createOpusFormat(config)
                Codec.AAC -> createAacFormat(config)
                Codec.FLAC -> createFlacFormat(config)
                else -> null
            } ?: return
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            decoder = codec
            decoderPrepared = true
        }.onFailure {
            Log.w(TAG, "prepareDecoder(): codec=$inputCodec failed", it)
        }
    }

    private fun drainDecoder() {
        val currentDecoder = decoder ?: return
        while (true) {
            when (val index = currentDecoder.dequeueOutputBuffer(decoderBufferInfo, 0L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> {
                    if (index < 0) continue
                    val outputBuffer = currentDecoder.getOutputBuffer(index)
                    if (outputBuffer != null && decoderBufferInfo.size > 0) {
                        ensurePcmBufferCapacity(decoderBufferInfo.size)
                        outputBuffer.position(decoderBufferInfo.offset)
                        outputBuffer.limit(decoderBufferInfo.offset + decoderBufferInfo.size)
                        outputBuffer.get(reusablePcmBuffer, 0, decoderBufferInfo.size)
                        writePcm(reusablePcmBuffer, decoderBufferInfo.size)
                    }
                    val eos = decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    currentDecoder.releaseOutputBuffer(index, false)
                    if (eos) {
                        return
                    }
                }
            }
        }
    }

    private fun signalEndOfStream() {
        if (inputCodec == Codec.RAW || decoderInputEnded) return
        val currentDecoder = decoder ?: return
        val inputIndex = currentDecoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            currentDecoder.queueInputBuffer(
                inputIndex,
                0,
                0,
                0L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            decoderInputEnded = true
        }
    }

    private fun writePcm(buffer: ByteArray, size: Int) {
        stream.write(buffer, 0, size)
        bytesWritten += size
    }

    private fun createOpusFormat(opusHead: ByteArray): MediaFormat {
        return MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            SAMPLE_RATE,
            CHANNEL_COUNT,
        ).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(opusHead))
            if (opusHead.size >= 12) {
                val preSkip =
                    ((opusHead[11].toInt() and 0xFF) shl 8) or (opusHead[10].toInt() and 0xFF)
                val codecDelayNs = preSkip.toLong() * 1_000_000_000L / SAMPLE_RATE
                setByteBuffer("csd-1", longBuffer(codecDelayNs))
                setByteBuffer("csd-2", longBuffer(OPUS_SEEK_PREROLL_NS))
            }
        }
    }

    private fun createAacFormat(config: ByteArray): MediaFormat {
        return MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            SAMPLE_RATE,
            CHANNEL_COUNT,
        ).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(config))
        }
    }

    private fun createFlacFormat(config: ByteArray): MediaFormat {
        return MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_FLAC,
            SAMPLE_RATE,
            CHANNEL_COUNT,
        ).apply {
            if (config.isNotEmpty()) {
                setByteBuffer("csd-0", ByteBuffer.wrap(config))
            }
        }
    }

    private fun ensurePcmBufferCapacity(requiredSize: Int) {
        if (reusablePcmBuffer.size < requiredSize) {
            reusablePcmBuffer = ByteArray(requiredSize)
        }
    }

    private fun wavHeader(dataSize: Long): ByteArray {
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val riffSize = (dataSize + 36L).coerceAtMost(UInt.MAX_VALUE.toLong()).toInt()
        val pcmSize = dataSize.coerceAtMost(UInt.MAX_VALUE.toLong()).toInt()
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(riffSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(CHANNEL_COUNT.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(BYTES_PER_SECOND_PCM_16_STEREO)
        header.putShort((CHANNEL_COUNT * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmSize)
        return header.array()
    }

    private fun longBuffer(value: Long): ByteBuffer {
        return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
            putLong(value)
            flip()
        }
    }

    companion object {
        private const val TAG = "NativeWavRecorder"
        private const val SAMPLE_RATE = 48_000
        private const val CHANNEL_COUNT = 2
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val OPUS_SEEK_PREROLL_NS = 80_000_000L
        private const val BYTES_PER_SECOND_PCM_16_STEREO = SAMPLE_RATE * CHANNEL_COUNT * 2
        private const val WAV_HEADER_SIZE = 44
    }
}
