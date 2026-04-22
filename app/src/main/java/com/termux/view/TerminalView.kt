package com.termux.view

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Scroller

import androidx.annotation.RequiresApi

import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.textselection.TextSelectionCursorController

/** View displaying and interacting with a [TerminalSession]. */
class TerminalView(context: Context, attributes: AttributeSet?) : View(context, attributes) {

    /** The currently displayed terminal session, whose emulator is [mEmulator]. */
    @JvmField
    var mTermSession: TerminalSession? = null

    /** Our terminal emulator whose session is [mTermSession]. */
    @JvmField
    var mEmulator: TerminalEmulator? = null

    @JvmField
    var mRenderer: TerminalRenderer? = null

    @JvmField
    var mClient: TerminalViewClient? = null

    private var mTextSelectionCursorController: TextSelectionCursorController? = null

    private var mTerminalCursorBlinkerHandler: Handler? = null
    private var mTerminalCursorBlinkerRunnable: TerminalCursorBlinkerRunnable? = null
    private var mTerminalCursorBlinkerRate: Int = 0
    private var mCursorInvisibleIgnoreOnce: Boolean = false

    /** The top row of text to display. Ranges from -activeTranscriptRows to 0. */
    @JvmField
    var mTopRow: Int = 0

    @JvmField
    var mDefaultSelectors: IntArray = intArrayOf(-1, -1, -1, -1)

    @JvmField
    var mScaleFactor: Float = 1f

    internal lateinit var mGestureRecognizer: GestureAndScaleRecognizer

    /** Keep track of where mouse touch event started which we report as mouse scroll. */
    private var mMouseScrollStartX: Int = -1
    private var mMouseScrollStartY: Int = -1

    /** Keep track of the time when a touch event leading to sending mouse scroll events started. */
    private var mMouseStartDownTime: Long = -1

    lateinit var mScroller: Scroller

    /** What was left in from scrolling movement. */
    @JvmField
    var mScrollRemainder: Float = 0f

    /** If non-zero, this is the last unicode code point received if that was a combining character. */
    @JvmField
    var mCombiningAccent: Int = 0

    /**
     * The current AutoFill type returned for [View.getAutofillType] by [getAutofillType].
     *
     * The default is [AUTOFILL_TYPE_NONE] so that AutoFill UI, like toolbar above keyboard
     * is not shown automatically, like on Activity starts/View create.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private var mAutoFillType: Int = AUTOFILL_TYPE_NONE

    /**
     * The current AutoFill type returned for [View.getImportantForAutofill] by [getImportantForAutofill].
     *
     * The default is [IMPORTANT_FOR_AUTOFILL_NO] so that view is not considered important for AutoFill.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private var mAutoFillImportance: Int = IMPORTANT_FOR_AUTOFILL_NO

    /**
     * The current AutoFill hints returned for [View.getAutofillHints] by [getAutofillHints].
     */
    private var mAutoFillHints: Array<String> = emptyArray()

    private val mAccessibilityEnabled: Boolean

    init {
        mGestureRecognizer = GestureAndScaleRecognizer(context, object : GestureAndScaleRecognizer.Listener {
            var scrolledWithFinger = false

            override fun onUp(event: MotionEvent): Boolean {
                mScrollRemainder = 0.0f
                if (mEmulator != null && mEmulator!!.isMouseTrackingActive() && !event.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText && !scrolledWithFinger) {
                    // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                    // for zooming.
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, true)
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false)
                    return true
                }
                scrolledWithFinger = false
                return false
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                if (mEmulator == null) return true

                if (isSelectingText) {
                    stopTextSelectionMode()
                    return true
                }
                requestFocus()
                mClient?.onSingleTapUp(event)
                return true
            }

            override fun onScroll(e: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (mEmulator == null) return true
                if (mEmulator!!.isMouseTrackingActive() && e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    // If moving with mouse pointer while pressing button, report that instead of scroll.
                    sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true)
                } else {
                    scrolledWithFinger = true
                    val adjustedDistanceY = distanceY + mScrollRemainder
                    val deltaRows = (adjustedDistanceY / mRenderer!!.mFontLineSpacing).toInt()
                    mScrollRemainder = adjustedDistanceY - deltaRows * mRenderer!!.mFontLineSpacing
                    doScroll(e, deltaRows)
                }
                return true
            }

            override fun onScale(focusX: Float, focusY: Float, scale: Float): Boolean {
                if (mEmulator == null || isSelectingText) return true
                mScaleFactor *= scale
                mScaleFactor = mClient!!.onScale(mScaleFactor)
                return true
            }

            override fun onFling(e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (mEmulator == null) return true
                // Do not start scrolling until last fling has been taken care of:
                if (!mScroller.isFinished) return true

                val mouseTrackingAtStartOfFling = mEmulator!!.isMouseTrackingActive()
                val SCALE = 0.25f
                if (mouseTrackingAtStartOfFling) {
                    mScroller.fling(0, 0, 0, -(velocityY * SCALE).toInt(), 0, 0, -mEmulator!!.mRows / 2, mEmulator!!.mRows / 2)
                } else {
                    mScroller.fling(0, mTopRow, 0, -(velocityY * SCALE).toInt(), 0, 0, -mEmulator!!.getScreen().activeTranscriptRows, 0)
                }

                post(object : Runnable {
                    private var mLastY = 0

                    override fun run() {
                        if (mouseTrackingAtStartOfFling != mEmulator!!.isMouseTrackingActive()) {
                            mScroller.abortAnimation()
                            return
                        }
                        if (mScroller.isFinished) return
                        val more = mScroller.computeScrollOffset()
                        val newY = mScroller.currY
                        val diff = if (mouseTrackingAtStartOfFling) (newY - mLastY) else (newY - mTopRow)
                        doScroll(e2, diff)
                        mLastY = newY
                        if (more) post(this)
                    }
                })

                return true
            }

            override fun onDown(x: Float, y: Float): Boolean {
                return false
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                // Do not treat is as a single confirmed tap - it may be followed by zoom.
                return false
            }

            override fun onLongPress(event: MotionEvent) {
                if (mGestureRecognizer.isInProgress()) return
                if (mClient?.onLongPress(event) == true) return
                if (!isSelectingText) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    startTextSelectionMode(event)
                }
            }
        })
        mScroller = Scroller(context)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        mAccessibilityEnabled = am.isEnabled
    }

    /**
     * @param client The [TerminalViewClient] interface implementation to allow
     *               for communication between [TerminalView] and its client.
     */
    fun setTerminalViewClient(client: TerminalViewClient) {
        this.mClient = client
    }

    /**
     * Sets whether terminal view key logging is enabled or not.
     *
     * @param value The boolean value that defines the state.
     */
    fun setIsTerminalViewKeyLoggingEnabled(value: Boolean) {
        TERMINAL_VIEW_KEY_LOGGING_ENABLED = value
    }

    /**
     * Attach a [TerminalSession] to this view.
     *
     * @param session The [TerminalSession] this view will be displaying.
     */
    fun attachSession(session: TerminalSession): Boolean {
        if (session == mTermSession) return false
        mTopRow = 0

        mTermSession = session
        mEmulator = null
        mCombiningAccent = 0

        updateSize()

        // Wait with enabling the scrollbar until we have a terminal to get scroll position from.
        isVerticalScrollBarEnabled = true

        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Ensure that inputType is only set if TerminalView is selected view with the keyboard and
        // an alternate view is not selected, like an EditText.
        if (mClient!!.isTerminalViewSelected()) {
            if (mClient!!.shouldEnforceCharBasedInput()) {
                // Some keyboards seems do not reset the internal state on TYPE_NULL.
                outAttrs.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } else {
                // Using InputType.NULL is the most correct input type and avoids issues with other hacks.
                outAttrs.inputType = InputType.TYPE_NULL
            }
        } else {
            // Corresponds to android:inputType="text"
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }

        // Note that IME_ACTION_NONE cannot be used as that makes it impossible to input newlines using the on-screen
        // keyboard on Android TV.
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {

            override fun finishComposingText(): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient!!.logInfo(LOG_TAG, "IME: finishComposingText()")
                super.finishComposingText()

                sendTextToTerminal(editable!!)
                editable!!.clear()
                return true
            }

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient!!.logInfo(LOG_TAG, "IME: commitText(\"$text\", $newCursorPosition)")
                }
                super.commitText(text, newCursorPosition)

                if (mEmulator == null) return true

                val content = editable!!
                sendTextToTerminal(content)
                content.clear()
                return true
            }

            override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient!!.logInfo(LOG_TAG, "IME: deleteSurroundingText($leftLength, $rightLength)")
                }
                // The stock Samsung keyboard with 'Auto check spelling' enabled sends leftLength > 1.
                val deleteKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                for (i in 0 until leftLength) sendKeyEvent(deleteKey)
                return super.deleteSurroundingText(leftLength, rightLength)
            }

            fun sendTextToTerminal(text: CharSequence) {
                stopTextSelectionMode()
                val textLengthInChars = text.length
                var i = 0
                while (i < textLengthInChars) {
                    val firstChar = text[i]
                    var codePoint: Int
                    if (Character.isHighSurrogate(firstChar)) {
                        if (++i < textLengthInChars) {
                            codePoint = Character.toCodePoint(firstChar, text[i])
                        } else {
                            // At end of string, with no low surrogate following the high:
                            codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR
                        }
                    } else {
                        codePoint = firstChar.code
                    }

                    // Check onKeyDown() for details.
                    if (mClient!!.readShiftKey())
                        codePoint = Character.toUpperCase(codePoint)

                    var ctrlHeld = false
                    if (codePoint <= 31 && codePoint != 27) {
                        if (codePoint == '\n'.code) {
                            // The AOSP keyboard and descendants seems to send \n as text when the enter key is pressed,
                            // instead of a key event like most other keyboard apps. A terminal expects \r for the enter
                            // key.
                            codePoint = '\r'.code
                        }

                        // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true
                        codePoint = when (codePoint) {
                            31 -> '_'.code
                            30 -> '^'.code
                            29 -> ']'.code
                            28 -> '\\'.code
                            else -> codePoint + 96
                        }
                    }

                    inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false)
                    i++
                }
            }
        }
    }

    override fun computeVerticalScrollRange(): Int {
        return mEmulator?.getScreen()?.activeRows ?: 1
    }

    override fun computeVerticalScrollExtent(): Int {
        return mEmulator?.mRows ?: 1
    }

    override fun computeVerticalScrollOffset(): Int {
        return if (mEmulator == null) 1 else mEmulator!!.getScreen().activeRows + mTopRow - mEmulator!!.mRows
    }

    fun onScreenUpdated() {
        onScreenUpdated(false)
    }

    fun onScreenUpdated(skipScrolling: Boolean) {
        var skipScroll = skipScrolling
        if (mEmulator == null) return

        val rowsInHistory = mEmulator!!.getScreen().activeTranscriptRows
        if (mTopRow < -rowsInHistory) mTopRow = -rowsInHistory

        if (isSelectingText || mEmulator!!.isAutoScrollDisabled()) {
            // Do not scroll when selecting text.
            val rowShift = mEmulator!!.getScrollCounter()
            if (-mTopRow + rowShift > rowsInHistory) {
                // .. unless we're hitting the end of history transcript, in which
                // case we abort text selection and scroll to end.
                if (isSelectingText)
                    stopTextSelectionMode()

                if (mEmulator!!.isAutoScrollDisabled()) {
                    mTopRow = -rowsInHistory
                    skipScroll = true
                }
            } else {
                skipScroll = true
                mTopRow -= rowShift
                decrementYTextSelectionCursors(rowShift)
            }
        }

        if (!skipScroll && mTopRow != 0) {
            // Scroll down if not already there.
            if (mTopRow < -3) {
                // Awaken scroll bars only if scrolling a noticeable amount
                awakenScrollBars()
            }
            mTopRow = 0
        }

        mEmulator!!.clearScrollCounter()

        invalidate()
        if (mAccessibilityEnabled) contentDescription = text
    }

    /**
     * This must be called by the hosting activity in [Activity.onContextMenuClosed]
     * when context menu for the [TerminalView] is started by
     * [TextSelectionCursorController.ACTION_MORE] is closed.
     */
    fun onContextMenuClosed(menu: Menu) {
        // Unset the stored text since it shouldn't be used anymore and should be cleared from memory
        unsetStoredSelectedText()
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param textSize the new font size, in density-independent pixels.
     */
    fun setTextSize(textSize: Int) {
        mRenderer = TerminalRenderer(textSize, mRenderer?.mTypeface ?: Typeface.MONOSPACE)
        updateSize()
    }

    fun setTypeface(newTypeface: Typeface) {
        mRenderer = TerminalRenderer(mRenderer!!.mTextSize, newTypeface)
        updateSize()
        invalidate()
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    override fun isOpaque(): Boolean {
        return true
    }

    /**
     * Get the zero indexed column and row of the terminal view for the
     * position of the event.
     *
     * @param event The event with the position to get the column and row for.
     * @param relativeToScroll If true the column number will take the scroll
     * position into account.
     * @return Array with the column and row.
     */
    fun getColumnAndRow(event: MotionEvent, relativeToScroll: Boolean): IntArray {
        val column = (event.x / mRenderer!!.mFontWidth).toInt()
        var row = ((event.y - mRenderer!!.mFontLineSpacingAndAscent) / mRenderer!!.mFontLineSpacing).toInt()
        if (relativeToScroll) {
            row += mTopRow
        }
        return intArrayOf(column, row)
    }

    /** Send a single mouse event code to the terminal. */
    fun sendMouseEventCode(e: MotionEvent, button: Int, pressed: Boolean) {
        val columnAndRow = getColumnAndRow(e, false)
        var x = columnAndRow[0] + 1
        var y = columnAndRow[1] + 1
        if (pressed && (button == TerminalEmulator.MOUSE_WHEELDOWN_BUTTON || button == TerminalEmulator.MOUSE_WHEELUP_BUTTON)) {
            if (mMouseStartDownTime == e.downTime) {
                x = mMouseScrollStartX
                y = mMouseScrollStartY
            } else {
                mMouseStartDownTime = e.downTime
                mMouseScrollStartX = x
                mMouseScrollStartY = y
            }
        }
        mEmulator!!.sendMouseEvent(button, x, y, pressed)
    }

    /** Perform a scroll, either from dragging the screen or by scrolling a mouse wheel. */
    fun doScroll(event: MotionEvent, rowsDown: Int) {
        val up = rowsDown < 0
        val amount = Math.abs(rowsDown)
        for (i in 0 until amount) {
            if (mEmulator!!.isMouseTrackingActive()) {
                sendMouseEventCode(event, if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true)
            } else if (mEmulator!!.isAlternateBufferActive()) {
                // Send up and down key events for scrolling, which is what some terminals do to make scroll work in
                // e.g. less, which shifts to the alt screen without mouse handling.
                handleKeyCode(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, 0)
            } else {
                mTopRow = Math.min(0, Math.max(-mEmulator!!.getScreen().activeTranscriptRows, mTopRow + if (up) -1 else 1))
                if (!awakenScrollBars()) invalidate()
            }
        }
    }

    /** Overriding [View.onGenericMotionEvent]. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (mEmulator != null && event.isFromSource(InputDevice.SOURCE_MOUSE) && event.action == MotionEvent.ACTION_SCROLL) {
            // Handle mouse wheel scrolling.
            val up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f
            doScroll(event, if (up) -3 else 3)
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    @TargetApi(23)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mEmulator == null) return true
        val action = event.action

        if (isSelectingText) {
            updateFloatingToolbarVisibility(event)
            mGestureRecognizer.onTouchEvent(event)
            return true
        } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                if (action == MotionEvent.ACTION_DOWN) showContextMenu()
                return true
            } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboardManager.primaryClip
                if (clipData != null) {
                    val clipItem = clipData.getItemAt(0)
                    if (clipItem != null) {
                        val text = clipItem.coerceToText(context)
                        if (!TextUtils.isEmpty(text)) mEmulator!!.paste(text.toString())
                    }
                }
            } else if (mEmulator!!.isMouseTrackingActive()) { // BUTTON_PRIMARY.
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP ->
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, event.action == MotionEvent.ACTION_DOWN)
                    MotionEvent.ACTION_MOVE ->
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true)
                }
            }
        }

        mGestureRecognizer.onTouchEvent(event)
        return true
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient!!.logInfo(LOG_TAG, "onKeyPreIme(keyCode=$keyCode, event=$event)")
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            cancelRequestAutoFill()
            if (isSelectingText) {
                stopTextSelectionMode()
                return true
            } else if (mClient!!.shouldBackButtonBeMappedToEscape()) {
                // Intercept back button to treat it as escape:
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> return onKeyDown(keyCode, event)
                    KeyEvent.ACTION_UP -> return onKeyUp(keyCode, event)
                }
            }
        } else if (mClient!!.shouldUseCtrlSpaceWorkaround() &&
            keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed) {
            /* ctrl+space does not work on some ROMs without this workaround. */
            return onKeyDown(keyCode, event)
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient!!.logInfo(LOG_TAG, "onKeyDown(keyCode=$keyCode, isSystem()=${event.isSystem}, event=$event)")
        if (mEmulator == null) return true
        if (isSelectingText) {
            stopTextSelectionMode()
        }

        if (mClient!!.onKeyDown(keyCode, event, mTermSession!!)) {
            invalidate()
            return true
        } else if (event.isSystem && (!mClient!!.shouldBackButtonBeMappedToEscape() || keyCode != KeyEvent.KEYCODE_BACK)) {
            return super.onKeyDown(keyCode, event)
        } else if (event.action == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            mTermSession!!.write(event.characters)
            return true
        } else if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
            return super.onKeyDown(keyCode, event)
        }

        val metaState = event.metaState
        val controlDown = event.isCtrlPressed || mClient!!.readControlKey()
        val leftAltDown = (metaState and KeyEvent.META_ALT_LEFT_ON) != 0 || mClient!!.readAltKey()
        val shiftDown = event.isShiftPressed || mClient!!.readShiftKey()
        val rightAltDownFromEvent = (metaState and KeyEvent.META_ALT_RIGHT_ON) != 0

        var keyMod = 0
        if (controlDown) keyMod = keyMod or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed || leftAltDown) keyMod = keyMod or KeyHandler.KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KeyHandler.KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KeyHandler.KEYMOD_NUM_LOCK
        // https://github.com/termux/termux-app/issues/731
        if (!event.isFunctionPressed && handleKeyCode(keyCode, keyMod)) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient!!.logInfo(LOG_TAG, "handleKeyCode() took key event")
            return true
        }

        // Clear Ctrl since we handle that ourselves:
        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (rightAltDownFromEvent) {
            // Let right Alt/Alt Gr be used to compose characters.
        } else {
            // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
            bitsToClear = bitsToClear or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        var effectiveMetaState = event.metaState and bitsToClear.inv()

        if (shiftDown) effectiveMetaState = effectiveMetaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (mClient!!.readFnKey()) effectiveMetaState = effectiveMetaState or KeyEvent.META_FUNCTION_ON

        var result = event.getUnicodeChar(effectiveMetaState)
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient!!.logInfo(LOG_TAG, "KeyEvent#getUnicodeChar($effectiveMetaState) returned: $result")
        if (result == 0) {
            return false
        }

        val oldCombiningAccent = mCombiningAccent
        if ((result and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            // If entered combining accent previously, write it out:
            if (mCombiningAccent != 0)
                inputCodePoint(event.deviceId, mCombiningAccent, controlDown, leftAltDown)
            mCombiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        } else {
            if (mCombiningAccent != 0) {
                val combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result)
                if (combinedChar > 0) result = combinedChar
                mCombiningAccent = 0
            }
            inputCodePoint(event.deviceId, result, controlDown, leftAltDown)
        }

        if (mCombiningAccent != oldCombiningAccent) invalidate()

        return true
    }

    fun inputCodePoint(eventSource: Int, codePoint: Int, controlDownFromEvent: Boolean, leftAltDownFromEvent: Boolean) {
        var cp = codePoint
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            mClient!!.logInfo(LOG_TAG, "inputCodePoint(eventSource=$eventSource, codePoint=$cp, controlDownFromEvent=$controlDownFromEvent, leftAltDownFromEvent=$leftAltDownFromEvent)")
        }

        if (mTermSession == null) return

        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        mEmulator?.setCursorBlinkState(true)

        val controlDown = controlDownFromEvent || mClient!!.readControlKey()
        val altDown = leftAltDownFromEvent || mClient!!.readAltKey()

        if (mClient!!.onCodePoint(cp, controlDown, mTermSession!!)) return

        if (controlDown) {
            cp = when {
                cp >= 'a'.code && cp <= 'z'.code -> cp - 'a'.code + 1
                cp >= 'A'.code && cp <= 'Z'.code -> cp - 'A'.code + 1
                cp == ' '.code || cp == '2'.code -> 0
                cp == '['.code || cp == '3'.code -> 27 // ^[ (Esc)
                cp == '\\'.code || cp == '4'.code -> 28
                cp == ']'.code || cp == '5'.code -> 29
                cp == '^'.code || cp == '6'.code -> 30 // control-^
                cp == '_'.code || cp == '7'.code || cp == '/'.code -> 31
                cp == '8'.code -> 127 // DEL
                else -> cp
            }
        }

        if (cp > -1) {
            // If not virtual or soft keyboard.
            if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
                // Work around bluetooth keyboards sending funny unicode characters instead
                // of the more normal ones from ASCII that terminal programs expect.
                cp = when (cp) {
                    0x02DC -> 0x007E // SMALL TILDE -> TILDE (~)
                    0x02CB -> 0x0060 // MODIFIER LETTER GRAVE ACCENT -> GRAVE ACCENT (`)
                    0x02C6 -> 0x005E // MODIFIER LETTER CIRCUMFLEX ACCENT -> CIRCUMFLEX ACCENT (^)
                    else -> cp
                }
            }

            // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            mTermSession!!.writeCodePoint(altDown, cp)
        }
    }

    /** Input the specified keyCode if applicable and return if the input was consumed. */
    fun handleKeyCode(keyCode: Int, keyMod: Int): Boolean {
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        mEmulator?.setCursorBlinkState(true)

        if (handleKeyCodeAction(keyCode, keyMod))
            return true

        val term = mTermSession!!.emulator
        val code = KeyHandler.getCode(keyCode, keyMod, term!!.isCursorKeysApplicationMode(), term.isKeypadApplicationMode())
            ?: return false
        mTermSession!!.write(code)
        return true
    }

    fun handleKeyCodeAction(keyCode: Int, keyMod: Int): Boolean {
        val shiftDown = (keyMod and KeyHandler.KEYMOD_SHIFT) != 0

        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                // shift+page_up and shift+page_down should scroll scrollback history instead of
                // scrolling command history or changing pages
                if (shiftDown) {
                    val time = SystemClock.uptimeMillis()
                    val motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                    doScroll(motionEvent, if (keyCode == KeyEvent.KEYCODE_PAGE_UP) -mEmulator!!.mRows else mEmulator!!.mRows)
                    motionEvent.recycle()
                    return true
                }
            }
        }

        return false
    }

    /**
     * Called when a key is released in the view.
     *
     * @param keyCode The keycode of the key which was released.
     * @param event   A [KeyEvent] describing the event.
     * @return Whether the event was handled.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient!!.logInfo(LOG_TAG, "onKeyUp(keyCode=$keyCode, event=$event)")

        // Do not return for KEYCODE_BACK and send it to the client since user may be trying
        // to exit the activity.
        if (mEmulator == null && keyCode != KeyEvent.KEYCODE_BACK) return true

        if (mClient!!.onKeyUp(keyCode, event)) {
            invalidate()
            return true
        } else if (event.isSystem) {
            // Let system key events through.
            return super.onKeyUp(keyCode, event)
        }

        return true
    }

    /**
     * This is called during layout when the size of this view has changed. If you were just added to the view
     * hierarchy, you're called with the old values of 0.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateSize()
    }

    /** Check if the terminal size in rows and columns should be updated. */
    fun updateSize() {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth == 0 || viewHeight == 0 || mTermSession == null) return

        // Set to 80 and 24 if you want to enable vttest.
        val newColumns = Math.max(4, (viewWidth / mRenderer!!.mFontWidth).toInt())
        val newRows = Math.max(4, (viewHeight - mRenderer!!.mFontLineSpacingAndAscent) / mRenderer!!.mFontLineSpacing)

        if (mEmulator == null || (newColumns != mEmulator!!.mColumns || newRows != mEmulator!!.mRows)) {
            mTermSession!!.updateSize(newColumns, newRows, mRenderer!!.mFontWidth.toInt(), mRenderer!!.mFontLineSpacing)
            mEmulator = mTermSession!!.emulator
            mClient!!.onEmulatorSet()

            // Update mTerminalCursorBlinkerRunnable inner class mEmulator on session change
            mTerminalCursorBlinkerRunnable?.setEmulator(mEmulator!!)

            mTopRow = 0
            scrollTo(0, 0)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (mEmulator == null) {
            canvas.drawColor(0XFF000000.toInt())
        } else {
            // render the terminal view and highlight any selected text
            val sel = mDefaultSelectors
            mTextSelectionCursorController?.getSelectors(sel)

            mRenderer!!.render(mEmulator!!, canvas, mTopRow, sel[0], sel[1], sel[2], sel[3])

            // render the text selection handles
            renderTextSelection()
        }
    }

    val currentSession: TerminalSession?
        get() = mTermSession

    private val text: CharSequence
        get() = mEmulator!!.getScreen().getSelectedText(0, mTopRow, mEmulator!!.mColumns, mTopRow + mEmulator!!.mRows)

    fun getCursorX(x: Float): Int {
        return (x / mRenderer!!.mFontWidth).toInt()
    }

    fun getCursorY(y: Float): Int {
        return (((y - 40) / mRenderer!!.mFontLineSpacing) + mTopRow).toInt()
    }

    fun getPointX(cx: Int): Int {
        val col = if (cx > mEmulator!!.mColumns) mEmulator!!.mColumns else cx
        return (col * mRenderer!!.mFontWidth).toInt()
    }

    fun getPointY(cy: Int): Int {
        return (cy - mTopRow) * mRenderer!!.mFontLineSpacing
    }

    fun getTopRow(): Int {
        return mTopRow
    }

    fun setTopRow(topRow: Int) {
        this.mTopRow = topRow
    }



    /**
     * Define functions required for AutoFill API
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun autofill(value: AutofillValue) {
        if (value.isText) {
            mTermSession!!.write(value.textValue.toString())
        }

        resetAutoFill()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillType(): Int {
        return mAutoFillType
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillHints(): Array<String> {
        return mAutoFillHints
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getAutofillValue(): AutofillValue {
        return AutofillValue.forText("")
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun getImportantForAutofill(): Int {
        return mAutoFillImportance
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Synchronized
    private fun resetAutoFill() {
        // Restore none type so that AutoFill UI isn't shown anymore.
        mAutoFillType = AUTOFILL_TYPE_NONE
        mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO
        mAutoFillHints = emptyArray()
    }

    fun getAutoFillManagerService(): AutofillManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        return try {
            context?.getSystemService(AutofillManager::class.java)
        } catch (e: Exception) {
            mClient!!.logStackTraceWithMessage(LOG_TAG, "Failed to get AutofillManager service", e)
            null
        }
    }

    val isAutoFillEnabled: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

            return try {
                val autofillManager = getAutoFillManagerService()
                autofillManager?.isEnabled == true
            } catch (e: Exception) {
                mClient!!.logStackTraceWithMessage(LOG_TAG, "Failed to check if Autofill is enabled", e)
                false
            }
        }

    @Synchronized
    fun requestAutoFillUsername() {
        requestAutoFill(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) arrayOf(AUTOFILL_HINT_USERNAME)
            else null
        )
    }

    @Synchronized
    fun requestAutoFillPassword() {
        requestAutoFill(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) arrayOf(AUTOFILL_HINT_PASSWORD)
            else null
        )
    }

    @Synchronized
    fun requestAutoFill(autoFillHints: Array<String>?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (autoFillHints == null || autoFillHints.isEmpty()) return

        try {
            val autofillManager = getAutoFillManagerService()
            if (autofillManager?.isEnabled == true) {
                // Update type that will be returned by `getAutofillType()` so that AutoFill UI is shown.
                mAutoFillType = AUTOFILL_TYPE_TEXT
                // Update importance that will be returned by `getImportantForAutofill()` so that
                // AutoFill considers the view as important.
                mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_YES
                // Update hints that will be returned by `getAutofillHints()` for which to show AutoFill UI.
                mAutoFillHints = autoFillHints
                autofillManager.requestAutofill(this)
            }
        } catch (e: Exception) {
            mClient!!.logStackTraceWithMessage(LOG_TAG, "Failed to request Autofill", e)
        }
    }

    @Synchronized
    fun cancelRequestAutoFill() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (mAutoFillType == AUTOFILL_TYPE_NONE) return

        try {
            val autofillManager = getAutoFillManagerService()
            if (autofillManager?.isEnabled == true) {
                resetAutoFill()
                autofillManager.cancel()
            }
        } catch (e: Exception) {
            mClient!!.logStackTraceWithMessage(LOG_TAG, "Failed to cancel Autofill request", e)
        }
    }



    /**
     * Set terminal cursor blinker rate. It must be between [TERMINAL_CURSOR_BLINK_RATE_MIN]
     * and [TERMINAL_CURSOR_BLINK_RATE_MAX], otherwise it will be disabled.
     *
     * @param blinkRate The value to set.
     * @return Returns `true` if setting blinker rate was successfully set, otherwise `false`.
     */
    @Synchronized
    fun setTerminalCursorBlinkerRate(blinkRate: Int): Boolean {
        val result: Boolean

        // If cursor blinking rate is not valid
        if (blinkRate != 0 && (blinkRate < TERMINAL_CURSOR_BLINK_RATE_MIN || blinkRate > TERMINAL_CURSOR_BLINK_RATE_MAX)) {
            mClient!!.logError(LOG_TAG, "The cursor blink rate must be in between $TERMINAL_CURSOR_BLINK_RATE_MIN-$TERMINAL_CURSOR_BLINK_RATE_MAX: $blinkRate")
            mTerminalCursorBlinkerRate = 0
            result = false
        } else {
            mClient!!.logVerbose(LOG_TAG, "Setting cursor blinker rate to $blinkRate")
            mTerminalCursorBlinkerRate = blinkRate
            result = true
        }

        if (mTerminalCursorBlinkerRate == 0) {
            mClient!!.logVerbose(LOG_TAG, "Cursor blinker disabled")
            stopTerminalCursorBlinker()
        }

        return result
    }

    /**
     * Sets whether cursor blinker should be started or stopped.
     */
    @Synchronized
    fun setTerminalCursorBlinkerState(start: Boolean, startOnlyIfCursorEnabled: Boolean) {
        // Stop any existing cursor blinker callbacks
        stopTerminalCursorBlinker()

        if (mEmulator == null) return

        mEmulator!!.setCursorBlinkingEnabled(false)

        if (start) {
            // If cursor blinker is not enabled or is not valid
            if (mTerminalCursorBlinkerRate < TERMINAL_CURSOR_BLINK_RATE_MIN || mTerminalCursorBlinkerRate > TERMINAL_CURSOR_BLINK_RATE_MAX)
                return
            // If cursor blinder is to be started only if cursor is enabled
            else if (startOnlyIfCursorEnabled && !mEmulator!!.isCursorEnabled()) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                    mClient!!.logVerbose(LOG_TAG, "Ignoring call to start cursor blinker since cursor is not enabled")
                return
            }

            // Start cursor blinker runnable
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                mClient!!.logVerbose(LOG_TAG, "Starting cursor blinker with the blink rate $mTerminalCursorBlinkerRate")
            if (mTerminalCursorBlinkerHandler == null)
                mTerminalCursorBlinkerHandler = Handler(Looper.getMainLooper())
            mTerminalCursorBlinkerRunnable = TerminalCursorBlinkerRunnable(mEmulator!!, mTerminalCursorBlinkerRate)
            mEmulator!!.setCursorBlinkingEnabled(true)
            mTerminalCursorBlinkerRunnable!!.run()
        }
    }

    /**
     * Cancel the terminal cursor blinker callbacks
     */
    private fun stopTerminalCursorBlinker() {
        if (mTerminalCursorBlinkerHandler != null && mTerminalCursorBlinkerRunnable != null) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                mClient!!.logVerbose(LOG_TAG, "Stopping cursor blinker")
            mTerminalCursorBlinkerHandler!!.removeCallbacks(mTerminalCursorBlinkerRunnable!!)
        }
    }

    private inner class TerminalCursorBlinkerRunnable(
        private var mEmulator: TerminalEmulator,
        private val mBlinkRate: Int
    ) : Runnable {

        // Initialize with false so that initial blink state is visible after toggling
        private var mCursorVisible = false

        fun setEmulator(emulator: TerminalEmulator) {
            mEmulator = emulator
        }

        override fun run() {
            try {
                // Toggle the blink state and then invalidate() the view so
                // that onDraw() is called, which then calls TerminalRenderer.render()
                // which checks with TerminalEmulator.shouldCursorBeVisible() to decide whether
                // to draw the cursor or not
                mCursorVisible = !mCursorVisible
                mEmulator.setCursorBlinkState(mCursorVisible)
                invalidate()
            } finally {
                // Recall the Runnable after mBlinkRate milliseconds to toggle the blink state
                mTerminalCursorBlinkerHandler!!.postDelayed(this, mBlinkRate.toLong())
            }
        }
    }



    /**
     * Define functions required for text selection and its handles.
     */
    fun getTextSelectionCursorController(): TextSelectionCursorController {
        if (mTextSelectionCursorController == null) {
            mTextSelectionCursorController = TextSelectionCursorController(this)

            val observer = viewTreeObserver
            observer?.addOnTouchModeChangeListener(mTextSelectionCursorController)
        }

        return mTextSelectionCursorController!!
    }

    private fun showTextSelectionCursors(event: MotionEvent) {
        getTextSelectionCursorController().show(event)
    }

    private fun hideTextSelectionCursors(): Boolean {
        return getTextSelectionCursorController().hide()
    }

    private fun renderTextSelection() {
        mTextSelectionCursorController?.render()
    }

    val isSelectingText: Boolean
        get() = mTextSelectionCursorController?.isActive() == true

    /** Get the currently selected text if selecting. */
    val selectedText: String?
        get() = if (isSelectingText && mTextSelectionCursorController != null)
            mTextSelectionCursorController!!.getSelectedText()
        else
            null

    /** Get the selected text stored before "MORE" button was pressed on the context menu. */
    val storedSelectedText: String?
        get() = mTextSelectionCursorController?.storedSelectedText

    /** Unset the selected text stored before "MORE" button was pressed on the context menu. */
    fun unsetStoredSelectedText() {
        mTextSelectionCursorController?.unsetStoredSelectedText()
    }

    private val textSelectionActionMode: ActionMode?
        get() = mTextSelectionCursorController?.getActionMode()

    fun startTextSelectionMode(event: MotionEvent) {
        if (!requestFocus()) {
            return
        }

        showTextSelectionCursors(event)
        mClient!!.copyModeChanged(isSelectingText)

        invalidate()
    }

    fun stopTextSelectionMode() {
        if (hideTextSelectionCursors()) {
            mClient!!.copyModeChanged(isSelectingText)
            invalidate()
        }
    }

    private fun decrementYTextSelectionCursors(decrement: Int) {
        mTextSelectionCursorController?.decrementYTextSelectionCursors(decrement)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (mTextSelectionCursorController != null) {
            viewTreeObserver.addOnTouchModeChangeListener(mTextSelectionCursorController)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (mTextSelectionCursorController != null) {
            // Might solve the following exception
            // android.view.WindowLeaked: Activity com.termux.app.TermuxActivity has leaked window android.widget.PopupWindow
            stopTextSelectionMode()

            viewTreeObserver.removeOnTouchModeChangeListener(mTextSelectionCursorController)
            mTextSelectionCursorController!!.onDetached()
        }
    }



    /**
     * Define functions required for long hold toolbar.
     */
    private val mShowFloatingToolbar = Runnable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textSelectionActionMode?.hide(0) // hide off.
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun showFloatingToolbar() {
        if (textSelectionActionMode != null) {
            val delay = ViewConfiguration.getDoubleTapTimeout()
            postDelayed(mShowFloatingToolbar, delay.toLong())
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun hideFloatingToolbar() {
        if (textSelectionActionMode != null) {
            removeCallbacks(mShowFloatingToolbar)
            textSelectionActionMode!!.hide(-1)
        }
    }

    fun updateFloatingToolbarVisibility(event: MotionEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && textSelectionActionMode != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> hideFloatingToolbar()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showFloatingToolbar()
            }
        }
    }

    companion object {
        /** Log terminal view key and IME events. */
        private var TERMINAL_VIEW_KEY_LOGGING_ENABLED = false

        const val TERMINAL_CURSOR_BLINK_RATE_MIN = 100
        const val TERMINAL_CURSOR_BLINK_RATE_MAX = 2000

        /** The [KeyEvent] is generated from a virtual keyboard, like manually with the [KeyEvent] constructor. */
        const val KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD = KeyCharacterMap.VIRTUAL_KEYBOARD // -1

        /** The [KeyEvent] is generated from a non-physical device, like if 0 value is returned by [KeyEvent.getDeviceId]. */
        const val KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0

        private const val LOG_TAG = "TerminalView"
    }
}
