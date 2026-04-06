package io.github.miuzarte.scrcpyforandroid.models

class ScrcpyOptions {
    data class NewDisplay(val width: Int? = null, val height: Int? = null, val dpi: Int? = null) {
        override fun toString() = buildString {
            if (width != null && width > 0 && height != null && height > 0)
                append("${width}x${height}")
            if (dpi != null && dpi > 0)
                append("/$dpi")
        }

        companion object {
            fun parseFrom(width: String, height: String, dpi: String) =
                NewDisplay(
                    width = width.toIntOrNull()?.takeIf { it > 0 },
                    height = height.toIntOrNull()?.takeIf { it > 0 },
                    dpi = dpi.toIntOrNull()?.takeIf { it > 0 },
                )

            fun parseFrom(input: String): NewDisplay {
                // [<width>x<height>][/<dpi>]

                val trimmed = input.trim()
                if (trimmed.isEmpty()) return NewDisplay()

                val slashIndex = trimmed.indexOf('/')
                val sizePart = if (slashIndex >= 0) trimmed.substring(0, slashIndex) else trimmed
                val dpiPart = if (slashIndex >= 0) trimmed.substring(slashIndex + 1) else ""

                val xIndex = sizePart.indexOf('x')
                var widthPart = ""
                var heightPart = ""
                if (xIndex >= 0) {
                    widthPart = sizePart.substring(0, xIndex)
                    heightPart = sizePart.substring(xIndex + 1)
                }

                return parseFrom(widthPart, heightPart, dpiPart)
            }
        }
    }

    data class Crop(
        val width: Int? = null,
        val height: Int? = null,
        val x: Int? = null,
        val y: Int? = null,
    ) {
        override fun toString() =
            if (width != null && width > 0
                && height != null && height > 0
                && x != null && x > 0
                && y != null && y > 0
            ) "$width:$height:$x:$y"
            else ""

        companion object {
            fun parseFrom(width: String, height: String, x: String, y: String) =
                Crop(
                    width = width.toIntOrNull()?.takeIf { it > 0 },
                    height = height.toIntOrNull()?.takeIf { it > 0 },
                    x = x.toIntOrNull()?.takeIf { it > 0 },
                    y = y.toIntOrNull()?.takeIf { it > 0 },
                )

            fun parseFrom(input: String): Crop {
                // width:height:x:y
                val parts = input.split(':', limit = 4)
                return if (parts.size >= 4)
                    parseFrom(parts[0], parts[1], parts[2], parts[3])
                else Crop()
            }
        }
    }
}
