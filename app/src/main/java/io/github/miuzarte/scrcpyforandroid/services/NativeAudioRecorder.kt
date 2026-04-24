package io.github.miuzarte.scrcpyforandroid.services

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeAudioRecorder(
    private val outputFile: File,
    private val inputCodec: Codec,
    bitRate: Int,
) {
    private val outputBitRate = bitRate.takeIf { it > 0 } ?: DEFAULT_AAC_BIT_RATE
    private val decoderBufferInfo = MediaCodec.BufferInfo()
    private val encoderBufferInfo = MediaCodec.BufferInfo()
    private val muxer = MediaMuxer(
        outputFile.absolutePath,
        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
    )

    private var decoder: MediaCodec? = null
    private var encoder: MediaCodec? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var released = false
    private var decoderPrepared = inputCodec == Codec.RAW
    private var encoderPrepared = false
    private var decoderInputEnded = false
    private var encoderInputEnded = false
    private var encoderOutputEnded = false
    private var reusablePcmBuffer = ByteArray(0)

    fun feedPacket(data: ByteArray, ptsUs: Long, isConfig: Boolean) {
        if (released) return

        if (isConfig) {
            if (inputCodec != Codec.RAW) {
                prepareDecoder(data)
            }
            return
        }

        ensureEncoder()

        if (inputCodec == Codec.RAW) {
            queuePcmToEncoder(data, ptsUs)
            drainEncoder()
            return
        }

        if (!decoderPrepared) return
        val currentDecoder = decoder ?: return

        val inputIndex = currentDecoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex < 0) {
            drainDecoder()
            drainEncoder()
            return
        }

        val inputBuffer = currentDecoder.getInputBuffer(inputIndex) ?: return
        inputBuffer.clear()
        inputBuffer.put(data)
        currentDecoder.queueInputBuffer(inputIndex, 0, data.size, ptsUs, 0)
        drainDecoder()
        drainEncoder()
    }

    fun release() {
        if (released) return
        released = true

        runCatching {
            signalEndOfStream()
            drainDecoder()
            drainEncoder()
        }.onFailure {
            Log.w(TAG, "release(): final drain failed", it)
        }

        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        runCatching {
            if (muxerStarted) {
                muxer.stop()
            }
        }
        runCatching { muxer.release() }

        decoder = null
        encoder = null
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

    private fun ensureEncoder() {
        if (encoderPrepared || released) return
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            SAMPLE_RATE,
            CHANNEL_COUNT,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, outputBitRate)
            setInteger(MediaFormat.KEY_PCM_ENCODING, PCM_ENCODING)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_ENCODER_INPUT_SIZE)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        encoder = codec
        encoderPrepared = true
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
                        queuePcmToEncoder(
                            reusablePcmBuffer,
                            decoderBufferInfo.presentationTimeUs,
                            decoderBufferInfo.size,
                        )
                    }
                    val eos = decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    currentDecoder.releaseOutputBuffer(index, false)
                    if (eos) {
                        decoderInputEnded = true
                        signalEncoderEndOfStream()
                        return
                    }
                }
            }
        }
    }

    private fun queuePcmToEncoder(
        data: ByteArray,
        ptsUs: Long,
        sizeOverride: Int = data.size,
    ) {
        val currentEncoder = encoder ?: return
        var remaining = sizeOverride
        var offset = 0
        while (remaining > 0) {
            val inputIndex = currentEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) {
                drainEncoder()
                return
            }
            val inputBuffer = currentEncoder.getInputBuffer(inputIndex) ?: return
            val chunkSize = minOf(inputBuffer.capacity(), remaining)
            inputBuffer.clear()
            inputBuffer.put(data, offset, chunkSize)
            val chunkPtsUs = ptsUs + pcmBytesToDurationUs(offset)
            currentEncoder.queueInputBuffer(inputIndex, 0, chunkSize, chunkPtsUs, 0)
            offset += chunkSize
            remaining -= chunkSize
        }
    }

    private fun drainEncoder() {
        val currentEncoder = encoder ?: return
        while (true) {
            when (val index = currentEncoder.dequeueOutputBuffer(encoderBufferInfo, 0L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (trackIndex >= 0) {
                        throw IllegalStateException("encoder output format changed twice")
                    }
                    trackIndex = muxer.addTrack(currentEncoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                else -> {
                    if (index < 0) continue
                    val outputBuffer = currentEncoder.getOutputBuffer(index)
                    val codecConfig =
                        encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (outputBuffer != null &&
                        encoderBufferInfo.size > 0 &&
                        muxerStarted &&
                        !codecConfig
                    ) {
                        outputBuffer.position(encoderBufferInfo.offset)
                        outputBuffer.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, encoderBufferInfo)
                    }
                    val eos = encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    currentEncoder.releaseOutputBuffer(index, false)
                    if (eos) {
                        encoderOutputEnded = true
                        return
                    }
                }
            }
        }
    }

    private fun signalEndOfStream() {
        if (inputCodec != Codec.RAW && !decoderInputEnded) {
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
            return
        }
        signalEncoderEndOfStream()
    }

    private fun signalEncoderEndOfStream() {
        if (encoderInputEnded || encoderOutputEnded) return
        val currentEncoder = encoder ?: return
        val inputIndex = currentEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            currentEncoder.queueInputBuffer(
                inputIndex,
                0,
                0,
                0L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            encoderInputEnded = true
        }
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

    private fun pcmBytesToDurationUs(byteCount: Int): Long {
        return byteCount.toLong() * 1_000_000L / BYTES_PER_SECOND_PCM_16_STEREO
    }

    private fun longBuffer(value: Long): ByteBuffer {
        return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
            putLong(value)
            flip()
        }
    }

    companion object {
        private const val TAG = "NativeAudioRecorder"
        private const val SAMPLE_RATE = 48_000
        private const val CHANNEL_COUNT = 2
        private const val PCM_ENCODING = 2
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val OPUS_SEEK_PREROLL_NS = 80_000_000L
        private const val DEFAULT_AAC_BIT_RATE = 128_000
        private const val MAX_ENCODER_INPUT_SIZE = 16 * 1024
        private const val BYTES_PER_SECOND_PCM_16_STEREO = SAMPLE_RATE * CHANNEL_COUNT * 2
    }
}
