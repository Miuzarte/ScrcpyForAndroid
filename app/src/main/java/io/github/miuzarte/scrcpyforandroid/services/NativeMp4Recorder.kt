package io.github.miuzarte.scrcpyforandroid.services

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeMp4Recorder(
    outputFile: File,
    private val includeVideo: Boolean,
    private val includeAudio: Boolean,
    private val inputAudioCodec: Codec?,
    private val width: Int,
    private val height: Int,
    videoBitRate: Int,
    audioBitRate: Int,
) {
    private enum class TrackType { AUDIO, VIDEO }

    private data class PendingSample(
        val trackType: TrackType,
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
    )

    private val lock = Any()
    private val muxer = MediaMuxer(
        outputFile.absolutePath,
        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
    )
    private val audioBitRate = audioBitRate.takeIf { it > 0 } ?: DEFAULT_AAC_BIT_RATE
    private val resolvedVideoBitRate = videoBitRate.takeIf { it > 0 }
        ?: (width * height * DEFAULT_VIDEO_BITS_PER_PIXEL).coerceAtLeast(MIN_VIDEO_BIT_RATE)

    private val audioDecoderBufferInfo = MediaCodec.BufferInfo()
    private val audioEncoderBufferInfo = MediaCodec.BufferInfo()
    private val videoEncoderBufferInfo = MediaCodec.BufferInfo()

    private var audioDecoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var videoEncoder: MediaCodec? = null
    private var videoInputSurface: Surface? = null

    private var audioTrackIndex = -1
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private var released = false
    private var audioBasePtsUs: Long? = null
    private var videoBasePtsUs: Long? = null
    private var lastAudioPtsUs = 0L
    private var lastVideoPtsUs = 0L

    private var audioDecoderPrepared = !includeAudio || inputAudioCodec == Codec.RAW
    private var audioEncoderPrepared = false
    private var audioDecoderInputEnded = false
    private var audioEncoderInputEnded = false
    private var audioEncoderOutputEnded = false
    private var videoEncoderInputEnded = false
    private var videoEncoderOutputEnded = false

    private var reusablePcmBuffer = ByteArray(0)
    private val pendingSamples = mutableListOf<PendingSample>()

    suspend fun start() {
        if (!includeVideo) return
        setupVideoEncoder()
        val surface = synchronized(lock) { videoInputSurface } ?: return
        NativeCoreFacade.attachRecordingSurface(
            surface = surface,
            width = width,
            height = height,
            onFrameRendered = { onVideoFrameRendered() },
        )
    }

    fun feedAudioPacket(data: ByteArray, ptsUs: Long, isConfig: Boolean) {
        if (!includeAudio) return
        synchronized(lock) {
            if (released) return

            if (isConfig) {
                if (inputAudioCodec != null && inputAudioCodec != Codec.RAW) {
                    prepareAudioDecoder(data)
                }
                return
            }

            ensureAudioEncoder()

            if (inputAudioCodec == Codec.RAW) {
                queuePcmToAudioEncoder(data, ptsUs)
                drainAudioEncoderLocked()
                return
            }

            if (!audioDecoderPrepared) return
            val currentDecoder = audioDecoder ?: return
            val inputIndex = currentDecoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) {
                drainAudioDecoderLocked()
                drainAudioEncoderLocked()
                return
            }
            val inputBuffer = currentDecoder.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(data)
            currentDecoder.queueInputBuffer(inputIndex, 0, data.size, ptsUs, 0)
            drainAudioDecoderLocked()
            drainAudioEncoderLocked()
        }
    }

    suspend fun release() {
        val surface = synchronized(lock) {
            if (released) return
            released = true
            videoInputSurface
        }

        if (includeVideo && surface != null) {
            NativeCoreFacade.detachRecordingSurface(surface, releaseSurface = false)
        }

        synchronized(lock) {
            runCatching {
                signalAudioEndOfStreamLocked()
                signalVideoEndOfStreamLocked()
                drainAudioDecoderLocked()
                drainAudioEncoderLocked()
                drainVideoEncoderLocked()
            }.onFailure {
                Log.w(TAG, "release(): final drain failed", it)
            }

            runCatching { audioDecoder?.stop() }
            runCatching { audioDecoder?.release() }
            runCatching { audioEncoder?.stop() }
            runCatching { audioEncoder?.release() }
            runCatching { videoEncoder?.stop() }
            runCatching { videoEncoder?.release() }
            runCatching { surface?.release() }
            runCatching {
                if (muxerStarted) {
                    muxer.stop()
                }
            }
            runCatching { muxer.release() }

            audioDecoder = null
            audioEncoder = null
            videoEncoder = null
            videoInputSurface = null
        }
    }

    private fun onVideoFrameRendered() {
        synchronized(lock) {
            if (released) return
            drainVideoEncoderLocked()
        }
    }

    private fun setupVideoEncoder() {
        synchronized(lock) {
            if (!includeVideo || videoEncoder != null || released) return
            val format = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, resolvedVideoBitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, DEFAULT_FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL)
            }
            val codec = MediaCodec.createEncoderByType(VIDEO_MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()
            videoEncoder = codec
            videoInputSurface = surface
        }
    }

    private fun prepareAudioDecoder(config: ByteArray) {
        if (audioDecoderPrepared || released) return
        runCatching {
            val format = when (inputAudioCodec) {
                Codec.OPUS -> createOpusFormat(config)
                Codec.AAC -> createAacFormat(config)
                Codec.FLAC -> createFlacFormat(config)
                else -> null
            } ?: return
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            audioDecoder = codec
            audioDecoderPrepared = true
        }.onFailure {
            Log.w(TAG, "prepareAudioDecoder(): codec=$inputAudioCodec failed", it)
        }
    }

    private fun ensureAudioEncoder() {
        if (!includeAudio || audioEncoderPrepared || released) return
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            SAMPLE_RATE,
            CHANNEL_COUNT,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, audioBitRate)
            setInteger(MediaFormat.KEY_PCM_ENCODING, PCM_ENCODING)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_AUDIO_ENCODER_INPUT_SIZE)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        audioEncoder = codec
        audioEncoderPrepared = true
    }

    private fun drainAudioDecoderLocked() {
        val currentDecoder = audioDecoder ?: return
        while (true) {
            when (val index = currentDecoder.dequeueOutputBuffer(audioDecoderBufferInfo, 0L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> {
                    if (index < 0) continue
                    val outputBuffer = currentDecoder.getOutputBuffer(index)
                    if (outputBuffer != null && audioDecoderBufferInfo.size > 0) {
                        ensurePcmBufferCapacity(audioDecoderBufferInfo.size)
                        outputBuffer.position(audioDecoderBufferInfo.offset)
                        outputBuffer.limit(audioDecoderBufferInfo.offset + audioDecoderBufferInfo.size)
                        outputBuffer.get(reusablePcmBuffer, 0, audioDecoderBufferInfo.size)
                        queuePcmToAudioEncoder(
                            reusablePcmBuffer,
                            audioDecoderBufferInfo.presentationTimeUs,
                            audioDecoderBufferInfo.size,
                        )
                    }
                    val eos =
                        audioDecoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    currentDecoder.releaseOutputBuffer(index, false)
                    if (eos) {
                        audioDecoderInputEnded = true
                        signalAudioEncoderEndOfStreamLocked()
                        return
                    }
                }
            }
        }
    }

    private fun queuePcmToAudioEncoder(
        data: ByteArray,
        ptsUs: Long,
        sizeOverride: Int = data.size,
    ) {
        val currentEncoder = audioEncoder ?: return
        var remaining = sizeOverride
        var offset = 0
        while (remaining > 0) {
            val inputIndex = currentEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) {
                drainAudioEncoderLocked()
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

    private fun drainAudioEncoderLocked() {
        val currentEncoder = audioEncoder ?: return
        while (true) {
            when (val index = currentEncoder.dequeueOutputBuffer(audioEncoderBufferInfo, 0L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    registerTrackLocked(TrackType.AUDIO, currentEncoder.outputFormat)
                }

                else -> {
                    if (index < 0) continue
                    val outputBuffer = currentEncoder.getOutputBuffer(index)
                    val codecConfig =
                        audioEncoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (outputBuffer != null &&
                        audioEncoderBufferInfo.size > 0 &&
                        !codecConfig
                    ) {
                        writeSampleLocked(
                            TrackType.AUDIO,
                            outputBuffer,
                            audioEncoderBufferInfo,
                        )
                    }
                    val eos =
                        audioEncoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    currentEncoder.releaseOutputBuffer(index, false)
                    if (eos) {
                        audioEncoderOutputEnded = true
                        return
                    }
                }
            }
        }
    }

    private fun drainVideoEncoderLocked() {
        val currentEncoder = videoEncoder ?: return
        while (true) {
            when (val index = currentEncoder.dequeueOutputBuffer(videoEncoderBufferInfo, 0L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    registerTrackLocked(TrackType.VIDEO, currentEncoder.outputFormat)
                }

                else -> {
                    if (index < 0) continue
                    val outputBuffer = currentEncoder.getOutputBuffer(index)
                    val codecConfig =
                        videoEncoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (outputBuffer != null &&
                        videoEncoderBufferInfo.size > 0 &&
                        !codecConfig
                    ) {
                        writeSampleLocked(
                            TrackType.VIDEO,
                            outputBuffer,
                            videoEncoderBufferInfo,
                        )
                    }
                    val eos =
                        videoEncoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    currentEncoder.releaseOutputBuffer(index, false)
                    if (eos) {
                        videoEncoderOutputEnded = true
                        return
                    }
                }
            }
        }
    }

    private fun registerTrackLocked(trackType: TrackType, format: MediaFormat) {
        when (trackType) {
            TrackType.AUDIO -> {
                if (audioTrackIndex >= 0) return
                audioTrackIndex = muxer.addTrack(format)
            }

            TrackType.VIDEO -> {
                if (videoTrackIndex >= 0) return
                videoTrackIndex = muxer.addTrack(format)
            }
        }
        startMuxerIfReadyLocked()
    }

    private fun startMuxerIfReadyLocked() {
        if (muxerStarted) return
        val audioReady = !includeAudio || audioTrackIndex >= 0
        val videoReady = !includeVideo || videoTrackIndex >= 0
        if (!audioReady || !videoReady) return
        muxer.start()
        muxerStarted = true
        if (pendingSamples.isNotEmpty()) {
            val snapshot = pendingSamples.sortedBy { it.presentationTimeUs }
            pendingSamples.clear()
            snapshot.forEach { sample ->
                val trackIndex = when (sample.trackType) {
                    TrackType.AUDIO -> audioTrackIndex
                    TrackType.VIDEO -> videoTrackIndex
                }
                val normalizedPtsUs = normalizePtsLocked(
                    trackType = sample.trackType,
                    presentationTimeUs = sample.presentationTimeUs,
                )
                val bufferInfo = MediaCodec.BufferInfo().apply {
                    offset = 0
                    size = sample.data.size
                    presentationTimeUs = normalizedPtsUs
                    flags = sample.flags
                }
                muxer.writeSampleData(trackIndex, ByteBuffer.wrap(sample.data), bufferInfo)
            }
        }
    }

    private fun writeSampleLocked(
        trackType: TrackType,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        val size = bufferInfo.size
        val data = ByteArray(size)
        buffer.position(bufferInfo.offset)
        buffer.limit(bufferInfo.offset + size)
        buffer.get(data)

        if (!muxerStarted) {
            pendingSamples += PendingSample(
                trackType = trackType,
                data = data,
                presentationTimeUs = bufferInfo.presentationTimeUs,
                flags = bufferInfo.flags,
            )
            return
        }

        val trackIndex = when (trackType) {
            TrackType.AUDIO -> audioTrackIndex
            TrackType.VIDEO -> videoTrackIndex
        }
        val normalizedPtsUs = normalizePtsLocked(
            trackType = trackType,
            presentationTimeUs = bufferInfo.presentationTimeUs,
        )
        val writeInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            this.size = size
            presentationTimeUs = normalizedPtsUs
            flags = bufferInfo.flags
        }
        muxer.writeSampleData(trackIndex, ByteBuffer.wrap(data), writeInfo)
    }

    private fun normalizePtsLocked(
        trackType: TrackType,
        presentationTimeUs: Long,
    ): Long {
        return when (trackType) {
            TrackType.AUDIO -> {
                val base = audioBasePtsUs ?: presentationTimeUs.also {
                    audioBasePtsUs = it
                    lastAudioPtsUs = 0L
                }
                val normalized = (presentationTimeUs - base).coerceAtLeast(0L)
                normalized.coerceAtLeast(lastAudioPtsUs).also { lastAudioPtsUs = it }
            }

            TrackType.VIDEO -> {
                val base = videoBasePtsUs ?: presentationTimeUs.also {
                    videoBasePtsUs = it
                    lastVideoPtsUs = 0L
                }
                val normalized = (presentationTimeUs - base).coerceAtLeast(0L)
                normalized.coerceAtLeast(lastVideoPtsUs).also { lastVideoPtsUs = it }
            }
        }
    }

    private fun signalAudioEndOfStreamLocked() {
        if (!includeAudio) return
        if (inputAudioCodec != Codec.RAW && !audioDecoderInputEnded) {
            val currentDecoder = audioDecoder ?: return
            val inputIndex = currentDecoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex >= 0) {
                currentDecoder.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    0L,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                audioDecoderInputEnded = true
            }
            return
        }
        signalAudioEncoderEndOfStreamLocked()
    }

    private fun signalAudioEncoderEndOfStreamLocked() {
        if (audioEncoderInputEnded || audioEncoderOutputEnded) return
        val currentEncoder = audioEncoder ?: return
        val inputIndex = currentEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            currentEncoder.queueInputBuffer(
                inputIndex,
                0,
                0,
                0L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            audioEncoderInputEnded = true
        }
    }

    private fun signalVideoEndOfStreamLocked() {
        if (!includeVideo || videoEncoderInputEnded || videoEncoderOutputEnded) return
        val currentEncoder = videoEncoder ?: return
        currentEncoder.signalEndOfInputStream()
        videoEncoderInputEnded = true
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
        private const val TAG = "NativeMp4Recorder"
        private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val SAMPLE_RATE = 48_000
        private const val CHANNEL_COUNT = 2
        private const val PCM_ENCODING = 2
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val OPUS_SEEK_PREROLL_NS = 80_000_000L
        private const val DEFAULT_AAC_BIT_RATE = 128_000
        private const val MAX_AUDIO_ENCODER_INPUT_SIZE = 16 * 1024
        private const val BYTES_PER_SECOND_PCM_16_STEREO = SAMPLE_RATE * CHANNEL_COUNT * 2
        private const val DEFAULT_FRAME_RATE = 60
        private const val DEFAULT_I_FRAME_INTERVAL = 2
        private const val DEFAULT_VIDEO_BITS_PER_PIXEL = 6
        private const val MIN_VIDEO_BIT_RATE = 4_000_000
    }
}
