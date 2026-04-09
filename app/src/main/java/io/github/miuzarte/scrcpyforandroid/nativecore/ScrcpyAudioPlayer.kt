package io.github.miuzarte.scrcpyforandroid.nativecore

// Go reader note: Audio output helper for scrcpy stream: decodes/plays PCM or codec audio frames.

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes and plays scrcpy audio stream (OPUS / AAC / FLAC / RAW PCM).
 *
 * All [feedPacket] calls are expected from a single background thread.
 * [release] may be called from any thread.
 */
class ScrcpyAudioPlayer(private val codecId: Int) {

    private var mediaCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    @Volatile
    private var prepared = false

    @Volatile
    private var released = false
    private var packetCount = 0L

    fun feedPacket(data: ByteArray, ptsUs: Long, isConfig: Boolean) {
        if (released) return

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
            ensureRawAudioTrack()?.write(data, 0, data.size, AudioTrack.WRITE_NON_BLOCKING)
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
        return AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
            (minBuf * 4).coerceAtLeast(65536),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
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
                val pcm = ByteArray(size)
                outBuf.position(bufferInfo.offset)
                outBuf.get(pcm)
                track.write(pcm, 0, size, AudioTrack.WRITE_NON_BLOCKING)
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

    private fun longBuffer(value: Long): ByteBuffer =
        ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply { putLong(value); flip() }

    companion object {
        private const val TAG = "ScrcpyAudioPlayer"
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = 2
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val OPUS_SEEK_PREROLL_NS = 80_000_000L // 80 ms
    }
}
