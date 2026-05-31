package io.github.miuzarte.scrcpyforandroid.services

import android.os.Environment
import java.io.File

object PublicDirs {
    private const val ROOT_DIRECTORY = "Scrcpy"

    fun transferDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            ROOT_DIRECTORY,
        )
    }

    fun recordDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            ROOT_DIRECTORY,
        )
    }
}
