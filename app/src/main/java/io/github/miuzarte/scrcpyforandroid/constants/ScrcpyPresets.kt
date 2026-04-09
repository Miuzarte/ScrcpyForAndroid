package io.github.miuzarte.scrcpyforandroid.constants

/**
 * A generic preset class that holds a list of preset values and provides
 * methods to find the index of a value or its nearest match.
 *
 * @param T The type of preset values (typically Int)
 * @param values The list of preset values
 */
class Preset<T : Comparable<T>>(val values: List<T>) {
    val lastIndex: Int get() = values.lastIndex
    val size: Int get() = values.size
    val indices: IntRange get() = values.indices
    
    operator fun get(index: Int): T = values[index]
    
    /**
     * Find the index of the exact value or the nearest preset.
     * For numeric types, finds the closest value by absolute difference.
     */
    fun indexOfOrNearest(value: T): Int {
        val exact = values.indexOf(value)
        if (exact >= 0) return exact
        
        // For numeric types, find nearest by comparing
        return values.withIndex().minByOrNull { (_, preset) ->
            when {
                preset is Number && value is Number -> {
                    kotlin.math.abs(preset.toDouble() - value.toDouble())
                }
                else -> if (preset > value) 1.0 else -1.0
            }
        }?.index ?: 0
    }
}

/**
 * Extension function for Int presets to find index from Int value.
 */
fun Preset<Int>.indexOfOrNearest(raw: Int): Int {
    val exact = values.indexOf(raw)
    if (exact >= 0) return exact
    val nearest = values.withIndex().minByOrNull { (_, preset) -> 
        kotlin.math.abs(preset - raw) 
    }
    return nearest?.index ?: 0
}

/**
 * Extension function for Int presets to find index from String value.
 */
fun Preset<Int>.indexOfOrNearest(raw: String): Int {
    if (raw.isBlank()) return 0
    val value = raw.toIntOrNull() ?: return 0
    return indexOfOrNearest(value)
}

object ScrcpyPresets {
    val MaxSize = Preset(listOf(0, 720, 1080, 1280, 1600, 1920, 2160, 2560, 3200, 3840)) // px
    val MaxFPS = Preset(listOf(0, 24, 30, 45, 60, 90, 120)) // fps
    val AudioBitRate = Preset(listOf(0, 32, 64, 96, 128, 160, 192, 256, 320, 384, 512)) // Kbps
    val CameraFps = Preset(listOf(0, 10, 15, 24, 30, 60, 120, 240, 480, 960)) // fps
}
