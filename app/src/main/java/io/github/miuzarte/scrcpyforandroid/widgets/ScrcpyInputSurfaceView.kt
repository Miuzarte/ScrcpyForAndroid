package io.github.miuzarte.scrcpyforandroid.widgets

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class ScrcpyInputSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SurfaceView(context, attrs, defStyleAttr) {
    interface InputCallbacks {
        fun handleKeyEvent(event: KeyEvent): Boolean
        fun handleCommitText(text: CharSequence): Boolean
        fun handleDeleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean
    }

    var inputCallbacks: InputCallbacks? = null
    private var commitTextEnabled = false

    fun setCommitTextEnabled(enabled: Boolean) {
        commitTextEnabled = enabled
        isFocusable = enabled
        isFocusableInTouchMode = enabled
        if (enabled) requestFocus()
        else clearFocus()
    }

    override fun onCheckIsTextEditor(): Boolean {
        return commitTextEnabled || super.onCheckIsTextEditor()
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyPreIme(keyCode, event)
        if (inputCallbacks?.handleKeyEvent(event) == true) return true
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!commitTextEnabled) return super.onCreateInputConnection(outAttrs)

        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
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
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.sendKeyEvent(event)
                if (inputCallbacks?.handleKeyEvent(event) == true) return true
                return super.sendKeyEvent(event)
            }
        }
    }
}
