package com.termux.terminal

import java.util.Properties

/**
 * Color scheme for a terminal with default colors, which may be overridden (and then reset) from the shell using
 * Operating System Control (OSC) sequences.
 *
 * @see TerminalColors
 */
class TerminalColorScheme {

    @JvmField
    val mDefaultColors = IntArray(TextStyle.NUM_INDEXED_COLORS)

    init {
        reset()
    }

    private fun reset() {
        System.arraycopy(DEFAULT_COLORSCHEME, 0, mDefaultColors, 0, TextStyle.NUM_INDEXED_COLORS)
    }

    fun updateWith(props: Properties) {
        reset()
        var cursorPropExists = false
        for ((key, value) in props.entries) {
            val keyStr = key as String
            val valueStr = value as String
            val colorIndex: Int

            when {
                keyStr == "foreground" -> colorIndex = TextStyle.COLOR_INDEX_FOREGROUND
                keyStr == "background" -> colorIndex = TextStyle.COLOR_INDEX_BACKGROUND
                keyStr == "cursor" -> {
                    colorIndex = TextStyle.COLOR_INDEX_CURSOR
                    cursorPropExists = true
                }
                keyStr.startsWith("color") -> {
                    colorIndex = try {
                        keyStr.substring(5).toInt()
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("Invalid property: '$keyStr'")
                    }
                }
                else -> throw IllegalArgumentException("Invalid property: '$keyStr'")
            }

            val colorValue = TerminalColors.parse(valueStr)
            if (colorValue == 0) {
                throw IllegalArgumentException("Property '$keyStr' has invalid color: '$valueStr'")
            }

            mDefaultColors[colorIndex] = colorValue
        }

        if (!cursorPropExists) {
            setCursorColorForBackground()
        }
    }

    /**
     * If the "cursor" color is not set by user, we need to decide on the appropriate color that will
     * be visible on the current terminal background. White will not be visible on light backgrounds
     * and black won't be visible on dark backgrounds. So we find the perceived brightness of the
     * background color and if its below the threshold (too dark), we use white cursor and if its
     * above (too bright), we use black cursor.
     */
    fun setCursorColorForBackground() {
        val backgroundColor = mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]
        val brightness = TerminalColors.getPerceivedBrightnessOfColor(backgroundColor)
        if (brightness > 0) {
            mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = if (brightness < 130) {
                0xffffffff.toInt()
            } else {
                0xff000000.toInt()
            }
        }
    }

    companion object {
        /** http://upload.wikimedia.org/wikipedia/en/1/15/Xterm_256color_chart.svg, but with blue color brighter. */
        private val DEFAULT_COLORSCHEME = intArrayOf(
            // 16 original colors. First 8 are dim.
            0xff000000.toInt(), // black
            0xffcd0000.toInt(), // dim red
            0xff00cd00.toInt(), // dim green
            0xffcdcd00.toInt(), // dim yellow
            0xff6495ed.toInt(), // dim blue
            0xffcd00cd.toInt(), // dim magenta
            0xff00cdcd.toInt(), // dim cyan
            0xffe5e5e5.toInt(), // dim white
            // Second 8 are bright:
            0xff7f7f7f.toInt(), // medium grey
            0xffff0000.toInt(), // bright red
            0xff00ff00.toInt(), // bright green
            0xffffff00.toInt(), // bright yellow
            0xff5c5cff.toInt(), // light blue
            0xffff00ff.toInt(), // bright magenta
            0xff00ffff.toInt(), // bright cyan
            0xffffffff.toInt(), // bright white

            // 216 color cube, six shades of each color:
            0xff000000.toInt(), 0xff00005f.toInt(), 0xff000087.toInt(), 0xff0000af.toInt(), 0xff0000d7.toInt(), 0xff0000ff.toInt(),
            0xff005f00.toInt(), 0xff005f5f.toInt(), 0xff005f87.toInt(), 0xff005faf.toInt(), 0xff005fd7.toInt(), 0xff005fff.toInt(),
            0xff008700.toInt(), 0xff00875f.toInt(), 0xff008787.toInt(), 0xff0087af.toInt(), 0xff0087d7.toInt(), 0xff0087ff.toInt(),
            0xff00af00.toInt(), 0xff00af5f.toInt(), 0xff00af87.toInt(), 0xff00afaf.toInt(), 0xff00afd7.toInt(), 0xff00afff.toInt(),
            0xff00d700.toInt(), 0xff00d75f.toInt(), 0xff00d787.toInt(), 0xff00d7af.toInt(), 0xff00d7d7.toInt(), 0xff00d7ff.toInt(),
            0xff00ff00.toInt(), 0xff00ff5f.toInt(), 0xff00ff87.toInt(), 0xff00ffaf.toInt(), 0xff00ffd7.toInt(), 0xff00ffff.toInt(),
            0xff5f0000.toInt(), 0xff5f005f.toInt(), 0xff5f0087.toInt(), 0xff5f00af.toInt(), 0xff5f00d7.toInt(), 0xff5f00ff.toInt(),
            0xff5f5f00.toInt(), 0xff5f5f5f.toInt(), 0xff5f5f87.toInt(), 0xff5f5faf.toInt(), 0xff5f5fd7.toInt(), 0xff5f5fff.toInt(),
            0xff5f8700.toInt(), 0xff5f875f.toInt(), 0xff5f8787.toInt(), 0xff5f87af.toInt(), 0xff5f87d7.toInt(), 0xff5f87ff.toInt(),
            0xff5faf00.toInt(), 0xff5faf5f.toInt(), 0xff5faf87.toInt(), 0xff5fafaf.toInt(), 0xff5fafd7.toInt(), 0xff5fafff.toInt(),
            0xff5fd700.toInt(), 0xff5fd75f.toInt(), 0xff5fd787.toInt(), 0xff5fd7af.toInt(), 0xff5fd7d7.toInt(), 0xff5fd7ff.toInt(),
            0xff5fff00.toInt(), 0xff5fff5f.toInt(), 0xff5fff87.toInt(), 0xff5fffaf.toInt(), 0xff5fffd7.toInt(), 0xff5fffff.toInt(),
            0xff870000.toInt(), 0xff87005f.toInt(), 0xff870087.toInt(), 0xff8700af.toInt(), 0xff8700d7.toInt(), 0xff8700ff.toInt(),
            0xff875f00.toInt(), 0xff875f5f.toInt(), 0xff875f87.toInt(), 0xff875faf.toInt(), 0xff875fd7.toInt(), 0xff875fff.toInt(),
            0xff878700.toInt(), 0xff87875f.toInt(), 0xff878787.toInt(), 0xff8787af.toInt(), 0xff8787d7.toInt(), 0xff8787ff.toInt(),
            0xff87af00.toInt(), 0xff87af5f.toInt(), 0xff87af87.toInt(), 0xff87afaf.toInt(), 0xff87afd7.toInt(), 0xff87afff.toInt(),
            0xff87d700.toInt(), 0xff87d75f.toInt(), 0xff87d787.toInt(), 0xff87d7af.toInt(), 0xff87d7d7.toInt(), 0xff87d7ff.toInt(),
            0xff87ff00.toInt(), 0xff87ff5f.toInt(), 0xff87ff87.toInt(), 0xff87ffaf.toInt(), 0xff87ffd7.toInt(), 0xff87ffff.toInt(),
            0xffaf0000.toInt(), 0xffaf005f.toInt(), 0xffaf0087.toInt(), 0xffaf00af.toInt(), 0xffaf00d7.toInt(), 0xffaf00ff.toInt(),
            0xffaf5f00.toInt(), 0xffaf5f5f.toInt(), 0xffaf5f87.toInt(), 0xffaf5faf.toInt(), 0xffaf5fd7.toInt(), 0xffaf5fff.toInt(),
            0xffaf8700.toInt(), 0xffaf875f.toInt(), 0xffaf8787.toInt(), 0xffaf87af.toInt(), 0xffaf87d7.toInt(), 0xffaf87ff.toInt(),
            0xffafaf00.toInt(), 0xffafaf5f.toInt(), 0xffafaf87.toInt(), 0xffafafaf.toInt(), 0xffafafd7.toInt(), 0xffafafff.toInt(),
            0xffafd700.toInt(), 0xffafd75f.toInt(), 0xffafd787.toInt(), 0xffafd7af.toInt(), 0xffafd7d7.toInt(), 0xffafd7ff.toInt(),
            0xffafff00.toInt(), 0xffafff5f.toInt(), 0xffafff87.toInt(), 0xffafffaf.toInt(), 0xffafffd7.toInt(), 0xffafffff.toInt(),
            0xffd70000.toInt(), 0xffd7005f.toInt(), 0xffd70087.toInt(), 0xffd700af.toInt(), 0xffd700d7.toInt(), 0xffd700ff.toInt(),
            0xffd75f00.toInt(), 0xffd75f5f.toInt(), 0xffd75f87.toInt(), 0xffd75faf.toInt(), 0xffd75fd7.toInt(), 0xffd75fff.toInt(),
            0xffd78700.toInt(), 0xffd7875f.toInt(), 0xffd78787.toInt(), 0xffd787af.toInt(), 0xffd787d7.toInt(), 0xffd787ff.toInt(),
            0xffd7af00.toInt(), 0xffd7af5f.toInt(), 0xffd7af87.toInt(), 0xffd7afaf.toInt(), 0xffd7afd7.toInt(), 0xffd7afff.toInt(),
            0xffd7d700.toInt(), 0xffd7d75f.toInt(), 0xffd7d787.toInt(), 0xffd7d7af.toInt(), 0xffd7d7d7.toInt(), 0xffd7d7ff.toInt(),
            0xffd7ff00.toInt(), 0xffd7ff5f.toInt(), 0xffd7ff87.toInt(), 0xffd7ffaf.toInt(), 0xffd7ffd7.toInt(), 0xffd7ffff.toInt(),
            0xffff0000.toInt(), 0xffff005f.toInt(), 0xffff0087.toInt(), 0xffff00af.toInt(), 0xffff00d7.toInt(), 0xffff00ff.toInt(),
            0xffff5f00.toInt(), 0xffff5f5f.toInt(), 0xffff5f87.toInt(), 0xffff5faf.toInt(), 0xffff5fd7.toInt(), 0xffff5fff.toInt(),
            0xffff8700.toInt(), 0xffff875f.toInt(), 0xffff8787.toInt(), 0xffff87af.toInt(), 0xffff87d7.toInt(), 0xffff87ff.toInt(),
            0xffffaf00.toInt(), 0xffffaf5f.toInt(), 0xffffaf87.toInt(), 0xffffafaf.toInt(), 0xffffafd7.toInt(), 0xffffafff.toInt(),
            0xffffd700.toInt(), 0xffffd75f.toInt(), 0xffffd787.toInt(), 0xffffd7af.toInt(), 0xffffd7d7.toInt(), 0xffffd7ff.toInt(),
            0xffffff00.toInt(), 0xffffff5f.toInt(), 0xffffff87.toInt(), 0xffffffaf.toInt(), 0xffffffd7.toInt(), 0xffffffff.toInt(),

            // 24 grey scale ramp:
            0xff080808.toInt(), 0xff121212.toInt(), 0xff1c1c1c.toInt(), 0xff262626.toInt(), 0xff303030.toInt(), 0xff3a3a3a.toInt(),
            0xff444444.toInt(), 0xff4e4e4e.toInt(), 0xff585858.toInt(), 0xff626262.toInt(), 0xff6c6c6c.toInt(), 0xff767676.toInt(),
            0xff808080.toInt(), 0xff8a8a8a.toInt(), 0xff949494.toInt(), 0xff9e9e9e.toInt(), 0xffa8a8a8.toInt(), 0xffb2b2b2.toInt(),
            0xffbcbcbc.toInt(), 0xffc6c6c6.toInt(), 0xffd0d0d0.toInt(), 0xffdadada.toInt(), 0xffe4e4e4.toInt(), 0xffeeeeee.toInt(),

            // COLOR_INDEX_DEFAULT_FOREGROUND, COLOR_INDEX_DEFAULT_BACKGROUND and COLOR_INDEX_DEFAULT_CURSOR:
            0xffffffff.toInt(), 0xff000000.toInt(), 0xffffffff.toInt()
        )
    }
}
