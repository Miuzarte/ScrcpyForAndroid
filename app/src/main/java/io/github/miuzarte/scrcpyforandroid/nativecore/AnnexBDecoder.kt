package io.github.miuzarte.scrcpyforandroid.nativecore

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

/**
 * AnnexBDecoder
 *
 * Purpose:
 * - Wraps Android MediaCodec for Annex-B framed codecs (H.264/H.265/AV1).
 * - Handles critical startup packets (config/keyframes) and provides callbacks
 *   for output size changes and FPS updates.
 *
 * Threading / safety:
 * - Public methods are synchronized to allow calls from multiple threads
 *   (packet producer vs. teardown). Internally, MediaCodec callbacks and
 *   buffer queues are used on the calling thread.
 */
class AnnexBDecoder(
    width: Int,
    height: Int,
    outputSurface: Surface,
    mimeType: String = MIME_AVC,
    sps: ByteArray? = null,
    pps: ByteArray? = null,
    onOutputSizeChanged: ((width: Int, height: Int) -> Unit)? = null,
    onFpsUpdated: ((fps: Float) -> Unit)? = null,
) {
    private val codec: MediaCodec = MediaCodec.createDecoderByType(mimeType)
    private val bufferInfo = MediaCodec.BufferInfo()
    private val outputSizeCallback = onOutputSizeChanged
    private val fpsUpdatedCallback = onFpsUpdated
    private val decoderMime = mimeType
    private var inCount = 0L
    private var outCount = 0L
    private var fpsWindowStartNs = System.nanoTime()
    private var fpsWindowFrameCount = 0

    @Volatile
    private var released = false

    init {
        if (!outputSurface.isValid) {
            throw IllegalStateException("Cannot initialize decoder: output surface is not valid")
        }
        val format = MediaFormat.createVideoFormat(mimeType, width, height)
        if (sps != null) {
            format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(sps))
        }
        if (pps != null) {
            format.setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(pps))
        }
        codec.configure(format, outputSurface, null, 0)
        codec.start()
    }

    /**
     * Feed an Annex-B framed packet into the decoder.
     *
     * - `isConfig` indicates codec configuration packets (SPS/PPS) and are handled with
     *   higher priority: they can clear bootstrap buffering and are retried for input.
     * - `isKeyFrame` is used to mark access units that allow decoder restarts.
     * - The method attempts to dequeue an input buffer and queue the data; if no
     *   input buffer is available for critical packets, it drains output and retries
     *   to reduce startup stalls.
     */
    @Synchronized
    fun feedAnnexB(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean, isConfig: Boolean = false) {
        if (released) {
            return
        }
        runCatching {
            var inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIndex < 0 && (isConfig || isKeyFrame)) {
                // Retry for critical packets to reduce startup stalls on av1/hevc.
                drainOutput()
                inputIndex = codec.dequeueInputBuffer(CRITICAL_INPUT_TIMEOUT_US)
            }
            if (inputIndex >= 0) {
                val inBuf = codec.getInputBuffer(inputIndex)
                inBuf?.clear()
                inBuf?.put(data)
                var flags = 0
                if (isKeyFrame) {
                    flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if (isConfig) {
                    flags = flags or MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                }
                codec.queueInputBuffer(inputIndex, 0, data.size, ptsUs, flags)
                inCount += 1
                if (inCount == 1L || inCount % 180L == 0L || isConfig) {
                    Log.i(
                        TAG,
                        "feed(): mime=$decoderMime in=$inCount size=${data.size} key=$isKeyFrame cfg=$isConfig pts=$ptsUs"
                    )
                }
            } else {
                if (isConfig || isKeyFrame) {
                    Log.w(
                        TAG,
                        "drop critical packet: mime=$decoderMime size=${data.size} key=$isKeyFrame cfg=$isConfig"
                    )
                }
            }
            drainOutput()
        }.onFailure {
            Log.w(
                TAG,
                "feed failed: mime=$decoderMime size=${data.size} key=$isKeyFrame cfg=$isConfig",
                it
            )
        }
    }

    /**
     * Switch the codec's output surface.
     *
     * - Returns true when the codec accepted the new surface, false otherwise.
     * - Safe to call during playback to move rendering between preview/fullscreen surfaces.
     */
    @Synchronized
    fun switchOutputSurface(surface: Surface): Boolean {
        if (released) {
            return false
        }
        return runCatching {
            codec.setOutputSurface(surface)
            true
        }.getOrElse { false }
    }

    /**
     * Release the codec resources.
     *
     * - Stops decoding, releases the MediaCodec instance, and marks this decoder as released
     *   so subsequent calls are no-ops.
     */
    @Synchronized
    fun release() {
        released = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private fun drainOutput() {
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, OUTPUT_TIMEOUT_US)
            when {
                outIndex >= 0 -> {
                    codec.releaseOutputBuffer(outIndex, true)
                    outCount += 1
                    recordOutputFrame()
                    if (outCount == 1L || outCount % 180L == 0L) {
                        Log.i(TAG, "drain(): mime=$decoderMime out=$outCount")
                    }
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    notifyOutputSize(codec.outputFormat)
                    continue
                }

                else -> return
            }
        }
    }

    private fun notifyOutputSize(format: MediaFormat) {
        val rawWidth = format.getInteger(MediaFormat.KEY_WIDTH)
        val rawHeight = format.getInteger(MediaFormat.KEY_HEIGHT)

        val width = if (format.containsKey("crop-right") && format.containsKey("crop-left")) {
            format.getInteger("crop-right") - format.getInteger("crop-left") + 1
        } else {
            rawWidth
        }
        val height = if (format.containsKey("crop-bottom") && format.containsKey("crop-top")) {
            format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1
        } else {
            rawHeight
        }

        if (width > 0 && height > 0) {
            outputSizeCallback?.invoke(width, height)
        }
    }

    private fun recordOutputFrame() {
        fpsWindowFrameCount += 1
        val nowNs = System.nanoTime()
        val elapsedNs = nowNs - fpsWindowStartNs
        if (elapsedNs < FPS_WINDOW_NS) {
            return
        }
        val fps = (fpsWindowFrameCount * 1_000_000_000f) / elapsedNs.toFloat()
        fpsUpdatedCallback?.invoke(fps)
        fpsWindowStartNs = nowNs
        fpsWindowFrameCount = 0
    }

    companion object {
        private const val TAG = "AnnexBDecoder"
        private const val MIME_AVC = "video/avc"
        private const val INPUT_TIMEOUT_US = 10_000L
        private const val CRITICAL_INPUT_TIMEOUT_US = 50_000L
        private const val OUTPUT_TIMEOUT_US = 0L
        private const val FPS_WINDOW_NS = 1_000_000_000L
    }
}
