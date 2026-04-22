package com.termux.view.textselection

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow

import io.github.miuzarte.scrcpyforandroid.R
import com.termux.view.TerminalView
import com.termux.view.support.PopupWindowCompatGingerbread

@SuppressLint("ViewConstructor")
class TextSelectionHandleView(
    private val terminalView: TerminalView,
    private val mCursorController: CursorController,
    private val mInitialOrientation: Int
) : View(terminalView.context) {

    private var mHandle: PopupWindow? = null

    private val mHandleLeftDrawable: Drawable?
    private val mHandleRightDrawable: Drawable?
    private var mHandleDrawable: Drawable? = null

    private var mIsDragging = false

    val mTempCoords = IntArray(2)
    var mTempRect: Rect? = null

    private var mPointX = 0
    private var mPointY = 0
    private var mTouchToWindowOffsetX = 0f
    private var mTouchToWindowOffsetY = 0f
    private var mHotspotX = 0f
    private var mHotspotY = 0f
    private var mTouchOffsetY = 0f
    private var mLastParentX = 0
    private var mLastParentY = 0

    private var mHandleHeight = 0
    private var mHandleWidth = 0

    private var mOrientation = 0

    private var mLastTime: Long = 0

    init {
        mHandleLeftDrawable = context.getDrawable(R.drawable.text_select_handle_left_material)
        mHandleRightDrawable = context.getDrawable(R.drawable.text_select_handle_right_material)

        setOrientation(mInitialOrientation)
    }

    private fun initHandle() {
        mHandle = PopupWindow(terminalView.context, null,
            android.R.attr.textSelectHandleWindowStyle)
        mHandle?.setSplitTouchEnabled(true)
        mHandle?.isClippingEnabled = false
        mHandle?.width = ViewGroup.LayoutParams.WRAP_CONTENT
        mHandle?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        mHandle?.setBackgroundDrawable(null)
        mHandle?.animationStyle = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mHandle?.windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            mHandle?.enterTransition = null
            mHandle?.exitTransition = null
        } else {
            mHandle?.let { PopupWindowCompatGingerbread.setWindowLayoutType(it, WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL) }
        }
        mHandle?.contentView = this
    }

    fun setOrientation(orientation: Int) {
        mOrientation = orientation
        var handleWidth = 0
        when (orientation) {
            LEFT -> {
                mHandleDrawable = mHandleLeftDrawable
                handleWidth = mHandleDrawable?.intrinsicWidth ?: 0
                mHotspotX = (handleWidth * 3) / 4f
            }
            RIGHT -> {
                mHandleDrawable = mHandleRightDrawable
                handleWidth = mHandleDrawable?.intrinsicWidth ?: 0
                mHotspotX = handleWidth / 4f
            }
        }

        mHandleHeight = mHandleDrawable?.intrinsicHeight ?: 0

        mHandleWidth = handleWidth
        mTouchOffsetY = -mHandleHeight * 0.3f
        mHotspotY = 0f
        invalidate()
    }

    fun show() {
        if (!isPositionVisible()) {
            hide()
            return
        }

        // We remove handle from its parent first otherwise the following exception may be thrown
        // java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
        removeFromParent()

        initHandle() // init the handle
        invalidate() // invalidate to make sure onDraw is called

        val coords = mTempCoords
        terminalView.getLocationInWindow(coords)
        coords[0] += mPointX
        coords[1] += mPointY

        mHandle?.showAtLocation(terminalView, 0, coords[0], coords[1])
    }

    fun hide() {
        mIsDragging = false

        mHandle?.let { handle ->
            handle.dismiss()

            // We remove handle from its parent, otherwise it may still be shown in some cases even after the dismiss call
            removeFromParent()
            mHandle = null  // garbage collect the handle
        }
        invalidate()
    }

    fun removeFromParent() {
        if (!isParentNull) {
            (this.parent as ViewGroup).removeView(this)
        }
    }

    fun positionAtCursor(cx: Int, cy: Int, forceOrientationCheck: Boolean) {
        val x = terminalView.getPointX(cx)
        val y = terminalView.getPointY(cy + 1)
        moveTo(x, y, forceOrientationCheck)
    }

    private fun moveTo(x: Int, y: Int, forceOrientationCheck: Boolean) {
        val oldHotspotX = mHotspotX
        checkChangedOrientation(x, forceOrientationCheck)
        mPointX = (x - (if (isShowing) oldHotspotX else mHotspotX)).toInt()
        mPointY = y

        if (isPositionVisible()) {
            var coords: IntArray? = null

            if (isShowing) {
                coords = mTempCoords
                terminalView.getLocationInWindow(coords)
                val x1 = coords[0] + mPointX
                val y1 = coords[1] + mPointY
                mHandle?.update(x1, y1, width, height)
            } else {
                show()
            }

            if (mIsDragging) {
                if (coords == null) {
                    coords = mTempCoords
                    terminalView.getLocationInWindow(coords)
                }
                if (coords[0] != mLastParentX || coords[1] != mLastParentY) {
                    mTouchToWindowOffsetX += coords[0] - mLastParentX
                    mTouchToWindowOffsetY += coords[1] - mLastParentY
                    mLastParentX = coords[0]
                    mLastParentY = coords[1]
                }
            }
        } else {
            hide()
        }
    }

    fun changeOrientation(orientation: Int) {
        if (mOrientation != orientation) {
            setOrientation(orientation)
        }
    }

    private fun checkChangedOrientation(posX: Int, force: Boolean) {
        if (!mIsDragging && !force) {
            return
        }
        val millis = SystemClock.currentThreadTimeMillis()
        if (millis - mLastTime < 50 && !force) {
            return
        }
        mLastTime = millis

        val hostView = terminalView
        val left = hostView.left
        val right = hostView.width
        val top = hostView.top
        val bottom = hostView.height

        if (mTempRect == null) {
            mTempRect = Rect()
        }
        val clip = mTempRect!!
        clip.left = left + terminalView.paddingLeft
        clip.top = top + terminalView.paddingTop
        clip.right = right - terminalView.paddingRight
        clip.bottom = bottom - terminalView.paddingBottom

        val parent = hostView.parent
        if (parent == null || !parent.getChildVisibleRect(hostView, clip, null)) {
            return
        }

        if (posX - mHandleWidth < clip.left) {
            changeOrientation(RIGHT)
        } else if (posX + mHandleWidth > clip.right) {
            changeOrientation(LEFT)
        } else {
            changeOrientation(mInitialOrientation)
        }
    }

    private fun isPositionVisible(): Boolean {
        // Always show a dragging handle.
        if (mIsDragging) {
            return true
        }

        val hostView = terminalView
        val left = 0
        val right = hostView.width
        val top = 0
        val bottom = hostView.height

        if (mTempRect == null) {
            mTempRect = Rect()
        }
        val clip = mTempRect!!
        clip.left = left + terminalView.paddingLeft
        clip.top = top + terminalView.paddingTop
        clip.right = right - terminalView.paddingRight
        clip.bottom = bottom - terminalView.paddingBottom

        val parent = hostView.parent
        if (parent == null || !parent.getChildVisibleRect(hostView, clip, null)) {
            return false
        }

        val coords = mTempCoords
        hostView.getLocationInWindow(coords)
        val posX = coords[0] + mPointX + mHotspotX.toInt()
        val posY = coords[1] + mPointY + mHotspotY.toInt()

        return posX >= clip.left && posX <= clip.right &&
            posY >= clip.top && posY <= clip.bottom
    }

    override fun onDraw(c: Canvas) {
        val width = mHandleDrawable?.intrinsicWidth ?: 0
        val height = mHandleDrawable?.intrinsicHeight ?: 0
        mHandleDrawable?.setBounds(0, 0, width, height)
        mHandleDrawable?.draw(c)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        terminalView.updateFloatingToolbarVisibility(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val rawX = event.rawX
                val rawY = event.rawY
                mTouchToWindowOffsetX = rawX - mPointX
                mTouchToWindowOffsetY = rawY - mPointY
                val coords = mTempCoords
                terminalView.getLocationInWindow(coords)
                mLastParentX = coords[0]
                mLastParentY = coords[1]
                mIsDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                val rawX = event.rawX
                val rawY = event.rawY

                val newPosX = rawX - mTouchToWindowOffsetX + mHotspotX
                val newPosY = rawY - mTouchToWindowOffsetY + mHotspotY + mTouchOffsetY

                mCursorController.updatePosition(this, Math.round(newPosX), Math.round(newPosY))
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> mIsDragging = false
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(mHandleDrawable?.intrinsicWidth ?: 0,
            mHandleDrawable?.intrinsicHeight ?: 0)
    }

    val handleHeight: Int
        get() = mHandleHeight

    val handleWidth: Int
        get() = mHandleWidth

    val isShowing: Boolean
        get() = mHandle?.isShowing == true

    val isParentNull: Boolean
        get() = this.parent == null

    val isDragging: Boolean
        get() = mIsDragging

    companion object {
        const val LEFT = 0
        const val RIGHT = 2
    }
}
