package io.github.vandosketch.camgrid.desktop.video

import io.github.vandosketch.camgrid.platform.AppLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOInterruptCB
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_LOW_DELAY
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_get_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.av_find_best_stream
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_context
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer

/**
 * One RTSP session through FFmpeg (the LGPL build from org.bytedeco): RTP over TCP (no lost
 * UDP packets, works through NAT and firewalls), no input buffering, low-delay decoding. Also
 * plays http(s) media URLs, such as go2rtc's MP4 (`/api/stream.mp4`), the fallback for WebRTC
 * streams in a codec webrtc-java cannot decode (H.265), with the same options.
 * Decoded video is scaled to the viewport and converted to BGRA into [frames]. With
 * [audioEnabled] the audio track is decoded and played ([AudioOut]); without it only video is
 * set up, so the server sends no audio at all.
 * [audioOutput] is replaceable for tests.
 */
class RtspConnection(
    private val url: String,
    private val frames: FrameHolder,
    private val audioEnabled: Boolean,
    private val audioOutput: () -> AudioOutput = ::JavaSoundOutput,
) : StreamConnection {

    @Volatile private var muted = false

    private val overHttp = url.trim().substringBefore("://").lowercase() in setOf("http", "https")

    override fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    override suspend fun play(frames: FrameListener) {
        Ffmpeg.init()
        withContext(Dispatchers.IO) {
            val stop = AtomicBoolean(false)
            // FFmpeg's blocking reads poll this callback, so a cancelled stream stops at once.
            val handle = coroutineContext[Job]?.invokeOnCompletion { stop.set(true) }
            try {
                Session(stop, frames).use { it.run() }
            } finally {
                handle?.dispose()
            }
        }
    }

    private inner class Session(private val stop: AtomicBoolean, private val listener: FrameListener) : AutoCloseable {
        private val interrupt = object : AVIOInterruptCB.Callback_Pointer() {
            override fun call(opaque: Pointer?): Int = if (stop.get()) 1 else 0
        }
        private var format: AVFormatContext? = avformat_alloc_context()
        private var video: AVCodecContext? = null
        private var audio: AudioDecoder? = null
        private val packet: AVPacket = av_packet_alloc()
        private val frame: AVFrame = av_frame_alloc()
        private var sws: SwsContext? = null
        private var bgra: BytePointer? = null
        // JavaCPP does not zero memory: unused planes must be null with stride 0 for sws_scale.
        private val dstData = PointerPointer<BytePointer>(4L).apply { for (i in 0L until 4L) put(i, null as Pointer?) }
        private val dstStride = IntPointer(4L).apply { for (i in 0L until 4L) put(i, 0) }

        fun run() {
            val fmt = format ?: throw StreamFailure("NO_MEMORY")
            fmt.interrupt_callback().callback(interrupt)
            val options = AVDictionary(null)
            av_dict_set(options, "rtsp_transport", "tcp", 0)
            av_dict_set(options, "allowed_media_types", if (audioEnabled) "video+audio" else "video", 0)
            av_dict_set(options, "fflags", "nobuffer", 0)
            av_dict_set(options, "flags", "low_delay", 0)
            // Socket timeout in microseconds: a dead server fails instead of hanging.
            av_dict_set(options, "timeout", "5000000", 0)
            av_dict_set(options, "max_delay", "500000", 0)
            av_dict_set(options, "reorder_queue_size", "0", 0)
            av_dict_set(options, "probesize", "500000", 0)
            av_dict_set(options, "analyzeduration", "1000000", 0)
            val opened = avformat_open_input(fmt, url, null, options)
            av_dict_free(options)
            if (opened < 0) {
                // avformat_open_input frees the context on failure.
                format = null
                throw StreamFailure(Ffmpeg.errorCode(opened, overHttp))
            }
            check(avformat_find_stream_info(fmt, null as PointerPointer<*>?), "NO_STREAM_INFO")

            val videoIndex = av_find_best_stream(fmt, AVMEDIA_TYPE_VIDEO, -1, -1, null as PointerPointer<*>?, 0)
            if (videoIndex < 0) throw StreamFailure("NO_VIDEO")
            video = openDecoder(fmt, videoIndex, lowDelay = true) ?: run {
                val params = fmt.streams(videoIndex).codecpar()
                // The codec and size only, for a report; never the URL.
                AppLog.w("No FFmpeg decoder for ${avcodec_get_name(params.codec_id()).string} ${params.width()}x${params.height()}")
                throw StreamFailure("NO_DECODER")
            }
            val audioIndex = if (audioEnabled) av_find_best_stream(fmt, AVMEDIA_TYPE_AUDIO, -1, videoIndex, null as PointerPointer<*>?, 0) else -1
            if (audioIndex >= 0) {
                audio = openDecoder(fmt, audioIndex, lowDelay = false)?.let { AudioDecoder(it, audioOutput()) { muted } }
            }

            while (!stop.get()) {
                val read = av_read_frame(fmt, packet)
                if (read < 0) {
                    if (stop.get()) return
                    throw StreamFailure(Ffmpeg.errorCode(read, overHttp))
                }
                try {
                    when (packet.stream_index()) {
                        videoIndex -> decodeVideo()
                        audioIndex -> audio?.decode(packet)
                    }
                } finally {
                    av_packet_unref(packet)
                }
            }
        }

        private fun openDecoder(fmt: AVFormatContext, index: Int, lowDelay: Boolean): AVCodecContext? {
            val params = fmt.streams(index).codecpar()
            val codec = avcodec_find_decoder(params.codec_id()) ?: return null
            val ctx = avcodec_alloc_context3(codec) ?: return null
            if (avcodec_parameters_to_context(ctx, params) < 0) {
                avcodec_free_context(ctx)
                return null
            }
            if (lowDelay) ctx.flags(ctx.flags() or AV_CODEC_FLAG_LOW_DELAY)
            if (avcodec_open2(ctx, codec, null as PointerPointer<*>?) < 0) {
                avcodec_free_context(ctx)
                return null
            }
            return ctx
        }

        private fun decodeVideo() {
            val ctx = video ?: return
            // A broken packet only spoils its frame; the next keyframe recovers.
            if (avcodec_send_packet(ctx, packet) < 0) return
            while (true) {
                val received = avcodec_receive_frame(ctx, frame)
                if (received == AVERROR_EAGAIN() || received < 0) return
                listener.onFrame()
                convert(frame)
            }
        }

        private fun convert(frame: AVFrame) {
            val srcW = frame.width()
            val srcH = frame.height()
            if (srcW <= 0 || srcH <= 0) return
            val (w, h) = frames.targetSize(srcW, srcH)
            sws = sws_getCachedContext(sws, srcW, srcH, frame.format(), w, h, AV_PIX_FMT_BGRA, SWS_BILINEAR, null, null, null as DoubleArray?)
            val size = w.toLong() * h * 4
            val out = bgra?.takeIf { it.capacity() == size } ?: BytePointer(size).also {
                bgra?.close()
                bgra = it
            }
            dstData.put(0L, out)
            // 0L: with an Int index Kotlin picks put(int...) and writes {0, stride}.
            dstStride.put(0L, w * 4)
            sws_scale(sws, frame.data(), frame.linesize(), 0, srcH, dstData, dstStride)
            frames.write(w, h) { pixels -> out.get(pixels, 0, pixels.size) }
        }

        private fun check(result: Int, code: String) {
            if (result < 0) throw StreamFailure(if (stop.get()) "CANCELLED" else code)
        }

        override fun close() {
            audio?.close()
            video?.let { avcodec_free_context(it) }
            format?.let { avformat_close_input(it) }
            av_packet_free(packet)
            av_frame_free(frame)
            sws?.let { sws_freeContext(it) }
            bgra?.close()
            dstData.close()
            dstStride.close()
            interrupt.close()
        }
    }
}
