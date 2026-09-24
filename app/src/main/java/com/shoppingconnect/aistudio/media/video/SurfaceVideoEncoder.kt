package com.shoppingconnect.aistudio.media.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class EncoderChoice(val mime: String, val width: Int, val height: Int, val codecName: String, val hardware: Boolean)

object CodecSupport {
    /**
     * Every encoder that claims to support the request, best first: requested size before smaller
     * sizes, HEVC before AVC when preferred, hardware before software.
     */
    fun candidates(preferHevc: Boolean, width: Int, height: Int, fps: Int): List<EncoderChoice> {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
        val mimes = if (preferHevc) listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC) else listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
        val sizes = listOf(width to height, 1080 to 1920, 720 to 1280).filter { it.first <= width }.distinct()
        val out = mutableListOf<EncoderChoice>()
        for ((w, h) in sizes) for (mime in mimes) {
            val infos = list.filter { info -> info.supportedTypes.any { it.equals(mime, true) } }
                .sortedByDescending { it.isHardwareAccelerated }
            for (info in infos) {
                val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
                val vc = caps.videoCapabilities ?: continue
                val surfaceOk = caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                if (surfaceOk && vc.isSizeSupported(w, h) && runCatching { vc.areSizeAndRateSupported(w, h, fps.toDouble()) }.getOrDefault(true)) {
                    out += EncoderChoice(mime, w, h, info.name, info.isHardwareAccelerated)
                }
            }
        }
        return out
    }

    fun choose(preferHevc: Boolean, width: Int, height: Int, fps: Int): EncoderChoice =
        candidates(preferHevc, width, height, fps).firstOrNull() ?: throw noEncoder(null)

    /**
     * Opens the first candidate whose encoder actually configures and starts. A codec can pass the
     * capability checks and still fail in configure()/start() (vendor quirks, instance limits), so a
     * failing hardware encoder falls through to the next candidate — typically the software one.
     */
    fun <T> openFirst(candidates: List<EncoderChoice>, onFallback: (EncoderChoice, Throwable) -> Unit = { _, _ -> }, open: (EncoderChoice) -> T): Pair<EncoderChoice, T> {
        var last: Throwable? = null
        for (c in candidates) {
            try {
                return c to open(c)
            } catch (e: Exception) {
                last = e
                onFallback(c, e)
            }
        }
        throw noEncoder(last)
    }

    private fun noEncoder(cause: Throwable?) = AppException(ErrorKind.RenderFailure, "이 기기에서 지원하는 영상 인코더를 찾지 못했습니다.", cause)
}

/**
 * H.264/HEVC encoder fed through its input Surface with OpenGL ES: each frame is drawn with the
 * Canvas into a Bitmap, uploaded as a texture and presented with an explicit timestamp
 * (eglPresentationTimeANDROID), giving exact frame timing independent of render speed.
 * Must be used from a single thread.
 */
class SurfaceVideoEncoder(val choice: EncoderChoice, fps: Int, bitrate: Int) {
    val codec: MediaCodec = MediaCodec.createByCodecName(choice.codecName)
    private var inputSurface: Surface? = null
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private var textureAllocated = false
    private val quad: FloatBuffer

    init {
        val fmt = MediaFormat.createVideoFormat(choice.mime, choice.width, choice.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        // x, y, u, v — bitmap row 0 maps to the top of the frame
        quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)); position(0)
        }
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()
            setupEgl()
            setupGl()
        } catch (e: Throwable) {
            // Free the codec instance so a fallback encoder can be opened (devices cap concurrent codecs).
            release()
            throw e
        }
    }

    private fun setupEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0) { "no EGL config" }
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], inputSurface!!, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent failed" }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "shader compile: ${GLES20.glGetShaderInfoLog(s)}" }
        return s
    }

    private fun setupGl() {
        val vs = compile(GLES20.GL_VERTEX_SHADER, "attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){ vUv=aUv; gl_Position=vec4(aPos,0.0,1.0); }")
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){ gl_FragColor=texture2D(uTex,vUv); }")
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs); GLES20.glLinkProgram(program)
        val ok = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "program link failed" }
        val tex = IntArray(1); GLES20.glGenTextures(1, tex, 0); texture = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    /** Uploads [frame] and submits it to the encoder with presentation time [ptsNs]. */
    fun submit(frame: Bitmap, ptsNs: Long) {
        GLES20.glViewport(0, 0, choice.width, choice.height)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        if (!textureAllocated) { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frame, 0); textureAllocated = true }
        else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, frame)
        val pos = GLES20.glGetAttribLocation(program, "aPos")
        val uv = GLES20.glGetAttribLocation(program, "aUv")
        quad.position(0); GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(pos)
        quad.position(2); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun signalEnd() = codec.signalEndOfInputStream()

    fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        inputSurface?.release()
        inputSurface = null
    }

    private companion object { const val EGL_RECORDABLE_ANDROID = 0x3142 }
}
