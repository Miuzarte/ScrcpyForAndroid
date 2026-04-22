package com.termux.terminal

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Locale
import java.util.Objects
import java.util.Stack

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 *
 * References:
 * - http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
 * - http://en.wikipedia.org/wiki/ANSI_escape_code
 * - http://man.he.net/man4/console_codes
 * - http://bazaar.launchpad.net/~leonerd/libvterm/trunk/view/head:/src/state.c
 * - http://www.columbia.edu/~kermit/k95manual/iso2022.html
 * - http://www.vt100.net/docs/vt510-rm/chapter4
 * - http://en.wikipedia.org/wiki/ISO/IEC_2022 - for 7-bit and 8-bit GL GR explanation
 * - http://bjh21.me.uk/all-escapes/all-escapes.txt - extensive!
 * - http://woldlab.caltech.edu/~diane/kde4.10/workingdir/kubuntu/konsole/doc/developer/old-documents/VT100/techref.html
 */
class TerminalEmulator(
    private val mSession: TerminalOutput,
    columns: Int,
    rows: Int,
    cellWidthPixels: Int,
    cellHeightPixels: Int,
    transcriptRows: Int?,
    client: TerminalSessionClient?
) {
    companion object {
        /** Log unknown or unimplemented escape sequences received from the shell process. */
        private const val LOG_ESCAPE_SEQUENCES = false

        @JvmField val MOUSE_LEFT_BUTTON = 0
        /** Mouse moving while having left mouse button pressed. */
        @JvmField val MOUSE_LEFT_BUTTON_MOVED = 32
        @JvmField val MOUSE_WHEELUP_BUTTON = 64
        @JvmField val MOUSE_WHEELDOWN_BUTTON = 65

        /** Used for invalid data - http://en.wikipedia.org/wiki/Replacement_character#Replacement_character */
        @JvmField val UNICODE_REPLACEMENT_CHAR = 0xFFFD

        /** Escape processing: Not currently in an escape sequence. */
        private const val ESC_NONE = 0
        /** Escape processing: Have seen an ESC character - proceed to [doEsc] */
        private const val ESC = 1
        /** Escape processing: Have seen ESC POUND */
        private const val ESC_POUND = 2
        /** Escape processing: Have seen ESC and a character-set-select ( char */
        private const val ESC_SELECT_LEFT_PAREN = 3
        /** Escape processing: Have seen ESC and a character-set-select ) char */
        private const val ESC_SELECT_RIGHT_PAREN = 4
        /** Escape processing: "ESC [" or CSI (Control Sequence Introducer). */
        private const val ESC_CSI = 6
        /** Escape processing: ESC [ ? */
        private const val ESC_CSI_QUESTIONMARK = 7
        /** Escape processing: ESC [ $ */
        private const val ESC_CSI_DOLLAR = 8
        /** Escape processing: ESC % */
        private const val ESC_PERCENT = 9
        /** Escape processing: ESC ] (AKA OSC - Operating System Controls) */
        private const val ESC_OSC = 10
        /** Escape processing: ESC ] (AKA OSC - Operating System Controls) ESC */
        private const val ESC_OSC_ESC = 11
        /** Escape processing: ESC [ > */
        private const val ESC_CSI_BIGGERTHAN = 12
        /** Escape procession: "ESC P" or Device Control String (DCS) */
        private const val ESC_P = 13
        /** Escape processing: CSI > */
        private const val ESC_CSI_QUESTIONMARK_ARG_DOLLAR = 14
        /** Escape processing: CSI ARGS ' ' */
        private const val ESC_CSI_ARGS_SPACE = 15
        /** Escape processing: CSI ARGS '*' */
        private const val ESC_CSI_ARGS_ASTERIX = 16
        /** Escape processing: CSI " */
        private const val ESC_CSI_DOUBLE_QUOTE = 17
        /** Escape processing: CSI ' */
        private const val ESC_CSI_SINGLE_QUOTE = 18
        /** Escape processing: CSI ! */
        private const val ESC_CSI_EXCLAMATION = 19
        /** Escape processing: "ESC _" or Application Program Command (APC). */
        private const val ESC_APC = 20
        /** Escape processing: "ESC _" or Application Program Command (APC), followed by Escape. */
        private const val ESC_APC_ESCAPE = 21
        /** Escape processing: ESC [ <parameter bytes> */
        private const val ESC_CSI_UNSUPPORTED_PARAMETER_BYTE = 22
        /** Escape processing: ESC [ <parameter bytes> <intermediate bytes> */
        private const val ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE = 23

        /** The number of parameter arguments including colon separated sub-parameters. */
        private const val MAX_ESCAPE_PARAMETERS = 32

        /** Needs to be large enough to contain reasonable OSC 52 pastes. */
        private const val MAX_OSC_STRING_LENGTH = 8192

        /** DECSET 1 - application cursor keys. */
        private const val DECSET_BIT_APPLICATION_CURSOR_KEYS = 1
        private const val DECSET_BIT_REVERSE_VIDEO = 1 shl 1
        private const val DECSET_BIT_ORIGIN_MODE = 1 shl 2
        private const val DECSET_BIT_AUTOWRAP = 1 shl 3
        /** DECSET 25 - if the cursor should be enabled, [isCursorEnabled]. */
        private const val DECSET_BIT_CURSOR_ENABLED = 1 shl 4
        private const val DECSET_BIT_APPLICATION_KEYPAD = 1 shl 5
        /** DECSET 1000 - if to report mouse press&release events. */
        private const val DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE = 1 shl 6
        /** DECSET 1002 - like 1000, but report moving mouse while pressed. */
        private const val DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT = 1 shl 7
        /** DECSET 1004 - NOT implemented. */
        private const val DECSET_BIT_SEND_FOCUS_EVENTS = 1 shl 8
        /** DECSET 1006 - SGR-like mouse protocol (the modern sane choice). */
        private const val DECSET_BIT_MOUSE_PROTOCOL_SGR = 1 shl 9
        /** DECSET 2004 - see [paste] */
        private const val DECSET_BIT_BRACKETED_PASTE_MODE = 1 shl 10
        /** Toggled with DECLRMM - http://www.vt100.net/docs/vt510-rm/DECLRMM */
        private const val DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 shl 11
        /** Not really DECSET bit... - http://www.vt100.net/docs/vt510-rm/DECSACE */
        private const val DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 shl 12

        /** The number of terminal transcript rows that can be scrolled back to. */
        @JvmField val TERMINAL_TRANSCRIPT_ROWS_MIN = 100
        @JvmField val TERMINAL_TRANSCRIPT_ROWS_MAX = 50000
        @JvmField val DEFAULT_TERMINAL_TRANSCRIPT_ROWS = 2000

        /* The supported terminal cursor styles. */
        @JvmField val TERMINAL_CURSOR_STYLE_BLOCK = 0
        @JvmField val TERMINAL_CURSOR_STYLE_UNDERLINE = 1
        @JvmField val TERMINAL_CURSOR_STYLE_BAR = 2
        @JvmField val DEFAULT_TERMINAL_CURSOR_STYLE = TERMINAL_CURSOR_STYLE_BLOCK
        @JvmField val TERMINAL_CURSOR_STYLES_LIST = arrayOf(TERMINAL_CURSOR_STYLE_BLOCK, TERMINAL_CURSOR_STYLE_UNDERLINE, TERMINAL_CURSOR_STYLE_BAR)

        private const val LOG_TAG = "TerminalEmulator"

        @JvmStatic
        fun mapDecSetBitToInternalBit(decsetBit: Int): Int {
            return when (decsetBit) {
                1 -> DECSET_BIT_APPLICATION_CURSOR_KEYS
                5 -> DECSET_BIT_REVERSE_VIDEO
                6 -> DECSET_BIT_ORIGIN_MODE
                7 -> DECSET_BIT_AUTOWRAP
                25 -> DECSET_BIT_CURSOR_ENABLED
                66 -> DECSET_BIT_APPLICATION_KEYPAD
                69 -> DECSET_BIT_LEFTRIGHT_MARGIN_MODE
                1000 -> DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE
                1002 -> DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
                1004 -> DECSET_BIT_SEND_FOCUS_EVENTS
                1006 -> DECSET_BIT_MOUSE_PROTOCOL_SGR
                2004 -> DECSET_BIT_BRACKETED_PASTE_MODE
                else -> -1
            }
        }
    }

    private var mTitle: String? = null
    private val mTitleStack = Stack<String>()

    /** The cursor position. Between (0,0) and (mRows-1, mColumns-1). */
    private var mCursorRow = 0
    private var mCursorCol = 0

    /** The number of character rows and columns in the terminal screen. */
    @JvmField var mRows: Int = rows
    @JvmField var mColumns: Int = columns

    /** Size of a terminal cell in pixels. */
    private var mCellWidthPixels: Int = cellWidthPixels
    private var mCellHeightPixels: Int = cellHeightPixels

    /** The terminal cursor styles. */
    private var mCursorStyle = DEFAULT_TERMINAL_CURSOR_STYLE

    /** The normal screen buffer. Stores the characters that appear on the screen of the emulated terminal. */
    private val mMainBuffer: TerminalBuffer

    /**
     * The alternate screen buffer, exactly as large as the display and contains no additional saved lines.
     */
    @JvmField
    val mAltBuffer: TerminalBuffer

    /** The current screen buffer, pointing at either [mMainBuffer] or [mAltBuffer]. */
    private var mScreen: TerminalBuffer

    @JvmField
    var mClient: TerminalSessionClient? = client

    /** Keeps track of the current argument of the current escape sequence. Ranges from 0 to MAX_ESCAPE_PARAMETERS-1. */
    private var mArgIndex = 0

    /** Holds the arguments of the current escape sequence. */
    private val mArgs = IntArray(MAX_ESCAPE_PARAMETERS)

    /** Holds the bit flags which arguments are sub parameters (after a colon) - bit N is set if mArgs[N] is a sub parameter. */
    private var mArgsSubParamsBitSet = 0

    /** Holds OSC and device control arguments, which can be strings. */
    private val mOSCOrDeviceControlArgs = StringBuilder()

    private var mContinueSequence = false

    /** The current state of the escape sequence state machine. One of the ESC_* constants. */
    private var mEscapeState = ESC_NONE

    private val mSavedStateMain = SavedScreenState()
    private val mSavedStateAlt = SavedScreenState()

    /** http://www.vt100.net/docs/vt102-ug/table5-15.html */
    private var mUseLineDrawingG0 = false
    private var mUseLineDrawingG1 = false
    private var mUseLineDrawingUsesG0 = true

    private var mCurrentDecSetFlags = 0
    private var mSavedDecSetFlags = 0

    private var mInsertMode = false

    /** An array of tab stops. mTabStop[i] is true if there is a tab stop set for column i. */
    private var mTabStop: BooleanArray

    private var mTopMargin = 0
    private var mBottomMargin = 0
    private var mLeftMargin = 0
    private var mRightMargin = 0

    private var mAboutToAutoWrap = false

    private var mCursorBlinkingEnabled = false
    private var mCursorBlinkState = false

    @JvmField var mForeColor = 0
    @JvmField var mBackColor = 0
    @JvmField var mUnderlineColor = 0

    /** Current [TextStyle] effect. */
    @JvmField var mEffect = 0

    private var mScrollCounter = 0

    /** If automatic scrolling of terminal is disabled */
    private var mAutoScrollDisabled = false

    private var mUtf8ToFollow: Byte = 0
    private var mUtf8Index: Byte = 0
    private val mUtf8InputBuffer = ByteArray(4)
    private var mLastEmittedCodePoint = -1

    @JvmField
    val mColors = TerminalColors()

    init {
        mScreen = TerminalBuffer(columns, getTerminalTranscriptRows(transcriptRows), rows)
        mMainBuffer = mScreen
        mAltBuffer = TerminalBuffer(columns, rows, rows)
        mTabStop = BooleanArray(mColumns)
        reset()
    }

    fun updateTerminalSessionClient(client: TerminalSessionClient?) {
        mClient = client
        setCursorStyle()
        setCursorBlinkState(true)
    }

    fun getScreen(): TerminalBuffer = mScreen

    fun isAlternateBufferActive(): Boolean = mScreen === mAltBuffer

    private fun getTerminalTranscriptRows(transcriptRows: Int?): Int {
        return if (transcriptRows == null || transcriptRows < TERMINAL_TRANSCRIPT_ROWS_MIN || transcriptRows > TERMINAL_TRANSCRIPT_ROWS_MAX)
            DEFAULT_TERMINAL_TRANSCRIPT_ROWS
        else
            transcriptRows
    }

    fun sendMouseEvent(mouseButton: Int, column: Int, row: Int, pressed: Boolean) {
        var col = column
        var r = row
        if (col < 1) col = 1
        if (col > mColumns) col = mColumns
        if (r < 1) r = 1
        if (r > mRows) r = mRows

        if (mouseButton == MOUSE_LEFT_BUTTON_MOVED && !isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) {
            // Do not send tracking.
        } else if (isDecsetInternalBitSet(DECSET_BIT_MOUSE_PROTOCOL_SGR)) {
            mSession.write(String.format("\u001b[<%d;%d;%d" + (if (pressed) 'M' else 'm'), mouseButton, col, r))
        } else {
            val button = if (pressed) mouseButton else 3
            val outOfBounds = col > 255 - 32 || r > 255 - 32
            if (!outOfBounds) {
                val data = byteArrayOf('\u001b'.code.toByte(), '['.code.toByte(), 'M'.code.toByte(), (32 + button).toByte(), (32 + col).toByte(), (32 + r).toByte())
                mSession.write(data, 0, data.size)
            }
        }
    }

    fun resize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.mCellWidthPixels = cellWidthPixels
        this.mCellHeightPixels = cellHeightPixels

        if (mRows == rows && mColumns == columns) {
            return
        } else if (columns < 2 || rows < 2) {
            throw IllegalArgumentException("rows=$rows, columns=$columns")
        }

        if (mRows != rows) {
            mRows = rows
            mTopMargin = 0
            mBottomMargin = mRows
        }
        if (mColumns != columns) {
            val oldColumns = mColumns
            mColumns = columns
            val oldTabStop = mTabStop
            mTabStop = BooleanArray(mColumns)
            setDefaultTabStops()
            val toTransfer = minOf(oldColumns, columns)
            System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer)
            mLeftMargin = 0
            mRightMargin = mColumns
        }

        resizeScreen()
    }

    private fun resizeScreen() {
        val cursor = intArrayOf(mCursorCol, mCursorRow)
        val newTotalRows = if (mScreen === mAltBuffer) mRows else mMainBuffer.mTotalRows
        mScreen.resize(mColumns, mRows, newTotalRows, cursor, getStyle(), isAlternateBufferActive())
        mCursorCol = cursor[0]
        mCursorRow = cursor[1]
    }

    fun getCursorRow(): Int = mCursorRow
    fun getCursorCol(): Int = mCursorCol
    fun getCursorStyle(): Int = mCursorStyle

    fun setCursorStyle() {
        val cursorStyle = mClient?.getTerminalCursorStyle()
        mCursorStyle = if (cursorStyle == null || !TERMINAL_CURSOR_STYLES_LIST.contains(cursorStyle))
            DEFAULT_TERMINAL_CURSOR_STYLE
        else
            cursorStyle
    }

    fun isReverseVideo(): Boolean = isDecsetInternalBitSet(DECSET_BIT_REVERSE_VIDEO)
    fun isCursorEnabled(): Boolean = isDecsetInternalBitSet(DECSET_BIT_CURSOR_ENABLED)

    fun shouldCursorBeVisible(): Boolean {
        return if (!isCursorEnabled()) false else if (mCursorBlinkingEnabled) mCursorBlinkState else true
    }

    fun setCursorBlinkingEnabled(cursorBlinkingEnabled: Boolean) {
        this.mCursorBlinkingEnabled = cursorBlinkingEnabled
    }

    fun setCursorBlinkState(cursorBlinkState: Boolean) {
        this.mCursorBlinkState = cursorBlinkState
    }

    fun isKeypadApplicationMode(): Boolean = isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD)
    fun isCursorKeysApplicationMode(): Boolean = isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS)
    fun isMouseTrackingActive(): Boolean = isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)

    private fun setDefaultTabStops() {
        for (i in 0 until mColumns)
            mTabStop[i] = (i and 7) == 0 && i != 0
    }

    fun append(buffer: ByteArray, length: Int) {
        for (i in 0 until length)
            processByte(buffer[i])
    }


    private fun processByte(byteToProcess: Byte) {
        if (mUtf8ToFollow > 0) {
            if ((byteToProcess.toInt() and 0b11000000) == 0b10000000) {
                // 10xxxxxx, a continuation byte.
                mUtf8InputBuffer[mUtf8Index++.toInt()] = byteToProcess
                if (--mUtf8ToFollow == 0.toByte()) {
                    val firstByteMask: Byte = if (mUtf8Index.toInt() == 2) 0b00011111 else (if (mUtf8Index.toInt() == 3) 0b00001111 else 0b00000111)
                    var codePoint = (mUtf8InputBuffer[0].toInt() and firstByteMask.toInt())
                    for (i in 1 until mUtf8Index.toInt())
                        codePoint = ((codePoint shl 6) or (mUtf8InputBuffer[i].toInt() and 0b00111111))
                    if (((codePoint <= 0b1111111) && mUtf8Index > 1) || (codePoint < 0b11111111111 && mUtf8Index > 2)
                        || (codePoint < 0b1111111111111111 && mUtf8Index > 3)) {
                        // Overlong encoding.
                        codePoint = UNICODE_REPLACEMENT_CHAR
                    }

                    mUtf8Index = 0
                    mUtf8ToFollow = 0

                    if (codePoint >= 0x80 && codePoint <= 0x9F) {
                        // Sequence decoded to a C1 control character which we ignore.
                    } else {
                        when (Character.getType(codePoint).toByte()) {
                            Character.UNASSIGNED, Character.SURROGATE -> codePoint = UNICODE_REPLACEMENT_CHAR
                        }
                        processCodePoint(codePoint)
                    }
                }
            } else {
                // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                mUtf8Index = 0
                mUtf8ToFollow = 0
                emitCodePoint(UNICODE_REPLACEMENT_CHAR)
                processByte(byteToProcess)
            }
        } else {
            if ((byteToProcess.toInt() and 0b10000000) == 0) { // The leading bit is not set so it is a 7-bit ASCII character.
                processCodePoint(byteToProcess.toInt())
                return
            } else if ((byteToProcess.toInt() and 0b11100000) == 0b11000000) { // 110xxxxx, a two-byte sequence.
                mUtf8ToFollow = 1
            } else if ((byteToProcess.toInt() and 0b11110000) == 0b11100000) { // 1110xxxx, a three-byte sequence.
                mUtf8ToFollow = 2
            } else if ((byteToProcess.toInt() and 0b11111000) == 0b11110000) { // 11110xxx, a four-byte sequence.
                mUtf8ToFollow = 3
            } else {
                // Not a valid UTF-8 sequence start, signal invalid data:
                processCodePoint(UNICODE_REPLACEMENT_CHAR)
                return
            }
            mUtf8InputBuffer[mUtf8Index++.toInt()] = byteToProcess
        }
    }

    fun processCodePoint(b: Int) {
        // The Application Program-Control (APC) string might be arbitrary non-printable characters, so handle that early.
        if (mEscapeState == ESC_APC) {
            doApc(b)
            return
        } else if (mEscapeState == ESC_APC_ESCAPE) {
            doApcEscape(b)
            return
        }

        when (b) {
            0 -> { } // Null character (NUL, ^@). Do nothing.
            7 -> { // Bell (BEL, ^G, \a). If in an OSC sequence, BEL may terminate a string; otherwise signal bell.
                if (mEscapeState == ESC_OSC)
                    doOsc(b)
                else
                    mSession.onBell()
            }
            8 -> { // Backspace (BS, ^H).
                if (mLeftMargin == mCursorCol) {
                    // Jump to previous line if it was auto-wrapped.
                    val previousRow = mCursorRow - 1
                    if (previousRow >= 0 && mScreen.getLineWrap(previousRow)) {
                        mScreen.clearLineWrap(previousRow)
                        setCursorRowCol(previousRow, mRightMargin - 1)
                    }
                } else {
                    setCursorCol(mCursorCol - 1)
                }
            }
            9 -> { // Horizontal tab (HT, \t) - move to next tab stop, but not past edge of screen
                mCursorCol = nextTabStop(1)
            }
            10, 11, 12 -> { // Line feed (LF, \n), Vertical tab (VT, \v), Form feed (FF, \f).
                doLinefeed()
            }
            13 -> { // Carriage return (CR, \r).
                setCursorCol(mLeftMargin)
            }
            14 -> { // Shift Out (Ctrl-N, SO) → Switch to Alternate Character Set. This invokes the G1 character set.
                mUseLineDrawingUsesG0 = false
            }
            15 -> { // Shift In (Ctrl-O, SI) → Switch to Standard Character Set. This invokes the G0 character set.
                mUseLineDrawingUsesG0 = true
            }
            24, 26 -> { // CAN, SUB.
                if (mEscapeState != ESC_NONE) {
                    mEscapeState = ESC_NONE
                    emitCodePoint(127)
                }
            }
            27 -> { // ESC
                // Starts an escape sequence unless we're parsing a string
                if (mEscapeState == ESC_P) {
                    // XXX: Ignore escape when reading device control sequence, since it may be part of string terminator.
                    return
                } else if (mEscapeState != ESC_OSC) {
                    startEscapeSequence()
                } else {
                    doOsc(b)
                }
            }
            else -> {
                mContinueSequence = false
                when (mEscapeState) {
                    ESC_NONE -> if (b >= 32) emitCodePoint(b)
                    ESC -> doEsc(b)
                    ESC_POUND -> doEscPound(b)
                    ESC_SELECT_LEFT_PAREN -> mUseLineDrawingG0 = (b == '0'.code)
                    ESC_SELECT_RIGHT_PAREN -> mUseLineDrawingG1 = (b == '0'.code)
                    ESC_CSI -> doCsi(b)
                    ESC_CSI_UNSUPPORTED_PARAMETER_BYTE, ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE -> doCsiUnsupportedParameterOrIntermediateByte(b)
                    ESC_CSI_EXCLAMATION -> {
                        if (b == 'p'.code) { // Soft terminal reset (DECSTR, http://vt100.net/docs/vt510-rm/DECSTR).
                            reset()
                        } else {
                            unknownSequence(b)
                        }
                    }
                    ESC_CSI_QUESTIONMARK -> doCsiQuestionMark(b)
                    ESC_CSI_BIGGERTHAN -> doCsiBiggerThan(b)
                    ESC_CSI_DOLLAR -> {
                        val originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
                        val effectiveTopMargin = if (originMode) mTopMargin else 0
                        val effectiveBottomMargin = if (originMode) mBottomMargin else mRows
                        val effectiveLeftMargin = if (originMode) mLeftMargin else 0
                        val effectiveRightMargin = if (originMode) mRightMargin else mColumns
                        when (b) {
                            'v'.code -> { // Copy rectangular area (DECCRA)
                                val topSource = minOf(getArg(0, 1, true) - 1 + effectiveTopMargin, mRows)
                                val leftSource = minOf(getArg(1, 1, true) - 1 + effectiveLeftMargin, mColumns)
                                val bottomSource = minOf(maxOf(getArg(2, mRows, true) + effectiveTopMargin, topSource), mRows)
                                val rightSource = minOf(maxOf(getArg(3, mColumns, true) + effectiveLeftMargin, leftSource), mColumns)
                                val destionationTop = minOf(getArg(5, 1, true) - 1 + effectiveTopMargin, mRows)
                                val destinationLeft = minOf(getArg(6, 1, true) - 1 + effectiveLeftMargin, mColumns)
                                val heightToCopy = minOf(mRows - destionationTop, bottomSource - topSource)
                                val widthToCopy = minOf(mColumns - destinationLeft, rightSource - leftSource)
                                mScreen.blockCopy(leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destionationTop)
                            }
                            '{'.code, 'x'.code, 'z'.code -> { // DECSERA, DECFRA, DECERA
                                val erase = b != 'x'.code
                                val selective = b == '{'.code
                                val keepVisualAttributes = erase && selective
                                var argIndex = 0
                                val fillChar = if (erase) ' '.code else getArg(argIndex++, -1, true)
                                if ((fillChar in 32..126) || (fillChar in 160..255)) {
                                    val top = minOf(getArg(argIndex++, 1, true) + effectiveTopMargin, effectiveBottomMargin + 1)
                                    val left = minOf(getArg(argIndex++, 1, true) + effectiveLeftMargin, effectiveRightMargin + 1)
                                    val bottom = minOf(getArg(argIndex++, mRows, true) + effectiveTopMargin, effectiveBottomMargin)
                                    val right = minOf(getArg(argIndex, mColumns, true) + effectiveLeftMargin, effectiveRightMargin)
                                    val style = getStyle()
                                    for (row in top - 1 until bottom)
                                        for (col in left - 1 until right)
                                            if (!selective || (TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                                                mScreen.setChar(col, row, fillChar, if (keepVisualAttributes) mScreen.getStyleAt(row, col) else style)
                                }
                            }
                            'r'.code, 't'.code -> { // DECCARA, DECRARA
                                val reverse = b == 't'.code
                                val top = minOf(getArg(0, 1, true) - 1, effectiveBottomMargin) + effectiveTopMargin
                                val left = minOf(getArg(1, 1, true) - 1, effectiveRightMargin) + effectiveLeftMargin
                                val bottom = minOf(getArg(2, mRows, true) + 1, effectiveBottomMargin - 1) + effectiveTopMargin
                                val right = minOf(getArg(3, mColumns, true) + 1, effectiveRightMargin - 1) + effectiveLeftMargin
                                if (mArgIndex >= 4) {
                                    if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                                    for (i in 4..mArgIndex) {
                                        var bits = 0
                                        var setOrClear = true
                                        when (getArg(i, 0, false)) {
                                            0 -> {
                                                bits = (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE or TextStyle.CHARACTER_ATTRIBUTE_BLINK or TextStyle.CHARACTER_ATTRIBUTE_INVERSE)
                                                if (!reverse) setOrClear = false
                                            }
                                            1 -> bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD
                                            4 -> bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                                            5 -> bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK
                                            7 -> bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE
                                            22 -> { bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD; setOrClear = false }
                                            24 -> { bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE; setOrClear = false }
                                            25 -> { bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK; setOrClear = false }
                                            27 -> { bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE; setOrClear = false }
                                        }
                                        if (!(reverse && !setOrClear)) {
                                            mScreen.setOrClearEffect(bits, setOrClear, reverse, isDecsetInternalBitSet(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE),
                                                effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right)
                                        }
                                    }
                                }
                            }
                            else -> unknownSequence(b)
                        }
                    }
                    ESC_CSI_DOUBLE_QUOTE -> {
                        if (b == 'q'.code) {
                            val arg = getArg0(0)
                            if (arg == 0 || arg == 2) {
                                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED.inv()
                            } else if (arg == 1) {
                                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_PROTECTED
                            } else {
                                unknownSequence(b)
                            }
                        } else {
                            unknownSequence(b)
                        }
                    }
                    ESC_CSI_SINGLE_QUOTE -> {
                        if (b == '}'.code) { // Insert Ps Column(s) (DECIC)
                            val columnsAfterCursor = mRightMargin - mCursorCol
                            val columnsToInsert = minOf(getArg0(1), columnsAfterCursor)
                            val columnsToMove = columnsAfterCursor - columnsToInsert
                            mScreen.blockCopy(mCursorCol, 0, columnsToMove, mRows, mCursorCol + columnsToInsert, 0)
                            blockClear(mCursorCol, 0, columnsToInsert, mRows)
                        } else if (b == '~'.code) { // Delete Ps Column(s) (DECDC)
                            val columnsAfterCursor = mRightMargin - mCursorCol
                            val columnsToDelete = minOf(getArg0(1), columnsAfterCursor)
                            val columnsToMove = columnsAfterCursor - columnsToDelete
                            mScreen.blockCopy(mCursorCol + columnsToDelete, 0, columnsToMove, mRows, mCursorCol, 0)
                        } else {
                            unknownSequence(b)
                        }
                    }
                    ESC_PERCENT -> { }
                    ESC_OSC -> doOsc(b)
                    ESC_OSC_ESC -> doOscEsc(b)
                    ESC_P -> doDeviceControl(b)
                    ESC_CSI_QUESTIONMARK_ARG_DOLLAR -> {
                        if (b == 'p'.code) {
                            val mode = getArg0(0)
                            val value: Int
                            if (mode == 47 || mode == 1047 || mode == 1049) {
                                value = if (mScreen === mAltBuffer) 1 else 2
                            } else {
                                val internalBit = mapDecSetBitToInternalBit(mode)
                                if (internalBit != -1) {
                                    value = if (isDecsetInternalBitSet(internalBit)) 1 else 2
                                } else {
                                    Logger.logError(mClient, LOG_TAG, "Got DECRQM for unrecognized private DEC mode=$mode")
                                    value = 0
                                }
                            }
                            mSession.write(String.format(Locale.US, "\u001b[?%d;%d\$y", mode, value))
                        } else {
                            unknownSequence(b)
                        }
                    }
                    ESC_CSI_ARGS_SPACE -> {
                        val arg = getArg0(0)
                        when (b) {
                            'q'.code -> {
                                when (arg) {
                                    0, 1, 2 -> mCursorStyle = TERMINAL_CURSOR_STYLE_BLOCK
                                    3, 4 -> mCursorStyle = TERMINAL_CURSOR_STYLE_UNDERLINE
                                    5, 6 -> mCursorStyle = TERMINAL_CURSOR_STYLE_BAR
                                }
                            }
                            't'.code, 'u'.code -> { } // Set margin-bell volume - ignore.
                            else -> unknownSequence(b)
                        }
                    }
                    ESC_CSI_ARGS_ASTERIX -> {
                        val attributeChangeExtent = getArg0(0)
                        if (b == 'x'.code && (attributeChangeExtent in 0..2)) {
                            setDecsetinternalBit(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, attributeChangeExtent == 2)
                        } else {
                            unknownSequence(b)
                        }
                    }
                    else -> unknownSequence(b)
                }
                if (!mContinueSequence) mEscapeState = ESC_NONE
            }
        }
    }

    /** When in [ESC_P] ("device control") sequence. */
    private fun doDeviceControl(b: Int) {
        when (b) {
            '\\'.code -> {
                val dcs = mOSCOrDeviceControlArgs.toString()
                if (dcs.startsWith("\$q")) {
                    if (dcs == "\$q\"p") {
                        val csiString = "64;1\"p"
                        mSession.write("\u001bP1\$r$csiString\u001b\\")
                    } else {
                        finishSequenceAndLogError("Unrecognized DECRQSS string: '$dcs'")
                    }
                } else if (dcs.startsWith("+q")) {
                    for (part in dcs.substring(2).split(";")) {
                        if (part.length % 2 == 0) {
                            val transBuffer = StringBuilder()
                            var c: Char
                            var i = 0
                            while (i < part.length) {
                                try {
                                    c = java.lang.Long.decode("0x" + part[i] + "" + part[i + 1]).toLong().toInt().toChar()
                                } catch (e: NumberFormatException) {
                                    Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Invalid device termcap/terminfo encoded name \"$part\"", e)
                                    i += 2
                                    continue
                                }
                                transBuffer.append(c)
                                i += 2
                            }

                            val trans = transBuffer.toString()
                            val responseValue: String? = when (trans) {
                                "Co", "colors" -> "256"
                                "TN", "name" -> "xterm"
                                else -> KeyHandler.getCodeFromTermcap(trans, isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS),
                                    isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD))
                            }
                            if (responseValue == null) {
                                when (trans) {
                                    "%1", "&8" -> { } // Help/Undo key - ignore
                                    else -> Logger.logWarn(mClient, LOG_TAG, "Unhandled termcap/terminfo name: '$trans'")
                                }
                                mSession.write("\u001bP0+r$part\u001b\\")
                            } else {
                                val hexEncoded = StringBuilder()
                                for (j in responseValue.indices) {
                                    hexEncoded.append(String.format("%02X", responseValue[j].code))
                                }
                                mSession.write("\u001bP1+r$part=$hexEncoded\u001b\\")
                            }
                        } else {
                            Logger.logError(mClient, LOG_TAG, "Invalid device termcap/terminfo name of odd length: $part")
                        }
                    }
                } else {
                    if (LOG_ESCAPE_SEQUENCES)
                        Logger.logError(mClient, LOG_TAG, "Unrecognized device control string: $dcs")
                }
                finishSequence()
            }
            else -> {
                if (mOSCOrDeviceControlArgs.length > MAX_OSC_STRING_LENGTH) {
                    mOSCOrDeviceControlArgs.setLength(0)
                    finishSequence()
                } else {
                    mOSCOrDeviceControlArgs.appendCodePoint(b)
                    continueSequence(mEscapeState)
                }
            }
        }
    }

    /** When in [ESC_APC] (APC, Application Program Command) sequence. */
    private fun doApc(b: Int) {
        if (b == 27) {
            continueSequence(ESC_APC_ESCAPE)
        }
        // Eat APC sequences silently for now.
    }

    /** When in [ESC_APC] (APC, Application Program Command) sequence. */
    private fun doApcEscape(b: Int) {
        if (b == '\\'.code) {
            finishSequence()
        } else {
            continueSequence(ESC_APC)
        }
    }

    private fun nextTabStop(numTabs: Int): Int {
        var tabs = numTabs
        for (i in mCursorCol + 1 until mColumns)
            if (mTabStop[i] && --tabs == 0) return minOf(i, mRightMargin)
        return mRightMargin - 1
    }

    private fun doCsiUnsupportedParameterOrIntermediateByte(b: Int) {
        if (mEscapeState == ESC_CSI_UNSUPPORTED_PARAMETER_BYTE && b >= 0x30 && b <= 0x3F) {
            continueSequence(ESC_CSI_UNSUPPORTED_PARAMETER_BYTE)
        } else if (b >= 0x20 && b <= 0x2F) {
            continueSequence(ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE)
        } else if (b >= 0x40 && b <= 0x7E) {
            finishSequence()
        } else {
            unknownSequence(b)
        }
    }

    /** Process byte while in the [ESC_CSI_QUESTIONMARK] escape state. */
    private fun doCsiQuestionMark(b: Int) {
        when (b) {
            'J'.code, 'K'.code -> { // DECSED, DECSEL
                mAboutToAutoWrap = false
                val fillChar = ' '.code
                var startCol = -1
                var startRow = -1
                var endCol = -1
                var endRow = -1
                val justRow = (b == 'K'.code)
                when (getArg0(0)) {
                    0 -> {
                        startCol = mCursorCol; startRow = mCursorRow
                        endCol = mColumns; endRow = if (justRow) (mCursorRow + 1) else mRows
                    }
                    1 -> {
                        startCol = 0; startRow = if (justRow) mCursorRow else 0
                        endCol = mCursorCol + 1; endRow = mCursorRow + 1
                    }
                    2 -> {
                        startCol = 0; startRow = if (justRow) mCursorRow else 0
                        endCol = mColumns; endRow = if (justRow) (mCursorRow + 1) else mRows
                    }
                    else -> unknownSequence(b)
                }
                val style = getStyle()
                for (row in startRow until endRow) {
                    for (col in startCol until endCol) {
                        if ((TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                            mScreen.setChar(col, row, fillChar, style)
                    }
                }
            }
            'h'.code, 'l'.code -> {
                if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                for (i in 0..mArgIndex)
                    doDecSetOrReset(b == 'h'.code, mArgs[i])
            }
            'n'.code -> { // Device Status Report (DSR, DEC-specific)
                when (getArg0(-1)) {
                    6 -> mSession.write(String.format(Locale.US, "\u001b[?%d;%d;1R", mCursorRow + 1, mCursorCol + 1))
                    else -> finishSequence()
                }
            }
            'r'.code, 's'.code -> {
                if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                for (i in 0..mArgIndex) {
                    val externalBit = mArgs[i]
                    val internalBit = mapDecSetBitToInternalBit(externalBit)
                    if (internalBit == -1) {
                        Logger.logWarn(mClient, LOG_TAG, "Ignoring request to save/recall decset bit=$externalBit")
                    } else {
                        if (b == 's'.code) {
                            mSavedDecSetFlags = mSavedDecSetFlags or internalBit
                        } else {
                            doDecSetOrReset((mSavedDecSetFlags and internalBit) != 0, externalBit)
                        }
                    }
                }
            }
            '$'.code -> {
                continueSequence(ESC_CSI_QUESTIONMARK_ARG_DOLLAR)
                return
            }
            else -> parseArg(b)
        }
    }

    fun doDecSetOrReset(setting: Boolean, externalBit: Int) {
        val internalBit = mapDecSetBitToInternalBit(externalBit)
        if (internalBit != -1) {
            setDecsetinternalBit(internalBit, setting)
        }
        when (externalBit) {
            1 -> { } // Application Cursor Keys (DECCKM).
            3 -> {
                mLeftMargin = 0; mTopMargin = 0
                mBottomMargin = mRows
                mRightMargin = mColumns
                setDecsetinternalBit(DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false)
                blockClear(0, 0, mColumns, mRows)
                setCursorRowCol(0, 0)
            }
            4 -> { } // DECSCLM-Scrolling Mode. Ignore.
            5 -> { } // Reverse video. No action.
            6 -> { if (setting) setCursorPosition(0, 0) } // DECOM
            7, 8, 9, 12 -> { } // Various - ignore
            25 -> { mClient?.onTerminalCursorStateChange(setting) }
            40, 45, 66 -> { } // Ignore
            69 -> { if (!setting) { mLeftMargin = 0; mRightMargin = mColumns } }
            1000, 1001, 1002, 1003, 1004, 1005, 1006, 1015, 1034 -> { }
            1048 -> { if (setting) saveCursor() else restoreCursor() }
            47, 1047, 1049 -> {
                val newScreen = if (setting) mAltBuffer else mMainBuffer
                if (newScreen !== mScreen) {
                    val resized = !(newScreen.mColumns == mColumns && newScreen.mScreenRows == mRows)
                    if (setting) saveCursor()
                    mScreen = newScreen
                    if (!setting) {
                        val col = mSavedStateMain.mSavedCursorCol
                        val row = mSavedStateMain.mSavedCursorRow
                        restoreCursor()
                        if (resized) {
                            mCursorCol = col
                            mCursorRow = row
                        }
                    }
                    if (resized) resizeScreen()
                    if (newScreen === mAltBuffer)
                        newScreen.blockSet(0, 0, mColumns, mRows, ' '.code, getStyle())
                }
            }
            2004 -> { } // Bracketed paste mode - setting bit is enough.
            else -> unknownParameter(externalBit)
        }
    }

    private fun doCsiBiggerThan(b: Int) {
        when (b) {
            'c'.code -> { // Secondary Device Attributes (DA2)
                mSession.write("\u001b[>41;320;0c")
            }
            'm'.code -> {
                Logger.logError(mClient, LOG_TAG, "(ignored) CSI > MODIFY RESOURCE: ${getArg0(-1)} to ${getArg1(-1)}")
            }
            else -> parseArg(b)
        }
    }

    private fun startEscapeSequence() {
        mEscapeState = ESC
        mArgIndex = 0
        Arrays.fill(mArgs, -1)
        mArgsSubParamsBitSet = 0
    }

    private fun doLinefeed() {
        val belowScrollingRegion = mCursorRow >= mBottomMargin
        var newCursorRow = mCursorRow + 1
        if (belowScrollingRegion) {
            if (mCursorRow != mRows - 1) {
                setCursorRow(newCursorRow)
            }
        } else {
            if (newCursorRow == mBottomMargin) {
                scrollDownOneLine()
                newCursorRow = mBottomMargin - 1
            }
            setCursorRow(newCursorRow)
        }
    }

    private fun continueSequence(state: Int) {
        mEscapeState = state
        mContinueSequence = true
    }

    private fun doEscPound(b: Int) {
        when (b) {
            '8'.code -> mScreen.blockSet(0, 0, mColumns, mRows, 'E'.code, getStyle())
            else -> unknownSequence(b)
        }
    }

    /** Encountering a character in the [ESC] state. */
    private fun doEsc(b: Int) {
        when (b) {
            '#'.code -> continueSequence(ESC_POUND)
            '('.code -> continueSequence(ESC_SELECT_LEFT_PAREN)
            ')'.code -> continueSequence(ESC_SELECT_RIGHT_PAREN)
            '6'.code -> { // Back index (DECBI)
                if (mCursorCol > mLeftMargin) {
                    mCursorCol--
                } else {
                    val rows = mBottomMargin - mTopMargin
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin + 1, mTopMargin)
                    mScreen.blockSet(mLeftMargin, mTopMargin, 1, rows, ' '.code, TextStyle.encode(mForeColor, mBackColor, 0))
                }
            }
            '7'.code -> saveCursor() // DECSC
            '8'.code -> restoreCursor() // DECRC
            '9'.code -> { // Forward Index (DECFI)
                if (mCursorCol < mRightMargin - 1) {
                    mCursorCol++
                } else {
                    val rows = mBottomMargin - mTopMargin
                    mScreen.blockCopy(mLeftMargin + 1, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin, mTopMargin)
                    mScreen.blockSet(mRightMargin - 1, mTopMargin, 1, rows, ' '.code, TextStyle.encode(mForeColor, mBackColor, 0))
                }
            }
            'c'.code -> { // RIS
                reset()
                mMainBuffer.clearTranscript()
                blockClear(0, 0, mColumns, mRows)
                setCursorPosition(0, 0)
            }
            'D'.code -> doLinefeed() // INDEX
            'E'.code -> { // Next line (NEL)
                setCursorCol(if (isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)) mLeftMargin else 0)
                doLinefeed()
            }
            'F'.code -> setCursorRowCol(0, mBottomMargin - 1) // Cursor to lower-left corner
            'H'.code -> mTabStop[mCursorCol] = true // Tab set
            'M'.code -> { // Reverse index (RI)
                if (mCursorRow <= mTopMargin) {
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, mBottomMargin - (mTopMargin + 1), mLeftMargin, mTopMargin + 1)
                    blockClear(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin)
                } else {
                    mCursorRow--
                }
            }
            'N'.code, '0'.code -> { } // SS2, SS3 - ignore
            'P'.code -> { // Device control string
                mOSCOrDeviceControlArgs.setLength(0)
                continueSequence(ESC_P)
            }
            '['.code -> continueSequence(ESC_CSI)
            '='.code -> setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, true) // DECKPAM
            ']'.code -> { // OSC
                mOSCOrDeviceControlArgs.setLength(0)
                continueSequence(ESC_OSC)
            }
            '>'.code -> setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, false) // DECKPNM
            '_'.code -> continueSequence(ESC_APC) // APC
            else -> unknownSequence(b)
        }
    }

    /** DECSC save cursor. */
    private fun saveCursor() {
        val state = if (mScreen === mMainBuffer) mSavedStateMain else mSavedStateAlt
        state.mSavedCursorRow = mCursorRow
        state.mSavedCursorCol = mCursorCol
        state.mSavedEffect = mEffect
        state.mSavedForeColor = mForeColor
        state.mSavedBackColor = mBackColor
        state.mSavedDecFlags = mCurrentDecSetFlags
        state.mUseLineDrawingG0 = mUseLineDrawingG0
        state.mUseLineDrawingG1 = mUseLineDrawingG1
        state.mUseLineDrawingUsesG0 = mUseLineDrawingUsesG0
    }

    /** DECRS restore cursor. */
    private fun restoreCursor() {
        val state = if (mScreen === mMainBuffer) mSavedStateMain else mSavedStateAlt
        setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol)
        mEffect = state.mSavedEffect
        mForeColor = state.mSavedForeColor
        mBackColor = state.mSavedBackColor
        val mask = (DECSET_BIT_AUTOWRAP or DECSET_BIT_ORIGIN_MODE)
        mCurrentDecSetFlags = (mCurrentDecSetFlags and mask.inv()) or (state.mSavedDecFlags and mask)
        mUseLineDrawingG0 = state.mUseLineDrawingG0
        mUseLineDrawingG1 = state.mUseLineDrawingG1
        mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0
    }

    /** Following a CSI - Control Sequence Introducer, "\033[". [ESC_CSI]. */
    private fun doCsi(b: Int) {
        when (b) {
            '!'.code -> continueSequence(ESC_CSI_EXCLAMATION)
            '"'.code -> continueSequence(ESC_CSI_DOUBLE_QUOTE)
            '\''.code -> continueSequence(ESC_CSI_SINGLE_QUOTE)
            '$'.code -> continueSequence(ESC_CSI_DOLLAR)
            '*'.code -> continueSequence(ESC_CSI_ARGS_ASTERIX)
            '@'.code -> { // Insert ${n} space characters (ICH)
                mAboutToAutoWrap = false
                val columnsAfterCursor = mColumns - mCursorCol
                val spacesToInsert = minOf(getArg0(1), columnsAfterCursor)
                val charsToMove = columnsAfterCursor - spacesToInsert
                mScreen.blockCopy(mCursorCol, mCursorRow, charsToMove, 1, mCursorCol + spacesToInsert, mCursorRow)
                blockClear(mCursorCol, mCursorRow, spacesToInsert)
            }
            'A'.code -> setCursorRow(maxOf(0, mCursorRow - getArg0(1))) // CUU
            'B'.code -> setCursorRow(minOf(mRows - 1, mCursorRow + getArg0(1))) // CUD
            'C'.code, 'a'.code -> setCursorCol(minOf(mRightMargin - 1, mCursorCol + getArg0(1))) // CUF, HPR
            'D'.code -> setCursorCol(maxOf(mLeftMargin, mCursorCol - getArg0(1))) // CUB
            'E'.code -> setCursorPosition(0, mCursorRow + getArg0(1)) // CNL
            'F'.code -> setCursorPosition(0, mCursorRow - getArg0(1)) // CPL
            'G'.code -> setCursorCol(minOf(maxOf(1, getArg0(1)), mColumns) - 1) // CHA
            'H'.code, 'f'.code -> setCursorPosition(getArg1(1) - 1, getArg0(1) - 1) // CUP, HVP
            'I'.code -> setCursorCol(nextTabStop(getArg0(1))) // CHT
            'J'.code -> { // ED - Erase in Display
                when (getArg0(0)) {
                    0 -> {
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol)
                        blockClear(0, mCursorRow + 1, mColumns, mRows - (mCursorRow + 1))
                    }
                    1 -> {
                        blockClear(0, 0, mColumns, mCursorRow)
                        blockClear(0, mCursorRow, mCursorCol + 1)
                    }
                    2 -> blockClear(0, 0, mColumns, mRows)
                    3 -> mMainBuffer.clearTranscript()
                    else -> { unknownSequence(b); return }
                }
                mAboutToAutoWrap = false
            }
            'K'.code -> { // EL - Erase in line
                when (getArg0(0)) {
                    0 -> blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol)
                    1 -> blockClear(0, mCursorRow, mCursorCol + 1)
                    2 -> blockClear(0, mCursorRow, mColumns)
                    else -> { unknownSequence(b); return }
                }
                mAboutToAutoWrap = false
            }
            'L'.code -> { // IL - insert lines
                val linesAfterCursor = mBottomMargin - mCursorRow
                val linesToInsert = minOf(getArg0(1), linesAfterCursor)
                val linesToMove = linesAfterCursor - linesToInsert
                mScreen.blockCopy(0, mCursorRow, mColumns, linesToMove, 0, mCursorRow + linesToInsert)
                blockClear(0, mCursorRow, mColumns, linesToInsert)
            }
            'M'.code -> { // DL - delete lines
                mAboutToAutoWrap = false
                val linesAfterCursor = mBottomMargin - mCursorRow
                val linesToDelete = minOf(getArg0(1), linesAfterCursor)
                val linesToMove = linesAfterCursor - linesToDelete
                mScreen.blockCopy(0, mCursorRow + linesToDelete, mColumns, linesToMove, 0, mCursorRow)
                blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete)
            }
            'P'.code -> { // DCH - delete characters
                mAboutToAutoWrap = false
                val cellsAfterCursor = mColumns - mCursorCol
                val cellsToDelete = minOf(getArg0(1), cellsAfterCursor)
                val cellsToMove = cellsAfterCursor - cellsToDelete
                mScreen.blockCopy(mCursorCol + cellsToDelete, mCursorRow, cellsToMove, 1, mCursorCol, mCursorRow)
                blockClear(mCursorCol + cellsToMove, mCursorRow, cellsToDelete)
            }
            'S'.code -> { // SU - scroll up
                val linesToScroll = getArg0(1)
                for (i in 0 until linesToScroll) scrollDownOneLine()
            }
            'T'.code -> {
                if (mArgIndex == 0) {
                    val linesToScrollArg = getArg0(1)
                    val linesBetweenTopAndBottomMargins = mBottomMargin - mTopMargin
                    val linesToScroll = minOf(linesBetweenTopAndBottomMargins, linesToScrollArg)
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, linesBetweenTopAndBottomMargins - linesToScroll, mLeftMargin, mTopMargin + linesToScroll)
                    blockClear(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, linesToScroll)
                } else {
                    unimplementedSequence(b)
                }
            }
            'X'.code -> { // ECH - Erase characters
                mAboutToAutoWrap = false
                mScreen.blockSet(mCursorCol, mCursorRow, minOf(getArg0(1), mColumns - mCursorCol), 1, ' '.code, getStyle())
            }
            'Z'.code -> { // CBT - Cursor Backward Tabulation
                var numberOfTabs = getArg0(1)
                var newCol = mLeftMargin
                for (i in mCursorCol - 1 downTo 0) {
                    if (mTabStop[i]) {
                        if (--numberOfTabs == 0) {
                            newCol = maxOf(i, mLeftMargin)
                            break
                        }
                    }
                }
                mCursorCol = newCol
            }
            '?'.code -> continueSequence(ESC_CSI_QUESTIONMARK)
            '>'.code -> continueSequence(ESC_CSI_BIGGERTHAN)
            '<'.code, '='.code -> continueSequence(ESC_CSI_UNSUPPORTED_PARAMETER_BYTE)
            '`'.code -> setCursorColRespectingOriginMode(getArg0(1) - 1) // HPA
            'b'.code -> { // REP - Repeat
                if (mLastEmittedCodePoint != -1) {
                    val numRepeat = getArg0(1)
                    for (i in 0 until numRepeat) emitCodePoint(mLastEmittedCodePoint)
                }
            }
            'c'.code -> { // DA1 - Primary Device Attributes
                if (getArg0(0) == 0) mSession.write("\u001b[?64;1;2;6;9;15;18;21;22c")
            }
            'd'.code -> setCursorRow(minOf(maxOf(1, getArg0(1)), mRows) - 1) // VPA
            'e'.code -> setCursorPosition(mCursorCol, mCursorRow + getArg0(1)) // VPR
            'g'.code -> { // Clear tab stop
                when (getArg0(0)) {
                    0 -> mTabStop[mCursorCol] = false
                    3 -> for (i in 0 until mColumns) mTabStop[i] = false
                }
            }
            'h'.code -> doSetMode(true)
            'l'.code -> doSetMode(false)
            'm'.code -> selectGraphicRendition() // SGR
            'n'.code -> { // DSR - Device Status Report
                when (getArg0(0)) {
                    5 -> {
                        val dsr = byteArrayOf(27.toByte(), '['.code.toByte(), '0'.code.toByte(), 'n'.code.toByte())
                        mSession.write(dsr, 0, dsr.size)
                    }
                    6 -> mSession.write(String.format(Locale.US, "\u001b[%d;%dR", mCursorRow + 1, mCursorCol + 1))
                }
            }
            'r'.code -> { // DECSTBM - set top and bottom margins
                mTopMargin = maxOf(0, minOf(getArg0(1) - 1, mRows - 2))
                mBottomMargin = maxOf(mTopMargin + 2, minOf(getArg1(mRows), mRows))
                setCursorPosition(0, 0)
            }
            's'.code -> {
                if (isDecsetInternalBitSet(DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {
                    mLeftMargin = minOf(getArg0(1) - 1, mColumns - 2)
                    mRightMargin = maxOf(mLeftMargin + 1, minOf(getArg1(mColumns), mColumns))
                    setCursorPosition(0, 0)
                } else {
                    saveCursor()
                }
            }
            't'.code -> { // Window manipulation
                when (getArg0(0)) {
                    11 -> mSession.write("\u001b[1t")
                    13 -> mSession.write("\u001b[3;0;0t")
                    14 -> mSession.write(String.format(Locale.US, "\u001b[4;%d;%dt", mRows * mCellHeightPixels, mColumns * mCellWidthPixels))
                    16 -> mSession.write(String.format(Locale.US, "\u001b[6;%d;%dt", mCellHeightPixels, mCellWidthPixels))
                    18 -> mSession.write(String.format(Locale.US, "\u001b[8;%d;%dt", mRows, mColumns))
                    19 -> mSession.write(String.format(Locale.US, "\u001b[9;%d;%dt", mRows, mColumns))
                    20 -> mSession.write("\u001b]LIconLabel\u001b\\")
                    21 -> mSession.write("\u001b]l\u001b\\")
                    22 -> {
                        mTitleStack.push(mTitle)
                        if (mTitleStack.size > 20) mTitleStack.removeAt(0)
                    }
                    23 -> if (mTitleStack.isNotEmpty()) setTitle(mTitleStack.pop())
                }
            }
            'u'.code -> restoreCursor()
            ' '.code -> continueSequence(ESC_CSI_ARGS_SPACE)
            else -> parseArg(b)
        }
    }

    /** Select Graphic Rendition (SGR). */
    private fun selectGraphicRendition() {
        if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
        var i = 0
        while (i <= mArgIndex) {
            if ((mArgsSubParamsBitSet and (1 shl i)) != 0) {
                i++
                continue
            }

            var code = getArg(i, 0, false)
            if (code < 0) {
                code = if (mArgIndex > 0) { i++; continue } else 0
            }
            when {
                code == 0 -> {
                    mForeColor = TextStyle.COLOR_INDEX_FOREGROUND
                    mBackColor = TextStyle.COLOR_INDEX_BACKGROUND
                    mEffect = 0
                }
                code == 1 -> mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_BOLD
                code == 2 -> mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_DIM
                code == 3 -> mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_ITALIC
                code == 4 -> {
                    if (i + 1 <= mArgIndex && ((mArgsSubParamsBitSet and (1 shl (i + 1))) != 0)) {
                        i++
                        if (mArgs[i] == 0) {
                            mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE.inv()
                        } else {
                            mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                        }
                    } else {
                        mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                    }
                }
                code == 5 -> mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_BLINK
                code == 7 -> mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_INVERSE
                code == 8 -> mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE
                code == 9 -> mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH
                code == 10 || code == 11 -> { } // Exit/enter alt charset (TERM=linux) - ignore.
                code == 22 -> mEffect = mEffect and (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_DIM).inv()
                code == 23 -> mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC.inv()
                code == 24 -> mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE.inv()
                code == 25 -> mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_BLINK.inv()
                code == 27 -> mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE.inv()
                code == 28 -> mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE.inv()
                code == 29 -> mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH.inv()
                code in 30..37 -> mForeColor = code - 30
                code == 38 || code == 48 || code == 58 -> {
                    if (i + 2 > mArgIndex) { i++; continue }
                    val firstArg = mArgs[i + 1]
                    if (firstArg == 2) {
                        if (i + 4 > mArgIndex) {
                            Logger.logWarn(mClient, LOG_TAG, "Too few CSI$code;2 RGB arguments")
                        } else {
                            val red = getArg(i + 2, 0, false)
                            val green = getArg(i + 3, 0, false)
                            val blue = getArg(i + 4, 0, false)
                            if (red < 0 || green < 0 || blue < 0 || red > 255 || green > 255 || blue > 255) {
                                finishSequenceAndLogError("Invalid RGB: $red,$green,$blue")
                            } else {
                                val argbColor = 0xff_00_00_00.toInt() or (red shl 16) or (green shl 8) or blue
                                when (code) {
                                    38 -> mForeColor = argbColor
                                    48 -> mBackColor = argbColor
                                    58 -> mUnderlineColor = argbColor
                                }
                            }
                            i += 4
                        }
                    } else if (firstArg == 5) {
                        val color = getArg(i + 2, 0, false)
                        i += 2
                        if (color in 0 until TextStyle.NUM_INDEXED_COLORS) {
                            when (code) {
                                38 -> mForeColor = color
                                48 -> mBackColor = color
                                58 -> mUnderlineColor = color
                            }
                        } else {
                            if (LOG_ESCAPE_SEQUENCES) Logger.logWarn(mClient, LOG_TAG, "Invalid color index: $color")
                        }
                    } else {
                        finishSequenceAndLogError("Invalid ISO-8613-3 SGR first argument: $firstArg")
                    }
                }
                code == 39 -> mForeColor = TextStyle.COLOR_INDEX_FOREGROUND
                code in 40..47 -> mBackColor = code - 40
                code == 49 -> mBackColor = TextStyle.COLOR_INDEX_BACKGROUND
                code == 59 -> mUnderlineColor = TextStyle.COLOR_INDEX_FOREGROUND
                code in 90..97 -> mForeColor = code - 90 + 8
                code in 100..107 -> mBackColor = code - 100 + 8
                else -> {
                    if (LOG_ESCAPE_SEQUENCES) Logger.logWarn(mClient, LOG_TAG, String.format("SGR unknown code %d", code))
                }
            }
            i++
        }
    }

    private fun doOsc(b: Int) {
        when (b) {
            7 -> doOscSetTextParameters("\u0007") // Bell
            27 -> continueSequence(ESC_OSC_ESC) // Escape
            else -> collectOSCArgs(b)
        }
    }

    private fun doOscEsc(b: Int) {
        when (b) {
            '\\'.code -> doOscSetTextParameters("\u001b\\")
            else -> {
                collectOSCArgs(27)
                collectOSCArgs(b)
                continueSequence(ESC_OSC)
            }
        }
    }

    /** An Operating System Controls (OSC) Set Text Parameters. */
    private fun doOscSetTextParameters(bellOrStringTerminator: String) {
        var value = -1
        var textParameter = ""
        for (mOSCArgTokenizerIndex in 0 until mOSCOrDeviceControlArgs.length) {
            val bChar = mOSCOrDeviceControlArgs[mOSCArgTokenizerIndex]
            if (bChar == ';') {
                textParameter = mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1)
                break
            } else if (bChar in '0'..'9') {
                value = (if (value < 0) 0 else value * 10) + (bChar - '0')
            } else {
                unknownSequence(bChar.code)
                return
            }
        }

        when (value) {
            0, 1, 2 -> setTitle(textParameter)
            4 -> {
                var colorIndex = -1
                var parsingPairStart = -1
                var i = 0
                while (true) {
                    val endOfInput = i == textParameter.length
                    val bChar = if (endOfInput) ';' else textParameter[i]
                    if (bChar == ';') {
                        if (parsingPairStart < 0) {
                            parsingPairStart = i + 1
                        } else {
                            if (colorIndex < 0 || colorIndex > 255) {
                                unknownSequence(bChar.code)
                                return
                            } else {
                                mColors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i))
                                mSession.onColorsChanged()
                                colorIndex = -1
                                parsingPairStart = -1
                            }
                        }
                    } else if (parsingPairStart >= 0) {
                        // We have passed a color index and are now going through color spec.
                    } else if (parsingPairStart < 0 && (bChar in '0'..'9')) {
                        colorIndex = (if (colorIndex < 0) 0 else colorIndex * 10) + (bChar - '0')
                    } else {
                        unknownSequence(bChar.code)
                        return
                    }
                    if (endOfInput) break
                    i++
                }
            }
            10, 11, 12 -> {
                var specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10)
                var lastSemiIndex = 0
                var charIndex = 0
                while (true) {
                    val endOfInput = charIndex == textParameter.length
                    if (endOfInput || textParameter[charIndex] == ';') {
                        try {
                            val colorSpec = textParameter.substring(lastSemiIndex, charIndex)
                            if (colorSpec == "?") {
                                val rgb = mColors.mCurrentColors[specialIndex]
                                val r = (65535 * ((rgb and 0x00FF0000) shr 16)) / 255
                                val g = (65535 * ((rgb and 0x0000FF00) shr 8)) / 255
                                val bVal = (65535 * (rgb and 0x000000FF)) / 255
                                mSession.write("\u001b]$value;rgb:${String.format(Locale.US, "%04x", r)}/${String.format(Locale.US, "%04x", g)}/${String.format(Locale.US, "%04x", bVal)}$bellOrStringTerminator")
                            } else {
                                mColors.tryParseColor(specialIndex, colorSpec)
                                mSession.onColorsChanged()
                            }
                            specialIndex++
                            if (endOfInput || (specialIndex > TextStyle.COLOR_INDEX_CURSOR) || ++charIndex >= textParameter.length)
                                break
                            lastSemiIndex = charIndex
                        } catch (e: NumberFormatException) {
                            // Ignore.
                        }
                    }
                    if (!endOfInput) charIndex++
                }
            }
            52 -> { // Manipulate Selection Data
                val startIndex = textParameter.indexOf(";") + 1
                try {
                    val clipboardText = String(Base64.decode(textParameter.substring(startIndex), 0), StandardCharsets.UTF_8)
                    mSession.onCopyTextToClipboard(clipboardText)
                } catch (e: Exception) {
                    Logger.logError(mClient, LOG_TAG, "OSC Manipulate selection, invalid string '$textParameter'")
                }
            }
            104 -> {
                if (textParameter.isEmpty()) {
                    mColors.reset()
                    mSession.onColorsChanged()
                } else {
                    var lastIndex = 0
                    var charIndex = 0
                    while (true) {
                        val endOfInput = charIndex == textParameter.length
                        if (endOfInput || textParameter[charIndex] == ';') {
                            try {
                                val colorToReset = textParameter.substring(lastIndex, charIndex).toInt()
                                mColors.reset(colorToReset)
                                mSession.onColorsChanged()
                                if (endOfInput) break
                                charIndex++
                                lastIndex = charIndex
                            } catch (e: NumberFormatException) {
                                // Ignore.
                            }
                        }
                        if (!endOfInput) charIndex++
                    }
                }
            }
            110, 111, 112 -> {
                mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110))
                mSession.onColorsChanged()
            }
            119 -> { } // Reset highlight color - ignore
            else -> unknownParameter(value)
        }
        finishSequence()
    }

    private fun blockClear(sx: Int, sy: Int, w: Int) {
        blockClear(sx, sy, w, 1)
    }

    private fun blockClear(sx: Int, sy: Int, w: Int, h: Int) {
        mScreen.blockSet(sx, sy, w, h, ' '.code, getStyle())
    }

    private fun getStyle(): Long {
        return TextStyle.encode(mForeColor, mBackColor, mEffect)
    }

    /** "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode. */
    private fun doSetMode(newValue: Boolean) {
        val modeBit = getArg0(0)
        when (modeBit) {
            4 -> mInsertMode = newValue // IRM
            20 -> unknownParameter(modeBit) // LNM
            34 -> { } // Normal cursor visibility - ignore
            else -> unknownParameter(modeBit)
        }
    }

    /** NOTE: The parameters of this function respect the [DECSET_BIT_ORIGIN_MODE]. */
    private fun setCursorPosition(x: Int, y: Int) {
        val originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
        val effectiveTopMargin = if (originMode) mTopMargin else 0
        val effectiveBottomMargin = if (originMode) mBottomMargin else mRows
        val effectiveLeftMargin = if (originMode) mLeftMargin else 0
        val effectiveRightMargin = if (originMode) mRightMargin else mColumns
        val newRow = maxOf(effectiveTopMargin, minOf(effectiveTopMargin + y, effectiveBottomMargin - 1))
        val newCol = maxOf(effectiveLeftMargin, minOf(effectiveLeftMargin + x, effectiveRightMargin - 1))
        setCursorRowCol(newRow, newCol)
    }

    private fun scrollDownOneLine() {
        mScrollCounter++
        val currentStyle = getStyle()
        if (mLeftMargin != 0 || mRightMargin != mColumns) {
            mScreen.blockCopy(mLeftMargin, mTopMargin + 1, mRightMargin - mLeftMargin, mBottomMargin - mTopMargin - 1, mLeftMargin, mTopMargin)
            mScreen.blockSet(mLeftMargin, mBottomMargin - 1, mRightMargin - mLeftMargin, 1, ' '.code, currentStyle)
        } else {
            mScreen.scrollDownOneLine(mTopMargin, mBottomMargin, currentStyle)
        }
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param codePoint The code point of the character to display
     */
    private fun emitCodePoint(codePoint: Int) {
        var cp = codePoint
        mLastEmittedCodePoint = cp
        if (if (mUseLineDrawingUsesG0) mUseLineDrawingG0 else mUseLineDrawingG1) {
            // http://www.vt100.net/docs/vt102-ug/table5-15.html.
            cp = when (cp) {
                '_'.code -> ' '.code // Blank.
                '`'.code -> '◆'.code // Diamond.
                '0'.code -> '█'.code // Solid block;
                'a'.code -> '▒'.code // Checker board.
                'b'.code -> '␉'.code // Horizontal tab.
                'c'.code -> '␌'.code // Form feed.
                'd'.code -> '\r'.code // Carriage return.
                'e'.code -> '␊'.code // Linefeed.
                'f'.code -> '°'.code // Degree.
                'g'.code -> '±'.code // Plus-minus.
                'h'.code -> '\n'.code // Newline.
                'i'.code -> '␋'.code // Vertical tab.
                'j'.code -> '┘'.code // Lower right corner.
                'k'.code -> '┐'.code // Upper right corner.
                'l'.code -> '┌'.code // Upper left corner.
                'm'.code -> '└'.code // Left left corner.
                'n'.code -> '┼'.code // Crossing lines.
                'o'.code -> '⎺'.code // Horizontal line - scan 1.
                'p'.code -> '⎻'.code // Horizontal line - scan 3.
                'q'.code -> '─'.code // Horizontal line - scan 5.
                'r'.code -> '⎼'.code // Horizontal line - scan 7.
                's'.code -> '⎽'.code // Horizontal line - scan 9.
                't'.code -> '├'.code // T facing rightwards.
                'u'.code -> '┤'.code // T facing leftwards.
                'v'.code -> '┴'.code // T facing upwards.
                'w'.code -> '┬'.code // T facing downwards.
                'x'.code -> '│'.code // Vertical line.
                'y'.code -> '≤'.code // Less than or equal to.
                'z'.code -> '≥'.code // Greater than or equal to.
                '{'.code -> 'π'.code // Pi.
                '|'.code -> '≠'.code // Not equal to.
                '}'.code -> '£'.code // UK pound.
                '~'.code -> '·'.code // Centered dot.
                else -> cp
            }
        }

        val autoWrap = isDecsetInternalBitSet(DECSET_BIT_AUTOWRAP)
        val displayWidth = WcWidth.width(cp)
        val cursorInLastColumn = mCursorCol == mRightMargin - 1

        if (autoWrap) {
            if (cursorInLastColumn && ((mAboutToAutoWrap && displayWidth == 1) || displayWidth == 2)) {
                mScreen.setLineWrap(mCursorRow)
                mCursorCol = mLeftMargin
                if (mCursorRow + 1 < mBottomMargin) {
                    mCursorRow++
                } else {
                    scrollDownOneLine()
                }
            }
        } else if (cursorInLastColumn && displayWidth == 2) {
            // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return
        }

        if (mInsertMode && displayWidth > 0) {
            // Move character to right one space.
            val destCol = mCursorCol + displayWidth
            if (destCol < mRightMargin)
                mScreen.blockCopy(mCursorCol, mCursorRow, mRightMargin - destCol, 1, destCol, mCursorRow)
        }

        val offsetDueToCombiningChar = if (displayWidth <= 0 && mCursorCol > 0 && !mAboutToAutoWrap) 1 else 0
        var column = mCursorCol - offsetDueToCombiningChar

        // Fix TerminalRow.setChar() ArrayIndexOutOfBoundsException index=-1 exception reported
        if (column < 0) column = 0
        mScreen.setChar(column, mCursorRow, cp, getStyle())

        if (autoWrap && displayWidth > 0)
            mAboutToAutoWrap = (mCursorCol == mRightMargin - displayWidth)

        mCursorCol = minOf(mCursorCol + displayWidth, mRightMargin - 1)
    }

    private fun parseArg(b: Int) {
        if (b in '0'.code..'9'.code) {
            if (mArgIndex < mArgs.size) {
                val oldValue = mArgs[mArgIndex]
                val thisDigit = b - '0'.code
                var value: Int
                if (oldValue >= 0) {
                    value = oldValue * 10 + thisDigit
                } else {
                    value = thisDigit
                }
                if (value > 9999) value = 9999
                mArgs[mArgIndex] = value
            }
            continueSequence(mEscapeState)
        } else if (b == ';'.code || b == ':'.code) {
            if (mArgIndex + 1 < mArgs.size) {
                mArgIndex++
                if (b == ':'.code) {
                    mArgsSubParamsBitSet = mArgsSubParamsBitSet or (1 shl mArgIndex)
                }
            } else {
                logError("Too many parameters when in state: $mEscapeState")
            }
            continueSequence(mEscapeState)
        } else {
            unknownSequence(b)
        }
    }

    private fun getArg0(defaultValue: Int): Int = getArg(0, defaultValue, true)

    private fun getArg1(defaultValue: Int): Int = getArg(1, defaultValue, true)

    private fun getArg(index: Int, defaultValue: Int, treatZeroAsDefault: Boolean): Int {
        var result = mArgs[index]
        if (result < 0 || (result == 0 && treatZeroAsDefault)) {
            result = defaultValue
        }
        return result
    }

    private fun collectOSCArgs(b: Int) {
        if (mOSCOrDeviceControlArgs.length < MAX_OSC_STRING_LENGTH) {
            mOSCOrDeviceControlArgs.appendCodePoint(b)
            continueSequence(mEscapeState)
        } else {
            unknownSequence(b)
        }
    }

    private fun unimplementedSequence(b: Int) {
        logError("Unimplemented sequence char '${b.toChar()}' (U+${String.format("%04x", b)})")
        finishSequence()
    }

    private fun unknownSequence(b: Int) {
        logError("Unknown sequence char '${b.toChar()}' (numeric value=$b)")
        finishSequence()
    }

    private fun unknownParameter(parameter: Int) {
        logError("Unknown parameter: $parameter")
        finishSequence()
    }

    private fun logError(errorType: String) {
        if (LOG_ESCAPE_SEQUENCES) {
            val buf = StringBuilder()
            buf.append(errorType)
            buf.append(", escapeState=")
            buf.append(mEscapeState)
            var firstArg = true
            if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
            for (i in 0..mArgIndex) {
                val value = mArgs[i]
                if (value >= 0) {
                    if (firstArg) {
                        firstArg = false
                        buf.append(", args={")
                    } else {
                        buf.append(',')
                    }
                    buf.append(value)
                }
            }
            if (!firstArg) buf.append('}')
            finishSequenceAndLogError(buf.toString())
        }
    }

    private fun finishSequenceAndLogError(error: String) {
        if (LOG_ESCAPE_SEQUENCES) Logger.logWarn(mClient, LOG_TAG, error)
        finishSequence()
    }

    private fun finishSequence() {
        mEscapeState = ESC_NONE
    }

    private fun isDecsetInternalBitSet(bit: Int): Boolean {
        return (mCurrentDecSetFlags and bit) != 0
    }

    private fun setDecsetinternalBit(internalBit: Int, set: Boolean) {
        if (set) {
            // The mouse modes are mutually exclusive.
            if (internalBit == DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false)
            } else if (internalBit == DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false)
            }
        }
        if (set) {
            mCurrentDecSetFlags = mCurrentDecSetFlags or internalBit
        } else {
            mCurrentDecSetFlags = mCurrentDecSetFlags and internalBit.inv()
        }
    }

    private fun setCursorRow(row: Int) {
        mCursorRow = row
        mAboutToAutoWrap = false
    }

    private fun setCursorCol(col: Int) {
        mCursorCol = col
        mAboutToAutoWrap = false
    }

    /** Set the cursor mode, but limit it to margins if [DECSET_BIT_ORIGIN_MODE] is enabled. */
    private fun setCursorColRespectingOriginMode(col: Int) {
        setCursorPosition(col, mCursorRow)
    }

    /** TODO: Better name, distinguished from [setCursorPosition] by not regarding origin mode. */
    private fun setCursorRowCol(row: Int, col: Int) {
        mCursorRow = maxOf(0, minOf(row, mRows - 1))
        mCursorCol = maxOf(0, minOf(col, mColumns - 1))
        mAboutToAutoWrap = false
    }

    fun getScrollCounter(): Int = mScrollCounter

    fun clearScrollCounter() {
        mScrollCounter = 0
    }

    fun isAutoScrollDisabled(): Boolean = mAutoScrollDisabled

    fun toggleAutoScrollDisabled() {
        mAutoScrollDisabled = !mAutoScrollDisabled
    }

    /** Reset terminal state so user can interact with it regardless of present state. */
    fun reset() {
        setCursorStyle()
        mArgIndex = 0
        mContinueSequence = false
        mEscapeState = ESC_NONE
        mInsertMode = false
        mTopMargin = 0
        mLeftMargin = 0
        mBottomMargin = mRows
        mRightMargin = mColumns
        mAboutToAutoWrap = false
        mForeColor = TextStyle.COLOR_INDEX_FOREGROUND
        mSavedStateMain.mSavedForeColor = TextStyle.COLOR_INDEX_FOREGROUND
        mSavedStateAlt.mSavedForeColor = TextStyle.COLOR_INDEX_FOREGROUND
        mBackColor = TextStyle.COLOR_INDEX_BACKGROUND
        mSavedStateMain.mSavedBackColor = TextStyle.COLOR_INDEX_BACKGROUND
        mSavedStateAlt.mSavedBackColor = TextStyle.COLOR_INDEX_BACKGROUND
        setDefaultTabStops()

        mUseLineDrawingG0 = false
        mUseLineDrawingG1 = false
        mUseLineDrawingUsesG0 = true

        mSavedStateMain.mSavedCursorRow = 0
        mSavedStateMain.mSavedCursorCol = 0
        mSavedStateMain.mSavedEffect = 0
        mSavedStateMain.mSavedDecFlags = 0
        mSavedStateAlt.mSavedCursorRow = 0
        mSavedStateAlt.mSavedCursorCol = 0
        mSavedStateAlt.mSavedEffect = 0
        mSavedStateAlt.mSavedDecFlags = 0
        mCurrentDecSetFlags = 0
        // Initial wrap-around is not accurate but makes terminal more useful, especially on a small screen:
        setDecsetinternalBit(DECSET_BIT_AUTOWRAP, true)
        setDecsetinternalBit(DECSET_BIT_CURSOR_ENABLED, true)
        mSavedDecSetFlags = mCurrentDecSetFlags
        mSavedStateMain.mSavedDecFlags = mCurrentDecSetFlags
        mSavedStateAlt.mSavedDecFlags = mCurrentDecSetFlags

        // XXX: Should we set terminal driver back to IUTF8 with termios?
        mUtf8Index = 0
        mUtf8ToFollow = 0

        mColors.reset()
        mSession.onColorsChanged()
    }

    fun getSelectedText(x1: Int, y1: Int, x2: Int, y2: Int): String {
        return mScreen.getSelectedText(x1, y1, x2, y2)
    }

    /** Get the terminal session's title (null if not set). */
    val title: String?
        get() = mTitle

    /** Change the terminal session's title. */
    private fun setTitle(newTitle: String?) {
        val oldTitle = mTitle
        mTitle = newTitle
        if (oldTitle != newTitle) {
            mSession.titleChanged(oldTitle, newTitle)
        }
    }

    /** If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~". */
    fun paste(text: String) {
        // First: Always remove escape key and C1 control characters [0x80,0x9F]:
        var processed = text.replace("(\u001B|[\u0080-\u009F])".toRegex(), "")
        // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        processed = processed.replace("\r?\n".toRegex(), "\r")

        // Then: Implement bracketed paste mode if enabled:
        val bracketed = isDecsetInternalBitSet(DECSET_BIT_BRACKETED_PASTE_MODE)
        if (bracketed) mSession.write("\u001b[200~")
        mSession.write(processed)
        if (bracketed) mSession.write("\u001b[201~")
    }

    /** http://www.vt100.net/docs/vt510-rm/DECSC */
    class SavedScreenState {
        /** Saved state of the cursor position, Used to implement the save/restore cursor position escape sequences. */
        @JvmField var mSavedCursorRow = 0
        @JvmField var mSavedCursorCol = 0
        @JvmField var mSavedEffect = 0
        @JvmField var mSavedForeColor = 0
        @JvmField var mSavedBackColor = 0
        @JvmField var mSavedDecFlags = 0
        @JvmField var mUseLineDrawingG0 = false
        @JvmField var mUseLineDrawingG1 = false
        @JvmField var mUseLineDrawingUsesG0 = true
    }

    override fun toString(): String {
        return "TerminalEmulator[size=${mScreen.mColumns}x${mScreen.mScreenRows}, margins={$mTopMargin,$mRightMargin,$mBottomMargin,$mLeftMargin}]"
    }
}
