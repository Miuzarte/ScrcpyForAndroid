package com.termux.view.textselection

import android.view.MotionEvent
import android.view.ViewTreeObserver

/**
 * A CursorController instance can be used to control cursors in the text.
 * It is not used outside of [com.termux.view.TerminalView].
 */
interface CursorController : ViewTreeObserver.OnTouchModeChangeListener {

    /**
     * Show the cursors on screen. Will be drawn by [render] by a call during onDraw.
     * See also [hide].
     */
    fun show(event: MotionEvent)

    /**
     * Hide the cursors from screen.
     * See also [show].
     */
    fun hide(): Boolean

    /**
     * Render the cursors.
     */
    fun render()

    /**
     * Update the cursor positions.
     */
    fun updatePosition(handle: TextSelectionHandleView, x: Int, y: Int)

    /**
     * This method is called by [onTouchEvent] and gives the cursors
     * a chance to become active and/or visible.
     *
     * @param event The touch event
     */
    fun onTouchEvent(event: MotionEvent): Boolean

    /**
     * Called when the view is detached from window. Perform house keeping task, such as
     * stopping Runnable thread that would otherwise keep a reference on the context, thus
     * preventing the activity to be recycled.
     */
    fun onDetached()

    /**
     * @return true if the cursors are currently active.
     */
    fun isActive(): Boolean
}
