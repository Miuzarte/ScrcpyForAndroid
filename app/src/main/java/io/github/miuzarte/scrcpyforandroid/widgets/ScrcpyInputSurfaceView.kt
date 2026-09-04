package io.github.miuzarte.scrcpyforandroid.widgets

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class ScrcpyInputSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
): SurfaceView(context, attrs, defStyleAttr) {
    interface InputCallbacks {
        fun handleKeyEvent(event: KeyEvent): Boolean
        fun handleCommitText(text: CharSequence): Boolean
        fun handleDeleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean
    }

    var inputCallbacks: InputCallbacks? = null

    // Gamepad (SOURCE_GAMEPAD) key / generic-motion forwarding. Return true to consume.
    var onGamepadKeyEvent: ((KeyEvent) -> Boolean)? = null
    var onGamepadMotionEvent: ((MotionEvent) -> Boolean)? = null

    private var commitTextEnabled = false
    private var gamepadCaptureEnabled = false

    fun setCommitTextEnabled(enabled: Boolean) {
        commitTextEnabled = enabled
        updateFocusability()
        if (enabled) requestFocus()
        else if (!gamepadCaptureEnabled) clearFocus()
    }

    /** Enable/disable gamepad event capture, requesting focus so SOURCE_GAMEPAD events reach this view. */
    fun setGamepadCaptureEnabled(enabled: Boolean) {
        gamepadCaptureEnabled = enabled
        updateFocusability()
        if (enabled) requestFocus()
        else if (!commitTextEnabled) clearFocus()
    }

    private fun updateFocusability() {
        isFocusable = commitTextEnabled || gamepadCaptureEnabled
        isFocusableInTouchMode = commitTextEnabled || gamepadCaptureEnabled
    }

    override fun onCheckIsTextEditor(): Boolean {
        return commitTextEnabled || super.onCheckIsTextEditor()
    }

    /**
     * Whether the event came from a game controller. We key off the INPUT DEVICE's
     * capabilities rather than the event source, because Android emits gamepad
     * d-pad/axes/key events with varying sources (SOURCE_GAMEPAD, SOURCE_JOYSTICK,
     * or KEYCODE_DPAD_* as plain key events).
     */
    private fun isGameController(deviceId: Int): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) != 0 ||
                (sources and InputDevice.SOURCE_JOYSTICK) != 0
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (isGameController(event.deviceId)) {
            // Consume all controller motion events (sticks/triggers/d-pad) so they
            // never fall through to the normal keyboard/mouse motion path.
            onGamepadMotionEvent?.invoke(event)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isGameController(event.deviceId)) {
            // Consume all controller key events (including DPAD and any unmapped
            // buttons) so they are forwarded as gamepad input instead of being
            // injected as keyboard keys.
            onGamepadKeyEvent?.invoke(event)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isGameController(event.deviceId)) {
            onGamepadKeyEvent?.invoke(event)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (isGameController(event.deviceId)) {
            onGamepadKeyEvent?.invoke(event)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyPreIme(keyCode, event)
        if (inputCallbacks?.handleKeyEvent(event) == true) return true
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!commitTextEnabled) return super.onCreateInputConnection(outAttrs)

        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object: BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                if (inputCallbacks?.handleCommitText(text) == true) return true
                return super.commitText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (inputCallbacks?.handleDeleteSurroundingText(beforeLength, afterLength) == true)
                    return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (isGameController(event.deviceId)) {
                    onGamepadKeyEvent?.invoke(event)
                    return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.sendKeyEvent(event)
                if (inputCallbacks?.handleKeyEvent(event) == true) return true
                return super.sendKeyEvent(event)
            }
        }
    }
}
