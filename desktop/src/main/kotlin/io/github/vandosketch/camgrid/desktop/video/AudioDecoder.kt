package io.github.vandosketch.camgrid.desktop.video

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVChannelLayout
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.swresample.swr_alloc_set_opts2
import org.bytedeco.ffmpeg.global.swresample.swr_convert
import org.bytedeco.ffmpeg.global.swresample.swr_free
import org.bytedeco.ffmpeg.global.swresample.swr_init
import org.bytedeco.ffmpeg.swresample.SwrContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer

/** Where decoded audio goes: 48 kHz stereo signed 16-bit little-endian PCM. */
interface AudioOutput : AutoCloseable {
    /** False when there is no output device; the decoder then skips audio entirely. */
    fun open(): Boolean

    /** Plays [length] bytes of [pcm] without blocking; what does not fit is dropped. */
    fun write(pcm: ByteArray, length: Int)

    /** Drops buffered audio (on mute). */
    fun flush()

    companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAME_BYTES = 4 // 2 channels x 16 bit
    }
}

/** The default output device through Java Sound, with about 250 ms of buffer. */
class JavaSoundOutput : AudioOutput {
    private var line: SourceDataLine? = null

    override fun open(): Boolean {
        if (line != null) return true
        return try {
            val format = AudioFormat(AudioOutput.SAMPLE_RATE.toFloat(), 16, 2, true, false)
            line = AudioSystem.getSourceDataLine(format).apply {
                open(format, AudioOutput.SAMPLE_RATE / 4 * AudioOutput.FRAME_BYTES)
                start()
            }
            true
        } catch (e: Exception) {
            System.err.println("CamGrid: no audio output (${e.javaClass.simpleName}); video only")
            false
        }
    }

    override fun write(pcm: ByteArray, length: Int) {
        val line = line ?: return
        // Never block the read loop on a slow or stuck audio device: live audio drops instead.
        val writable = minOf(length, line.available()) / AudioOutput.FRAME_BYTES * AudioOutput.FRAME_BYTES
        if (writable > 0) line.write(pcm, 0, writable)
    }

    override fun flush() {
        line?.flush()
    }

    override fun close() {
        line?.let {
            it.stop()
            it.close()
        }
        line = null
    }
}

/**
 * Decodes an RTSP stream's audio, resamples it to [AudioOutput]'s format and plays it, for
 * fullscreen. While [muted] nothing is decoded; without an output device nothing ever is.
 * Takes ownership of [ctx] and [output].
 */
internal class AudioDecoder(
    private val ctx: AVCodecContext,
    private val output: AudioOutput,
    private val muted: () -> Boolean,
) : AutoCloseable {
    private val frame: AVFrame = av_frame_alloc()
    private val available = output.open()
    private var swr: SwrContext? = null
    private var swrFailed = false
    private var out: BytePointer? = null
    private val outData = PointerPointer<BytePointer>(1L).apply { put(0L, null as Pointer?) }
    private var bytes = ByteArray(0)
    private var wasMuted = false

    fun decode(packet: AVPacket) {
        if (!available) return
        if (muted()) {
            if (!wasMuted) output.flush()
            wasMuted = true
            return
        }
        wasMuted = false
        if (avcodec_send_packet(ctx, packet) < 0) return
        while (avcodec_receive_frame(ctx, frame) >= 0) play(frame)
    }

    private fun play(frame: AVFrame) {
        val resampler = swr ?: createResampler(frame)?.also { swr = it } ?: return
        val rate = AudioOutput.SAMPLE_RATE
        val maxSamples = (frame.nb_samples().toLong() * rate / frame.sample_rate().coerceAtLeast(1)).toInt() + 256
        val size = maxSamples.toLong() * AudioOutput.FRAME_BYTES
        val buffer = out?.takeIf { it.capacity() >= size } ?: BytePointer(size).also {
            out?.close()
            out = it
        }
        // 0L: with an Int index Kotlin would pick the varargs put(Pointer...).
        outData.put(0L, buffer)
        val samples = swr_convert(resampler, outData, maxSamples, frame.extended_data(), frame.nb_samples())
        if (samples <= 0) return
        val count = samples * AudioOutput.FRAME_BYTES
        if (bytes.size < count) bytes = ByteArray(count)
        buffer.get(bytes, 0, count)
        output.write(bytes, count)
    }

    private fun createResampler(frame: AVFrame): SwrContext? {
        if (swrFailed) return null
        // swr_alloc_set_opts2 reuses *holder when it is not null; JavaCPP does not zero memory.
        val holder = PointerPointer<SwrContext>(1L).apply { put(0L, null as Pointer?) }
        val outLayout = AVChannelLayout()
        av_channel_layout_default(outLayout, 2)
        val result = swr_alloc_set_opts2(
            holder, outLayout, AV_SAMPLE_FMT_S16, AudioOutput.SAMPLE_RATE,
            frame.ch_layout(), frame.format(), frame.sample_rate(), 0, null as Pointer?,
        )
        outLayout.close()
        val context = holder.get(SwrContext::class.java, 0L)
        holder.close()
        if (result < 0 || context == null || context.isNull || swr_init(context) < 0) {
            context?.takeIf { !it.isNull }?.let { swr_free(it) }
            swrFailed = true
            return null
        }
        return context
    }

    override fun close() {
        output.close()
        swr?.let { swr_free(it) }
        av_frame_free(frame)
        avcodec_free_context(ctx)
        out?.close()
        outData.close()
    }
}
