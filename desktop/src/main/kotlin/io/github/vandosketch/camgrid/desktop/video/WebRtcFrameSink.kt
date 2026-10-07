package io.github.vandosketch.camgrid.desktop.video

import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoFrame
import dev.onvoid.webrtc.media.video.VideoFrameBuffer
import dev.onvoid.webrtc.media.video.VideoTrackSink

/**
 * Converts each decoded frame (on a libwebrtc decoder thread) into [frames], scaled to the
 * viewport, as BGRA.
 *
 * webrtc-java hands every sink its own native copy of each frame, and only the sink can free
 * it: every frame must be released, whatever happens to it. Without that, each frame leaked
 * (issue #24: about 160 MB/s with six streams, until the system killed the app).
 *
 * @param convert writes a frame buffer as BGRA into the array; replaced in tests.
 */
internal class WebRtcFrameSink(
    private val frames: FrameHolder,
    private val listener: FrameListener,
    private val isClosed: () -> Boolean,
    private val convert: (VideoFrameBuffer, ByteArray) -> Unit = ::convertToBgra,
) : VideoTrackSink {

    override fun onVideoFrame(frame: VideoFrame) {
        try {
            show(frame.buffer)
        } finally {
            frame.release()
        }
    }

    private fun show(buffer: VideoFrameBuffer) {
        if (isClosed()) return
        listener.onFrame()
        val (width, height) = frames.targetSize(buffer.width, buffer.height)
        val scaled = if (width == buffer.width && height == buffer.height) {
            null
        } else {
            buffer.cropAndScale(0, 0, buffer.width, buffer.height, width, height)
        }
        try {
            frames.write(width, height) { pixels -> convert(scaled ?: buffer, pixels) }
        } finally {
            scaled?.release()
        }
    }
}

/** libyuv's "ARGB" is B, G, R, A in memory: Skia's BGRA_8888. */
private fun convertToBgra(buffer: VideoFrameBuffer, pixels: ByteArray) =
    VideoBufferConverter.convertFromI420(buffer, pixels, FourCC.ARGB)
