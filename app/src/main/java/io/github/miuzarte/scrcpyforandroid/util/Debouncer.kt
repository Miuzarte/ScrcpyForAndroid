package io.github.miuzarte.scrcpyforandroid.util

import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

/**
 * Simple debouncer that delays execution of [block] until [delayMs]
 * milliseconds have elapsed since the last call to [invoke].
 *
 * All calls happen on the main thread via [Handler].
 */
class Debouncer(
    private val delayMs: Long,
    private val block: (Int, Int) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun invoke(width: Float, height: Float) {
        invoke(width.roundToInt(), height.roundToInt())
    }

    fun invoke(width: Int, height: Int) {
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { block(width, height) }
        pending = task
        handler.postDelayed(task, delayMs)
    }

    fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}
