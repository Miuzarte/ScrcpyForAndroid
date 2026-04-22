package com.termux.terminal

import android.view.KeyEvent.*

object KeyHandler {

    const val KEYMOD_ALT = 0x80000000.toInt()
    const val KEYMOD_CTRL = 0x40000000
    const val KEYMOD_SHIFT = 0x20000000
    const val KEYMOD_NUM_LOCK = 0x10000000

    private val TERMCAP_TO_KEYCODE = HashMap<String, Int>().apply {
        // terminfo: http://pubs.opengroup.org/onlinepubs/7990989799/xcurses/terminfo.html
        // termcap: http://man7.org/linux/man-pages/man5/termcap.5.html
        put("%i", KEYMOD_SHIFT or KEYCODE_DPAD_RIGHT)
        put("#2", KEYMOD_SHIFT or KEYCODE_MOVE_HOME) // Shifted home
        put("#4", KEYMOD_SHIFT or KEYCODE_DPAD_LEFT)
        put("*7", KEYMOD_SHIFT or KEYCODE_MOVE_END) // Shifted end key

        put("k1", KEYCODE_F1)
        put("k2", KEYCODE_F2)
        put("k3", KEYCODE_F3)
        put("k4", KEYCODE_F4)
        put("k5", KEYCODE_F5)
        put("k6", KEYCODE_F6)
        put("k7", KEYCODE_F7)
        put("k8", KEYCODE_F8)
        put("k9", KEYCODE_F9)
        put("k;", KEYCODE_F10)
        put("F1", KEYCODE_F11)
        put("F2", KEYCODE_F12)
        put("F3", KEYMOD_SHIFT or KEYCODE_F1)
        put("F4", KEYMOD_SHIFT or KEYCODE_F2)
        put("F5", KEYMOD_SHIFT or KEYCODE_F3)
        put("F6", KEYMOD_SHIFT or KEYCODE_F4)
        put("F7", KEYMOD_SHIFT or KEYCODE_F5)
        put("F8", KEYMOD_SHIFT or KEYCODE_F6)
        put("F9", KEYMOD_SHIFT or KEYCODE_F7)
        put("FA", KEYMOD_SHIFT or KEYCODE_F8)
        put("FB", KEYMOD_SHIFT or KEYCODE_F9)
        put("FC", KEYMOD_SHIFT or KEYCODE_F10)
        put("FD", KEYMOD_SHIFT or KEYCODE_F11)
        put("FE", KEYMOD_SHIFT or KEYCODE_F12)

        put("kb", KEYCODE_DEL) // backspace key

        put("kd", KEYCODE_DPAD_DOWN) // terminfo=kcud1, down-arrow key
        put("kh", KEYCODE_MOVE_HOME)
        put("kl", KEYCODE_DPAD_LEFT)
        put("kr", KEYCODE_DPAD_RIGHT)

        // K1=Upper left of keypad:
        // t_K1 <kHome> keypad home key
        // t_K3 <kPageUp> keypad page-up key
        // t_K4 <kEnd> keypad end key
        // t_K5 <kPageDown> keypad page-down key
        put("K1", KEYCODE_MOVE_HOME)
        put("K3", KEYCODE_PAGE_UP)
        put("K4", KEYCODE_MOVE_END)
        put("K5", KEYCODE_PAGE_DOWN)

        put("ku", KEYCODE_DPAD_UP)

        put("kB", KEYMOD_SHIFT or KEYCODE_TAB) // termcap=kB, terminfo=kcbt: Back-tab
        put("kD", KEYCODE_FORWARD_DEL) // terminfo=kdch1, delete-character key
        put("kDN", KEYMOD_SHIFT or KEYCODE_DPAD_DOWN) // non-standard shifted arrow down
        put("kF", KEYMOD_SHIFT or KEYCODE_DPAD_DOWN) // terminfo=kind, scroll-forward key
        put("kI", KEYCODE_INSERT)
        put("kN", KEYCODE_PAGE_UP)
        put("kP", KEYCODE_PAGE_DOWN)
        put("kR", KEYMOD_SHIFT or KEYCODE_DPAD_UP) // terminfo=kri, scroll-backward key
        put("kUP", KEYMOD_SHIFT or KEYCODE_DPAD_UP) // non-standard shifted up

        put("@7", KEYCODE_MOVE_END)
        put("@8", KEYCODE_NUMPAD_ENTER)
    }

    @JvmStatic
    fun getCodeFromTermcap(termcap: String, cursorKeysApplication: Boolean, keypadApplication: Boolean): String? {
        val keyCodeAndMod = TERMCAP_TO_KEYCODE[termcap] ?: return null
        var keyCode = keyCodeAndMod
        var keyMod = 0
        if ((keyCode and KEYMOD_SHIFT) != 0) {
            keyMod = keyMod or KEYMOD_SHIFT
            keyCode = keyCode and KEYMOD_SHIFT.inv()
        }
        if ((keyCode and KEYMOD_CTRL) != 0) {
            keyMod = keyMod or KEYMOD_CTRL
            keyCode = keyCode and KEYMOD_CTRL.inv()
        }
        if ((keyCode and KEYMOD_ALT) != 0) {
            keyMod = keyMod or KEYMOD_ALT
            keyCode = keyCode and KEYMOD_ALT.inv()
        }
        if ((keyCode and KEYMOD_NUM_LOCK) != 0) {
            keyMod = keyMod or KEYMOD_NUM_LOCK
            keyCode = keyCode and KEYMOD_NUM_LOCK.inv()
        }
        return getCode(keyCode, keyMod, cursorKeysApplication, keypadApplication)
    }

    @JvmStatic
    fun getCode(keyCode: Int, keyMode: Int, cursorApp: Boolean, keypadApplication: Boolean): String? {
        val numLockOn = (keyMode and KEYMOD_NUM_LOCK) != 0
        val keyMod = keyMode and KEYMOD_NUM_LOCK.inv()
        return when (keyCode) {
            KEYCODE_DPAD_CENTER -> "\r"

            KEYCODE_DPAD_UP -> if (keyMod == 0) (if (cursorApp) "\u001bOA" else "\u001b[A") else transformForModifiers("\u001b[1", keyMod, 'A')
            KEYCODE_DPAD_DOWN -> if (keyMod == 0) (if (cursorApp) "\u001bOB" else "\u001b[B") else transformForModifiers("\u001b[1", keyMod, 'B')
            KEYCODE_DPAD_RIGHT -> if (keyMod == 0) (if (cursorApp) "\u001bOC" else "\u001b[C") else transformForModifiers("\u001b[1", keyMod, 'C')
            KEYCODE_DPAD_LEFT -> if (keyMod == 0) (if (cursorApp) "\u001bOD" else "\u001b[D") else transformForModifiers("\u001b[1", keyMod, 'D')

            KEYCODE_MOVE_HOME -> if (keyMod == 0) (if (cursorApp) "\u001bOH" else "\u001b[H") else transformForModifiers("\u001b[1", keyMod, 'H')
            KEYCODE_MOVE_END -> if (keyMod == 0) (if (cursorApp) "\u001bOF" else "\u001b[F") else transformForModifiers("\u001b[1", keyMod, 'F')

            KEYCODE_F1 -> if (keyMod == 0) "\u001bOP" else transformForModifiers("\u001b[1", keyMod, 'P')
            KEYCODE_F2 -> if (keyMod == 0) "\u001bOQ" else transformForModifiers("\u001b[1", keyMod, 'Q')
            KEYCODE_F3 -> if (keyMod == 0) "\u001bOR" else transformForModifiers("\u001b[1", keyMod, 'R')
            KEYCODE_F4 -> if (keyMod == 0) "\u001bOS" else transformForModifiers("\u001b[1", keyMod, 'S')
            KEYCODE_F5 -> transformForModifiers("\u001b[15", keyMod, '~')
            KEYCODE_F6 -> transformForModifiers("\u001b[17", keyMod, '~')
            KEYCODE_F7 -> transformForModifiers("\u001b[18", keyMod, '~')
            KEYCODE_F8 -> transformForModifiers("\u001b[19", keyMod, '~')
            KEYCODE_F9 -> transformForModifiers("\u001b[20", keyMod, '~')
            KEYCODE_F10 -> transformForModifiers("\u001b[21", keyMod, '~')
            KEYCODE_F11 -> transformForModifiers("\u001b[23", keyMod, '~')
            KEYCODE_F12 -> transformForModifiers("\u001b[24", keyMod, '~')

            KEYCODE_SYSRQ -> "\u001b[32~" // Sys Request / Print
            KEYCODE_BREAK -> "\u001b[34~" // Pause/Break

            KEYCODE_ESCAPE, KEYCODE_BACK -> "\u001b"

            KEYCODE_INSERT -> transformForModifiers("\u001b[2", keyMod, '~')
            KEYCODE_FORWARD_DEL -> transformForModifiers("\u001b[3", keyMod, '~')

            KEYCODE_PAGE_UP -> transformForModifiers("\u001b[5", keyMod, '~')
            KEYCODE_PAGE_DOWN -> transformForModifiers("\u001b[6", keyMod, '~')
            KEYCODE_DEL -> {
                val prefix = if ((keyMod and KEYMOD_ALT) == 0) "" else "\u001b"
                prefix + if ((keyMod and KEYMOD_CTRL) == 0) "\u007F" else "\u0008"
            }
            KEYCODE_NUM_LOCK -> if (keypadApplication) "\u001bOP" else null
            KEYCODE_SPACE -> if ((keyMod and KEYMOD_CTRL) == 0) null else "\u0000"
            KEYCODE_TAB -> if ((keyMod and KEYMOD_SHIFT) == 0) "\t" else "\u001b[Z"
            KEYCODE_ENTER -> if ((keyMod and KEYMOD_ALT) == 0) "\r" else "\u001b\r"

            KEYCODE_NUMPAD_ENTER -> if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'M') else "\n"
            KEYCODE_NUMPAD_MULTIPLY -> if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'j') else "*"
            KEYCODE_NUMPAD_ADD -> if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'k') else "+"
            KEYCODE_NUMPAD_COMMA -> ","
            KEYCODE_NUMPAD_DOT -> if (numLockOn) (if (keypadApplication) "\u001bOn" else ".") else transformForModifiers("\u001b[3", keyMod, '~')
            KEYCODE_NUMPAD_SUBTRACT -> if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'm') else "-"
            KEYCODE_NUMPAD_DIVIDE -> if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'o') else "/"
            KEYCODE_NUMPAD_0 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'p') else "0") else transformForModifiers("\u001b[2", keyMod, '~')
            KEYCODE_NUMPAD_1 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'q') else "1") else (if (keyMod == 0) (if (cursorApp) "\u001bOF" else "\u001b[F") else transformForModifiers("\u001b[1", keyMod, 'F'))
            KEYCODE_NUMPAD_2 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'r') else "2") else (if (keyMod == 0) (if (cursorApp) "\u001bOB" else "\u001b[B") else transformForModifiers("\u001b[1", keyMod, 'B'))
            KEYCODE_NUMPAD_3 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 's') else "3") else "\u001b[6~"
            KEYCODE_NUMPAD_4 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 't') else "4") else (if (keyMod == 0) (if (cursorApp) "\u001bOD" else "\u001b[D") else transformForModifiers("\u001b[1", keyMod, 'D'))
            KEYCODE_NUMPAD_5 -> if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'u') else "5"
            KEYCODE_NUMPAD_6 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'v') else "6") else (if (keyMod == 0) (if (cursorApp) "\u001bOC" else "\u001b[C") else transformForModifiers("\u001b[1", keyMod, 'C'))
            KEYCODE_NUMPAD_7 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'w') else "7") else (if (keyMod == 0) (if (cursorApp) "\u001bOH" else "\u001b[H") else transformForModifiers("\u001b[1", keyMod, 'H'))
            KEYCODE_NUMPAD_8 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'x') else "8") else (if (keyMod == 0) (if (cursorApp) "\u001bOA" else "\u001b[A") else transformForModifiers("\u001b[1", keyMod, 'A'))
            KEYCODE_NUMPAD_9 -> if (numLockOn) (if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'y') else "9") else "\u001b[5~"
            KEYCODE_NUMPAD_EQUALS -> if (keypadApplication) transformForModifiers("\u001bO", keyMod, 'X') else "="

            else -> null
        }
    }

    private fun transformForModifiers(start: String, keymod: Int, lastChar: Char): String {
        val modifier = when (keymod) {
            KEYMOD_SHIFT -> 2
            KEYMOD_ALT -> 3
            KEYMOD_SHIFT or KEYMOD_ALT -> 4
            KEYMOD_CTRL -> 5
            KEYMOD_SHIFT or KEYMOD_CTRL -> 6
            KEYMOD_ALT or KEYMOD_CTRL -> 7
            KEYMOD_SHIFT or KEYMOD_ALT or KEYMOD_CTRL -> 8
            else -> return start + lastChar
        }
        return "$start;$modifier$lastChar"
    }
}
