package io.github.miuzarte.scrcpyforandroid.nativecore

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicLong

/**
 * Decoder always renders into a persistent SurfaceTexture-backed Surface.
 * UI surfaces are display-only targets fed from that persistent texture via EGL.
 */
class PersistentVideoRenderer {
    private val tag = "PersistentVideoRenderer"
    private val renderThread = HandlerThread("PersistentVideoRenderer").apply { start() }
    private val handler = Handler(renderThread.looper)

    @Volatile
    private var initialized = false

    @Volatile
    private var released = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var eglPbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var displayEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var displaySurface: Surface? = null
    private var displaySurfaceId: Int? = null
    private var recordEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var recordSurface: Surface? = null
    private var recordSurfaceId: Int? = null
    private var recordWidth: Int = 0
    private var recordHeight: Int = 0
    private var onRecordFrameRendered: ((Long) -> Unit)? = null

    private var oesTextureId = 0
    private var decoderSurfaceTexture: SurfaceTexture? = null
    private var decoderSurface: Surface? = null
    private val stMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val frameAvailableCount = AtomicLong(0)
    private val frameConsumedCount = AtomicLong(0)
    private val frameRenderedCount = AtomicLong(0)

    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var samplerHandle = 0

    private val initLock = Any()

    fun getDecoderSurface(): Surface {
        ensureInitialized()
        synchronized(initLock) {
            return requireNotNull(decoderSurface) { "decoderSurface not initialized" }
        }
    }

    fun attachDisplaySurface(surface: Surface) {
        ensureInitialized()
        val newId = System.identityHashCode(surface)
        if (displaySurfaceId == newId) return
        Log.i(tag, "attachDisplaySurface(): request surfaceId=$newId old=${displaySurfaceId}")
        handler.post {
            if (released || !surface.isValid) return@post
            releaseDisplaySurfaceLocked()
            displaySurface = surface
            displaySurfaceId = newId
            displayEglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                eglConfig,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            Log.i(tag, "attachDisplaySurface(): attached surfaceId=$newId")
            drawFrame()
        }
    }

    fun detachDisplaySurface(surface: Surface? = null, releaseSurface: Boolean = false) {
        val requestId = surface?.let { System.identityHashCode(it) }
        Log.i(
            tag,
            "detachDisplaySurface(): request surfaceId=$requestId releaseSurface=$releaseSurface current=${displaySurfaceId}"
        )
        handler.post {
            if (released) return@post
            if (requestId != null && requestId != displaySurfaceId) return@post
            releaseDisplaySurfaceLocked()
            if (releaseSurface) {
                runCatching { surface?.release() }
            }
        }
    }

    fun attachRecordSurface(
        surface: Surface,
        width: Int,
        height: Int,
        onFrameRendered: ((Long) -> Unit)? = null,
    ) {
        ensureInitialized()
        val newId = System.identityHashCode(surface)
        if (recordSurfaceId == newId &&
            recordWidth == width &&
            recordHeight == height
        ) {
            onRecordFrameRendered = onFrameRendered
            return
        }
        Log.i(tag, "attachRecordSurface(): request surfaceId=$newId size=${width}x$height")
        handler.post {
            if (released || !surface.isValid) return@post
            releaseRecordSurfaceLocked()
            recordSurface = surface
            recordSurfaceId = newId
            recordWidth = width.coerceAtLeast(1)
            recordHeight = height.coerceAtLeast(1)
            onRecordFrameRendered = onFrameRendered
            recordEglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                eglConfig,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            Log.i(tag, "attachRecordSurface(): attached surfaceId=$newId")
            drawFrame()
        }
    }

    fun detachRecordSurface(surface: Surface? = null, releaseSurface: Boolean = false) {
        val requestId = surface?.let { System.identityHashCode(it) }
        Log.i(
            tag,
            "detachRecordSurface(): request surfaceId=$requestId releaseSurface=$releaseSurface current=$recordSurfaceId"
        )
        handler.post {
            if (released) return@post
            if (requestId != null && requestId != recordSurfaceId) return@post
            releaseRecordSurfaceLocked()
            if (releaseSurface) {
                runCatching { surface?.release() }
            }
        }
    }

    fun release() {
        handler.post {
            if (released) return@post
            released = true
            releaseDisplaySurfaceLocked()
            releaseRecordSurfaceLocked()
            runCatching { decoderSurface?.release() }
            decoderSurface = null
            runCatching { decoderSurfaceTexture?.release() }
            decoderSurfaceTexture = null
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            if (oesTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                oesTextureId = 0
            }
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                if (eglPbufferSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglPbufferSurface)
                }
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglPbufferSurface = EGL14.EGL_NO_SURFACE
            eglConfig = null
            renderThread.quitSafely()
        }
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            val latch = java.util.concurrent.CountDownLatch(1)
            handler.post {
                initializeLocked()
                initialized = true
                latch.countDown()
            }
            latch.await()
        }
    }

    private fun initializeLocked() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1))

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 4,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0))
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(0x3098, 2, EGL14.EGL_NONE),
            0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        eglPbufferSurface = EGL14.eglCreatePbufferSurface(
            eglDisplay,
            eglConfig,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
            0
        )
        check(eglPbufferSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(eglDisplay, eglPbufferSurface, eglPbufferSurface, eglContext))

        oesTextureId = createExternalTexture()
        decoderSurfaceTexture = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener({
                val n = frameAvailableCount.incrementAndGet()
                if (n == 1L || n % 120L == 0L) {
                    Log.i(
                        tag,
                        "onFrameAvailable(): available=$n consumed=${frameConsumedCount.get()} rendered=${frameRenderedCount.get()} display=${displaySurfaceId != null}"
                    )
                }
                drawFrame()
            }, handler)
        }
        decoderSurface = Surface(decoderSurfaceTexture)
        Log.i(tag, "initializeLocked(): decoder surface created")

        Matrix.setIdentityM(stMatrix, 0)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.rotateM(mvpMatrix, 0, 180f, 0f, 0f, 1f)
        Matrix.scaleM(mvpMatrix, 0, -1f, 1f, 1f)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        stMatrixHandle = GLES20.glGetUniformLocation(program, "uStMatrix")
        samplerHandle = GLES20.glGetUniformLocation(program, "sTexture")
    }

    private fun drawFrame() {
        if (released) return
        val surfaceTexture = decoderSurfaceTexture ?: return
        if (eglPbufferSurface == EGL14.EGL_NO_SURFACE) return

        // Always consume decoder frames on the persistent context, even if there is no
        // visible output surface right now. Otherwise the producer side can stall.
        EGL14.eglMakeCurrent(eglDisplay, eglPbufferSurface, eglPbufferSurface, eglContext)
        runCatching { surfaceTexture.updateTexImage() }
            .onSuccess {
                val consumed = frameConsumedCount.incrementAndGet()
                if (consumed == 1L || consumed % 120L == 0L) {
                    Log.i(
                        tag,
                        "drawFrame(): consumed=$consumed available=${frameAvailableCount.get()} rendered=${frameRenderedCount.get()} display=${displaySurfaceId != null}"
                    )
                }
            }
            .onFailure { Log.w(tag, "updateTexImage failed", it) }
        surfaceTexture.getTransformMatrix(stMatrix)
        val frameTimestampNs = surfaceTexture.timestamp

        if (recordEglSurface != EGL14.EGL_NO_SURFACE) {
            renderToSurface(
                eglSurface = recordEglSurface,
                width = recordWidth,
                height = recordHeight,
                presentationTimeNs = frameTimestampNs,
            )
            onRecordFrameRendered?.invoke(frameTimestampNs)
        }

        if (displayEglSurface != EGL14.EGL_NO_SURFACE) {
            val width = IntArray(1)
            val height = IntArray(1)
            EGL14.eglQuerySurface(eglDisplay, displayEglSurface, EGL14.EGL_WIDTH, width, 0)
            EGL14.eglQuerySurface(eglDisplay, displayEglSurface, EGL14.EGL_HEIGHT, height, 0)
            renderToSurface(
                eglSurface = displayEglSurface,
                width = width[0].coerceAtLeast(1),
                height = height[0].coerceAtLeast(1),
            )
            val rendered = frameRenderedCount.incrementAndGet()
            if (rendered == 1L || rendered % 120L == 0L) {
                Log.i(
                    tag,
                    "drawFrame(): rendered=$rendered consumed=${frameConsumedCount.get()} available=${frameAvailableCount.get()} viewport=${width[0]}x${height[0]}"
                )
            }
        }
    }

    private fun releaseDisplaySurfaceLocked() {
        if (displayEglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, displayEglSurface)
            displayEglSurface = EGL14.EGL_NO_SURFACE
        }
        if (displaySurfaceId != null) {
            Log.i(tag, "releaseDisplaySurfaceLocked(): surfaceId=$displaySurfaceId")
        }
        displaySurface = null
        displaySurfaceId = null
    }

    private fun releaseRecordSurfaceLocked() {
        if (recordEglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, recordEglSurface)
            recordEglSurface = EGL14.EGL_NO_SURFACE
        }
        if (recordSurfaceId != null) {
            Log.i(tag, "releaseRecordSurfaceLocked(): surfaceId=$recordSurfaceId")
        }
        recordSurface = null
        recordSurfaceId = null
        recordWidth = 0
        recordHeight = 0
        onRecordFrameRendered = null
    }

    private fun renderToSurface(
        eglSurface: EGLSurface,
        width: Int,
        height: Int,
        presentationTimeNs: Long? = null,
    ) {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)
        GLES20.glUniform1i(samplerHandle, 0)

        VERTICES.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, VERTICES)
        GLES20.glEnableVertexAttribArray(positionHandle)
        VERTICES.position(2)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, VERTICES)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        presentationTimeNs?.let { EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, it) }
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        return textures[0]
    }

    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
    }

    companion object {
        private val VERTICES = java.nio.ByteBuffer.allocateDirect(4 * 4 * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(
                    floatArrayOf(
                        -1f, -1f, 0f, 1f,
                        1f, -1f, 1f, 1f,
                        -1f, 1f, 0f, 0f,
                        1f, 1f, 1f, 0f,
                    )
                )
                position(0)
            }

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uMvpMatrix;
            uniform mat4 uStMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
                vTexCoord = (uStMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
