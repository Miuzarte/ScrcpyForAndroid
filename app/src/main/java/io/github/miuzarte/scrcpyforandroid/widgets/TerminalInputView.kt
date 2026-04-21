package io.github.miuzarte.scrcpyforandroid.widgets

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class TerminalInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    interface InputCallbacks {
        fun handleCommitText(text: CharSequence): Boolean
        fun handleDeleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean
        fun handleKeyEvent(event: KeyEvent): Boolean
    }

    var inputCallbacks: InputCallbacks? = null
    private var inputEnabled = true

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
        isFocusable = enabled
        isFocusableInTouchMode = enabled
        if (!enabled) {
            clearFocus()
        }
    }

    override fun onCheckIsTextEditor(): Boolean {
        return inputEnabled || super.onCheckIsTextEditor()
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (inputCallbacks?.handleKeyEvent(event) == true) return true
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!inputEnabled) return super.onCreateInputConnection(outAttrs)

        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                if (inputCallbacks?.handleCommitText(text) == true) return true
                return super.commitText(text, newCursorPosition)
            }

            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                return true
            }

            override fun finishComposingText(): Boolean {
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (inputCallbacks?.handleDeleteSurroundingText(beforeLength, afterLength) == true) {
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (inputCallbacks?.handleKeyEvent(event) == true) return true
                return super.sendKeyEvent(event)
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                return when (actionCode) {
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_SEND,
                        -> inputCallbacks?.handleCommitText("\n") == true

                    else -> super.performEditorAction(actionCode)
                }
            }
        }
    }
}
