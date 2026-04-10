package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun FloatingOverlayScreen(
    scrcpy: Scrcpy,
    nativeCore: NativeCoreFacade,
) {
    val currentSession by scrcpy.currentSessionState.collectAsState()
    val session = currentSession

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (session == null) {
            return@BoxWithConstraints
        }

        val sessionAspect = if (session.height == 0) {
            16f / 9f
        } else {
            session.width.toFloat() / session.height.toFloat()
        }
        val containerAspect = maxWidth.value / maxHeight.value
        val fittedModifier = if (sessionAspect > containerAspect) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(sessionAspect)
        } else {
            Modifier
                .fillMaxHeight()
                .aspectRatio(sessionAspect)
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(fittedModifier),
        ) {
            FloatingVideoSurface(
                modifier = Modifier.fillMaxSize(),
                nativeCore = nativeCore,
                session = session,
            )
        }
    }
}

@Composable
private fun FloatingVideoSurface(
    modifier: Modifier,
    nativeCore: NativeCoreFacade,
    session: Scrcpy.Session.SessionInfo?,
) {
    var currentSurface by remember { mutableStateOf<Surface?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(session, currentSurface) {
        val surface = currentSurface
        if (session != null && surface != null && surface.isValid) {
            nativeCore.attachVideoSurface(surface)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val surface = currentSurface
            if (surface != null) {
                scope.launch(Dispatchers.IO) {
                    nativeCore.detachVideoSurface(surface, releaseDecoder = false)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        @SuppressLint("Recycle")
                        val newSurface = Surface(surfaceTexture)
                        currentSurface = newSurface
                        if (session != null) {
                            scope.launch {
                                nativeCore.attachVideoSurface(newSurface)
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        val surface = currentSurface
                        if (surface != null) {
                            scope.launch(Dispatchers.IO) {
                                nativeCore.detachVideoSurface(surface, releaseDecoder = false)
                            }
                            surface.release()
                            currentSurface = null
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
        update = {},
    )
}
