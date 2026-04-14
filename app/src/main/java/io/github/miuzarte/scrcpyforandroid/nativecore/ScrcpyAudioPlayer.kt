package io.github.miuzarte.scrcpyforandroid.nativecore

// Go reader note: Audio output helper for scrcpy stream: decodes/plays PCM or codec audio frames.

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Decodes and plays scrcpy audio stream (OPUS / AAC / FLAC / RAW PCM).
 *
 * All [feedPacket] calls are expected from a single background thread.
 * [release] may be called from any thread.
 */
class ScrcpyAudioPlayer(
    context: Context,
    private val codecId: Int,
    private val lowLatency: Boolean,
) {

    private var mediaCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var reusablePcmBuffer = ByteArray(0)

    @Volatile
    private var audioThreadPriorityApplied = false

    @Volatile
    private var prepared = false

    @Volatile
    private var released = false
    private var packetCount = 0L

    fun feedPacket(data: ByteArray, ptsUs: Long, isConfig: Boolean) {
        if (released) return
        applyAudioThreadPriorityIfNeeded()

        if (isConfig) {
            Log.i(
                TAG,
                "feedPacket(): config packet size=${data.size} codec=0x${
                    codecId.toUInt().toString(16)
                }"
            )
            when (codecId) {
                Codec.OPUS.id -> prepareOpus(data)
                Codec.AAC.id -> prepareAac(data)
                Codec.FLAC.id -> prepareFlac(data)
                // RAW has no config packet
            }
            return
        }

        packetCount += 1
        if (packetCount == 1L || packetCount % 120L == 0L) {
            Log.i(TAG, "feedPacket(): packets=$packetCount prepared=$prepared size=${data.size}")
        }

        if (codecId == Codec.RAW.id) {
            val track = ensureRawAudioTrack() ?: return
            track.write(
                data,
                0,
                data.size,
                AudioTrack.WRITE_NON_BLOCKING,
            )
            return
        }

        if (!prepared) return
        val codec = mediaCodec ?: return

        val inputIdx = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIdx >= 0) {
            val buf = codec.getInputBuffer(inputIdx) ?: return
            buf.clear()
            buf.put(data)
            codec.queueInputBuffer(inputIdx, 0, data.size, ptsUs, 0)
        }
        drainOutput(codec)
    }

    // OpusHead bytes (already extracted by server's fixOpusConfigPacket)
    private fun prepareOpus(opusHead: ByteArray) {
        if (prepared || released) return
        runCatching {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                SAMPLE_RATE,
                CHANNELS
            )
            format.setByteBuffer("csd-0", ByteBuffer.wrap(opusHead))
            // pre-skip field: 2 bytes LE at offset 10 of the OpusHead
            if (opusHead.size >= 12) {
                val preSkip =
                    ((opusHead[11].toInt() and 0xFF) shl 8) or (opusHead[10].toInt() and 0xFF)
                val codecDelayNs = preSkip.toLong() * 1_000_000_000L / SAMPLE_RATE
                format.setByteBuffer("csd-1", longBuffer(codecDelayNs))
                format.setByteBuffer("csd-2", longBuffer(OPUS_SEEK_PREROLL_NS))
            }
            startCodecAndTrack(format)
        }.onFailure { Log.w(TAG, "prepareOpus failed", it) }
    }

    private fun prepareAac(aacConfig: ByteArray) {
        if (prepared || released) return
        runCatching {
            val format =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(aacConfig))
            startCodecAndTrack(format)
        }.onFailure { Log.w(TAG, "prepareAac failed", it) }
    }

    private fun prepareFlac(flacConfig: ByteArray) {
        if (prepared || released) return
        runCatching {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_FLAC,
                SAMPLE_RATE,
                CHANNELS
            )
            if (flacConfig.isNotEmpty()) {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(flacConfig))
            }
            startCodecAndTrack(format)
        }.onFailure { Log.w(TAG, "prepareFlac failed", it) }
    }

    /**
     * Initialize MediaCodec decoder and an AudioTrack for playback.
     *
     * - Configures codec with provided format and starts an AudioTrack in streaming mode.
     * - Called once when a config packet is received for codec formats.
     */
    private fun startCodecAndTrack(format: MediaFormat) {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        val track = buildAudioTrack()
        codec.start()
        track.play()
        mediaCodec = codec
        audioTrack = track
        prepared = true
        Log.i(TAG, "audio player started: mime=$mime sampleRate=$SAMPLE_RATE ch=$CHANNELS")
    }

    private fun ensureRawAudioTrack(): AudioTrack? {
        if (released) return null
        if (audioTrack == null) {
            val track = buildAudioTrack()
            track.play()
            audioTrack = track
            prepared = true
        }
        return audioTrack
    }

    private fun buildAudioTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(1)
        val framesPerBurst = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_FRAMES_PER_BURST
        val nativeSampleRate = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: SAMPLE_RATE

        val bufferSize = if (lowLatency) {
            val targetBuffer = framesPerBurst * 2 * BYTES_PER_FRAME_PCM16_STEREO
            max(minBuf, targetBuffer)
        } else {
            (minBuf * 4).coerceAtLeast(DEFAULT_BUFFER_SIZE_BYTES)
        }

        val attributesBuilder = AudioAttributes.Builder()
            .setUsage(
                if (lowLatency) AudioAttributes.USAGE_GAME
                else AudioAttributes.USAGE_MEDIA
            )
            .setContentType(
                if (lowLatency) AudioAttributes.CONTENT_TYPE_SONIFICATION
                else AudioAttributes.CONTENT_TYPE_MOVIE
            )
        if (lowLatency) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                attributesBuilder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            }
        }

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(attributesBuilder.build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
        if (lowLatency)
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)

        val track = trackBuilder.build()

        if (lowLatency) {
            Log.i(
                TAG,
                "low-latency audio requested: nativeSampleRate=$nativeSampleRate streamSampleRate=$SAMPLE_RATE " +
                        "framesPerBurst=$framesPerBurst bufferSize=$bufferSize performanceMode=${track.performanceMode}"
            )
        }
        return track
    }

    /**
     * Drain decoder output and write PCM frames to the AudioTrack.
     *
     * - Non-blocking writes are used so audio does not stall the decoder thread.
     */
    private fun drainOutput(codec: MediaCodec) {
        val track = audioTrack ?: return
        var idx = codec.dequeueOutputBuffer(bufferInfo, 0L)
        while (idx >= 0) {
            val outBuf = codec.getOutputBuffer(idx) ?: break
            val size = bufferInfo.size
            if (size > 0) {
                ensurePcmBufferCapacity(size)
                outBuf.position(bufferInfo.offset)
                outBuf.get(reusablePcmBuffer, 0, size)
                track.write(
                    reusablePcmBuffer,
                    0,
                    size,
                    AudioTrack.WRITE_NON_BLOCKING,
                )
            }
            codec.releaseOutputBuffer(idx, false)
            idx = codec.dequeueOutputBuffer(bufferInfo, 0L)
        }
    }

    /**
     * Release media and audio resources. Safe to call from any thread.
     */
    fun release() {
        if (released) return
        released = true
        prepared = false
        runCatching { mediaCodec?.stop() }
        runCatching { mediaCodec?.release() }
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        mediaCodec = null
        audioTrack = null
    }

    private fun applyAudioThreadPriorityIfNeeded() {
        if (!lowLatency || audioThreadPriorityApplied) return
        runCatching {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            audioThreadPriorityApplied = true
        }.onFailure {
            Log.w(TAG, "set audio thread priority failed", it)
        }
    }

    private fun ensurePcmBufferCapacity(requiredSize: Int) {
        if (reusablePcmBuffer.size < requiredSize) {
            reusablePcmBuffer = ByteArray(requiredSize)
        }
    }

    private fun longBuffer(value: Long): ByteBuffer =
        ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply { putLong(value); flip() }

    companion object {
        private const val TAG = "ScrcpyAudioPlayer"
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = 2
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val OPUS_SEEK_PREROLL_NS = 80_000_000L // 80 ms
        private const val DEFAULT_BUFFER_SIZE_BYTES = 65536
        private const val DEFAULT_FRAMES_PER_BURST = 128
        private const val BYTES_PER_FRAME_PCM16_STEREO = 4
    }
}
