package io.github.miuzarte.scrcpyforandroid.scrcpy

import android.util.Log
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TouchEventHandler(
    private val coroutineScope: CoroutineScope,
    private val session: Scrcpy.Session.SessionInfo,
    private val touchAreaSize: IntSize,
    private val activePointerIds: LinkedHashSet<Int>,
    private val activePointerPositions: LinkedHashMap<Int, Offset>,
    private val activePointerDevicePositions: LinkedHashMap<Int, Pair<Int, Int>>,
    private val pointerLabels: LinkedHashMap<Int, Int>,
    private var nextPointerLabel: Int,
    private val onInjectTouch: suspend (action: Int, pointerId: Long, x: Int, y: Int, pressure: Float, buttons: Int) -> Unit,
    private val onActiveTouchCountChanged: (Int) -> Unit,
    private val onActiveTouchDebugChanged: (String) -> Unit,
    private val onNextPointerLabelChanged: (Int) -> Unit,
) {
    companion object {
        private const val FULLSCREEN_TOUCH_LOG_TAG = "FullscreenTouch"
    }

    private object UiMotionActions {
        const val DOWN = 0
        const val UP = 1
        const val MOVE = 2
        const val CANCEL = 3
        const val POINTER_DOWN = 5
        const val POINTER_UP = 6
    }

    private val eventPointerIds = HashSet<Int>(10)
    private val eventPositions = HashMap<Int, Offset>(10)
    private val eventPressures = HashMap<Int, Float>(10)
    private val justPressedPointerIds = HashSet<Int>(10)

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (touchAreaSize.width == 0 || touchAreaSize.height == 0) {
            return true
        }

        val bounds = calculateContentBounds()

        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            return handleCancelAction(bounds)
        }

        extractEventData(event)
        handleDisappearedPointers(eventPointerIds, bounds)

        val endedPointerId = getEndedPointerId(event)
        handlePointerDown(event, endedPointerId, bounds)
        handlePointerMove(event, endedPointerId, bounds)
        handlePointerUp(endedPointerId, bounds)

        onActiveTouchCountChanged(activePointerIds.size)
        refreshTouchDebug()
        return true
    }

    private data class ContentBounds(
        val width: Float,
        val height: Float,
        val left: Float,
        val top: Float,
    )

    private fun calculateContentBounds(): ContentBounds {
        val sessionAspect = if (session.height == 0) {
            16f / 9f
        } else {
            session.width.toFloat() / session.height.toFloat()
        }
        val containerWidth = touchAreaSize.width.toFloat()
        val containerHeight = touchAreaSize.height.toFloat()
        val containerAspect = containerWidth / containerHeight

        val contentWidth: Float
        val contentHeight: Float
        if (sessionAspect > containerAspect) {
            contentWidth = containerWidth
            contentHeight = containerWidth / sessionAspect
        } else {
            contentHeight = containerHeight
            contentWidth = containerHeight * sessionAspect
        }
        val contentLeft = (containerWidth - contentWidth) / 2f
        val contentTop = (containerHeight - contentHeight) / 2f

        return ContentBounds(contentWidth, contentHeight, contentLeft, contentTop)
    }

    private fun isInsideContent(rawX: Float, rawY: Float, bounds: ContentBounds): Boolean {
        return rawX in bounds.left..(bounds.left + bounds.width) &&
                rawY in bounds.top..(bounds.top + bounds.height)
    }

    private fun mapToDevice(rawX: Float, rawY: Float, bounds: ContentBounds): Pair<Int, Int> {
        val normalizedX = ((rawX - bounds.left) / bounds.width).coerceIn(0f, 1f)
        val normalizedY = ((rawY - bounds.top) / bounds.height).coerceIn(0f, 1f)
        val x = (normalizedX * (session.width - 1).coerceAtLeast(0)).roundToInt()
            .coerceIn(0, (session.width - 1).coerceAtLeast(0))
        val y = (normalizedY * (session.height - 1).coerceAtLeast(0)).roundToInt()
            .coerceIn(0, (session.height - 1).coerceAtLeast(0))
        return x to y
    }

    private fun getPointerLabel(pointerId: Int): Int {
        val existing = pointerLabels[pointerId]
        if (existing != null) {
            return existing
        }
        val assigned = nextPointerLabel
        nextPointerLabel += 1
        onNextPointerLabelChanged(nextPointerLabel)
        pointerLabels[pointerId] = assigned
        return assigned
    }

    private fun refreshTouchDebug() {
        if (activePointerIds.isEmpty()) {
            onActiveTouchDebugChanged("")
            return
        }
        val debug = activePointerIds
            .sortedBy { getPointerLabel(it) }
            .joinToString(separator = "\n") { pointerId ->
                val label = getPointerLabel(pointerId)
                val pos = activePointerDevicePositions[pointerId]
                if (pos == null) {
                    "#$label(id=$pointerId):?"
                } else {
                    "#$label(id=$pointerId):${pos.first},${pos.second}"
                }
            }
        onActiveTouchDebugChanged(debug)
    }

    private fun releasePointer(pointerId: Int, bounds: ContentBounds) {
        if (!activePointerIds.contains(pointerId)) return
        val pos = activePointerPositions[pointerId] ?: Offset.Zero
        val (x, y) = mapToDevice(pos.x, pos.y, bounds)
        coroutineScope.launch {
            runCatching {
                onInjectTouch(UiMotionActions.UP, pointerId.toLong(), x, y, 0f, 0)
            }.onFailure { e ->
                Log.w(FULLSCREEN_TOUCH_LOG_TAG, "releasePointer failed for pointerId=$pointerId", e)
            }
        }
        activePointerIds -= pointerId
        activePointerPositions.remove(pointerId)
        activePointerDevicePositions.remove(pointerId)
        pointerLabels.remove(pointerId)
    }

    private fun handleCancelAction(bounds: ContentBounds): Boolean {
        val toCancel = activePointerIds.toList()
        for (pointerId in toCancel) {
            releasePointer(pointerId, bounds)
        }
        onActiveTouchCountChanged(activePointerIds.size)
        refreshTouchDebug()
        return true
    }

    private fun extractEventData(event: MotionEvent) {
        eventPointerIds.clear()
        eventPositions.clear()
        eventPressures.clear()
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            eventPointerIds += pointerId
            eventPositions[pointerId] = Offset(event.getX(i), event.getY(i))
            eventPressures[pointerId] = event.getPressure(i).coerceIn(0f, 1f)
        }
    }

    private fun handleDisappearedPointers(eventPointerIds: Set<Int>, bounds: ContentBounds) {
        val disappearedPointers = activePointerIds.filter { it !in eventPointerIds }
        for (pointerId in disappearedPointers) {
            releasePointer(pointerId, bounds)
        }
    }

    private fun getEndedPointerId(event: MotionEvent): Int? {
        return when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> event.getPointerId(event.actionIndex)
            else -> null
        }
    }

    private fun handlePointerDown(
        event: MotionEvent,
        endedPointerId: Int?,
        bounds: ContentBounds,
    ) {
        justPressedPointerIds.clear()
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            if (pointerId == endedPointerId) continue
            val raw = eventPositions[pointerId] ?: continue
            val pressure = eventPressures[pointerId] ?: 0f
            if (!activePointerIds.contains(pointerId)) {
                if (!isInsideContent(raw.x, raw.y, bounds)) continue
                val (x, y) = mapToDevice(raw.x, raw.y, bounds)
                activePointerIds += pointerId
                activePointerPositions[pointerId] = raw
                activePointerDevicePositions[pointerId] = x to y
                justPressedPointerIds += pointerId
                coroutineScope.launch {
                    runCatching {
                        onInjectTouch(UiMotionActions.DOWN, pointerId.toLong(), x, y, pressure, 0)
                    }.onFailure { e ->
                        Log.w(
                            FULLSCREEN_TOUCH_LOG_TAG,
                            "handlePointerDown failed for pointerId=$pointerId",
                            e
                        )
                    }
                }
            }
        }
    }

    private fun handlePointerMove(
        event: MotionEvent,
        endedPointerId: Int?,
        bounds: ContentBounds,
    ) {
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            if (!activePointerIds.contains(pointerId)) continue
            if (pointerId == endedPointerId) continue
            if (pointerId in justPressedPointerIds) continue
            val raw = eventPositions[pointerId] ?: continue
            val pressure = eventPressures[pointerId] ?: 0f
            activePointerPositions[pointerId] = raw
            val (x, y) = mapToDevice(raw.x, raw.y, bounds)
            activePointerDevicePositions[pointerId] = x to y
            coroutineScope.launch {
                runCatching {
                    onInjectTouch(UiMotionActions.MOVE, pointerId.toLong(), x, y, pressure, 0)
                }.onFailure { e ->
                    Log.w(
                        FULLSCREEN_TOUCH_LOG_TAG,
                        "handlePointerMove failed for pointerId=$pointerId",
                        e
                    )
                }
            }
        }
    }

    private fun handlePointerUp(
        endedPointerId: Int?,
        bounds: ContentBounds,
    ) {
        if (endedPointerId != null) {
            val endPos = eventPositions[endedPointerId]
            if (endPos != null) {
                activePointerPositions[endedPointerId] = endPos
            }
            releasePointer(endedPointerId, bounds)
        }
    }

}
