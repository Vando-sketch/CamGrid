package io.github.vandosketch.camgrid.player

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import java.util.concurrent.CountDownLatch
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * Draws WebRTC frames into a [TextureView] through libwebrtc's [EglRenderer], for a zoomed
 * fullscreen picture: a TextureView is drawn by Compose like any other content, so it can be
 * laid out larger than the screen and clipped. A SurfaceViewRenderer is composited outside the
 * view hierarchy and is never clipped, so it only serves the unzoomed picture.
 *
 * Set it as the view's [TextureView.SurfaceTextureListener] and attach it to the stream. Each
 * frame is cropped to the surface's aspect ratio and fills it; [onVideoSize] reports the
 * (rotated) frame size on the main thread, so the view can be given the video's aspect ratio
 * for a letterboxed picture. Call [release] when the view goes away.
 */
internal class WebRtcTextureRenderer(
    eglContext: EglBase.Context,
    private val onVideoSize: (width: Int, height: Int) -> Unit,
) : VideoSink, TextureView.SurfaceTextureListener {

    private val renderer = EglRenderer("CamGridTextureRenderer")
    private val mainHandler = Handler(Looper.getMainLooper())

    // Written on the decoder thread that delivers frames.
    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0

    // Main thread only.
    private var released = false

    init {
        renderer.init(eglContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
    }

    /** Called on a WebRTC decoder thread; the renderer keeps its own reference to the frame. */
    override fun onFrame(frame: VideoFrame) {
        val width = frame.rotatedWidth
        val height = frame.rotatedHeight
        if (width != videoWidth || height != videoHeight) {
            videoWidth = width
            videoHeight = height
            mainHandler.post { if (!released) onVideoSize(width, height) }
        }
        renderer.onFrame(frame)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderer.setLayoutAspectRatio(aspectRatio(width, height))
        renderer.createEglSurface(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderer.setLayoutAspectRatio(aspectRatio(width, height))
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // Like SurfaceViewRenderer: the render thread must be done with the surface before the
        // TextureView releases it.
        val done = CountDownLatch(1)
        renderer.releaseEglSurface { done.countDown() }
        ThreadUtils.awaitUninterruptibly(done)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    /** Stops drawing and frees the render thread; detach it from the stream first. */
    fun release() {
        if (released) return
        released = true
        renderer.release()
    }

    // 0 lets the renderer keep the frame's own aspect ratio (no crop) while the size is unknown.
    private fun aspectRatio(width: Int, height: Int): Float =
        if (width > 0 && height > 0) width.toFloat() / height else 0f
}
