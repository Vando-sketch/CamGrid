package io.github.vandosketch.camgrid.desktop.video

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import io.github.vandosketch.camgrid.core.FitMode
import java.util.concurrent.atomic.AtomicLong

/**
 * The latest decoded frame of one stream, handed from the decoder thread to the UI thread.
 * Two pixel buffers alternate: the decoder fills the spare one without a lock and publishes
 * it; the UI copies the published one into a Skia image under the lock. No frame queue, so a
 * slow UI only ever skips frames, never falls behind.
 */
class FrameHolder {
    /** BGRA pixels, 4 bytes each, rows packed without padding; alpha is opaque. */
    class Frame(val width: Int, val height: Int, val pixels: ByteArray)

    /** Pixel size and fit of the surface showing this stream; decoders scale down to it. */
    data class Viewport(val width: Int, val height: Int, val fit: FitMode)

    @Volatile var viewport: Viewport = Viewport(0, 0, FitMode.FIT)

    private val lock = Any()
    private var latest: Frame? = null
    private var spare: ByteArray? = null
    private val versions = AtomicLong()
    private val frames = AtomicLong()
    private val versionState = mutableLongStateOf(0L)

    /** Changes with every new frame; Compose state, so a draw that reads it is redone. */
    val version: State<Long> get() = versionState

    /** Frames written since creation (for tests and diagnostics). */
    val framesWritten: Long get() = frames.get()

    /** The size to convert a [frameWidth] x [frameHeight] frame to for the current viewport. */
    fun targetSize(frameWidth: Int, frameHeight: Int): Pair<Int, Int> {
        val v = viewport
        return FrameGeometry.decodeSize(frameWidth, frameHeight, v.width, v.height, v.fit)
    }

    /** Publishes a [width] x [height] frame that [fill] writes as BGRA into the given buffer. */
    fun write(width: Int, height: Int, fill: (ByteArray) -> Unit) {
        val size = width * height * 4
        val buffer = synchronized(lock) {
            spare?.takeIf { it.size == size }.also { spare = null }
        } ?: ByteArray(size)
        fill(buffer)
        synchronized(lock) {
            spare = latest?.pixels
            latest = Frame(width, height, buffer)
        }
        frames.incrementAndGet()
        versionState.longValue = versions.incrementAndGet()
    }

    /** Runs [block] on the latest frame while no decoder can overwrite it, or returns null. */
    fun <T> withLatest(block: (Frame) -> T): T? = synchronized(lock) { latest?.let(block) }

    /** Drops the frame, so a reconnecting stream shows black instead of a stale picture. */
    fun clear() {
        synchronized(lock) {
            latest = null
            spare = null
        }
        versionState.longValue = versions.incrementAndGet()
    }
}
