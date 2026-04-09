package io.github.miuzarte.scrcpyforandroid.constants

import top.yukonga.miuix.kmp.theme.ColorSchemeMode

object ThemeModes {
    data class Option(
        val label: String,
        val mode: ColorSchemeMode,
    )

    val baseOptions = listOf(
        Option("跟随系统", ColorSchemeMode.System),
        Option("浅色", ColorSchemeMode.Light),
        Option("深色", ColorSchemeMode.Dark),
    )
}
