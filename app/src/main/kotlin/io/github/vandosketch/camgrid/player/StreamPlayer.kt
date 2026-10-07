package io.github.vandosketch.camgrid.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import io.github.vandosketch.camgrid.core.ReconnectPolicy
import io.github.vandosketch.camgrid.core.StreamFailures
import io.github.vandosketch.camgrid.core.StreamWatchdog
import io.github.vandosketch.camgrid.core.UrlRedactor
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.StreamStatus
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.util.Log as Media3Log

/**
 * One ExoPlayer playing one live stream, reconnecting with [ReconnectPolicy] backoff after
 * errors. Must be created and used on the main thread; call [release] when done.
 *
 * Plays RTSP(S) and http(s) media URLs (HLS, or go2rtc's MP4, the fallback for WebRTC streams
 * in a codec libwebrtc does not offer). When the first decoder fails, ExoPlayer tries the
 * device's next one (a Fire TV's hardware decoder may reject a portrait or high resolution
 * stream that a software decoder plays). [StreamWatchdog] counts the frames the video decoder
 * puts out: a decoder that takes the stream but never shows a frame fails with
 * [StreamFailures.DECODER_NO_OUTPUT], like a decoder error, so fullscreen can switch to the
 * camera's grid stream. Decoder failures are logged with the stream's format and the decoders
 * that claim to play it, but never the URL.
 *
 * @param label used in log messages instead of the URL (the URL may contain credentials).
 * @param audioEnabled false for grid tiles: volume 0 and the audio track is not even selected,
 *   which saves a decoder on weak devices. With true, [setMuted] toggles the volume.
 */
@OptIn(UnstableApi::class)
class StreamPlayer(
    context: Context,
    url: String,
    private val label: String,
    audioEnabled: Boolean,
) : LiveStream {
    val player: ExoPlayer = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setLoadControl(lowLatencyLoadControl())
        .build()

    /** Current state; Compose snapshot state, so composables reading it recompose on change. */
    override var status by mutableStateOf<StreamStatus>(StreamStatus.Connecting)
        private set

    /**
     * Whether the stream has an audio track, from the tracks ExoPlayer found (the RTSP
     * session's SDP, the MP4's header); null until the first stream was opened. Kept across
     * reconnects, which clear the tracks for a moment.
     */
    override var hasAudio by mutableStateOf<Boolean?>(null)
        private set

    private val reconnectPolicy = ReconnectPolicy()
    private val watchdog = StreamWatchdog()
    private var attempt = 0
    private val scope = MainScope()
    private var retryJob: Job? = null
    private var watchdogJob: Job? = null
    private var released = false
    private var firstFrameRendered = false
    private var videoDecoder: String? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> if (firstFrameRendered) onPlaying()
                Player.STATE_BUFFERING -> {
                    if (status !is StreamStatus.Offline) status = StreamStatus.Connecting
                }
                // A live stream should never end; treat it like a dropped connection.
                Player.STATE_ENDED -> scheduleReconnect("ENDED")
                else -> Unit
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (!tracks.isEmpty) hasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO)
        }

        override fun onRenderedFirstFrame() {
            firstFrameRendered = true
            if (player.playbackState == Player.STATE_READY) onPlaying()
        }

        override fun onPlayerError(error: PlaybackException) {
            val reason = error.errorCodeName.removePrefix("ERROR_CODE_")
            if (StreamFailures.isDecoderFailure(reason)) logDecoderFailure(reason, (error as? ExoPlaybackException)?.rendererFormat)
            scheduleReconnect(reason)
        }
    }

    private val analytics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoder = decoderName
        }
    }

    init {
        if (!audioEnabled) {
            player.volume = 0f
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        player.setMediaSource(buildMediaSource(context, url))
        player.playWhenReady = true
        player.prepare()
        startWatchdog()
    }

    override fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    override fun release() {
        if (released) return
        released = true
        scope.cancel()
        player.removeListener(listener)
        player.removeAnalyticsListener(analytics)
        player.release()
    }

    private fun onPlaying() {
        attempt = 0
        status = StreamStatus.Playing
    }

    /**
     * Checks once a second that the video decoder puts out frames (rendered, or dropped and
     * skipped while there is no surface): none within the watchdog's connect timeout, or none
     * for its stall timeout, reconnects.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        firstFrameRendered = false
        val startedAt = SystemClock.elapsedRealtime()
        var lastCount = 0L
        var lastFrameAt: Long? = null
        watchdogJob = scope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                val counters = player.videoDecoderCounters?.apply { ensureUpdated() }
                val now = SystemClock.elapsedRealtime()
                val count = counters?.let {
                    it.renderedOutputBufferCount.toLong() + it.skippedOutputBufferCount + it.droppedBufferCount
                } ?: 0L
                if (count > lastCount) {
                    lastCount = count
                    lastFrameAt = now
                }
                when (watchdog.check(now, startedAt, lastFrameAt)) {
                    StreamWatchdog.Verdict.OK -> Unit
                    StreamWatchdog.Verdict.CONNECT_TIMEOUT -> {
                        // Fed but silent: the decoder cannot play this stream. Otherwise nothing came.
                        val fed = (counters?.queuedInputBufferCount ?: 0) >= MIN_INPUT_FOR_NO_OUTPUT
                        if (fed) logDecoderFailure(StreamFailures.DECODER_NO_OUTPUT, player.videoFormat)
                        scheduleReconnect(if (fed) StreamFailures.DECODER_NO_OUTPUT else "TIMEOUT")
                    }
                    StreamWatchdog.Verdict.STALLED -> scheduleReconnect("STALLED")
                }
            }
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (released) return
        // Never log the error's message or the URL: either may contain credentials.
        AppLog.w("Stream '$label' failed: $reason (attempt $attempt)")
        watchdogJob?.cancel()
        retryJob?.cancel()
        val delayMillis = reconnectPolicy.delayMillis(attempt)
        attempt++
        retryJob = scope.launch {
            // Count down in whole seconds so the tile can show "retrying in N s".
            var remaining = delayMillis
            while (remaining > 0) {
                status = StreamStatus.Offline(
                    reason = UrlRedactor.redact(reason),
                    retryInSeconds = ((remaining + 999) / 1000).toInt(),
                )
                val step = minOf(remaining, 1_000L)
                delay(step)
                remaining -= step
            }
            status = StreamStatus.Connecting
            // stop() moves an ENDED player back to IDLE; after an error it already is.
            player.stop()
            player.seekToDefaultPosition()
            player.prepare()
            startWatchdog()
        }
    }

    /**
     * Logs what a future bug report needs about a stream the device could not decode: codec,
     * resolution, rotation and frame rate, the decoder that was used, and for every decoder
     * of the device for that codec whether it claims to support the size and rate.
     */
    private fun logDecoderFailure(reason: String, rendererFormat: Format?) {
        val format = rendererFormat ?: player.videoFormat
        if (format == null) {
            AppLog.w("Stream '$label' $reason: no video format known, decoder ${videoDecoder ?: "none"}")
            return
        }
        val mime = format.sampleMimeType
        val frameRate = format.frameRate.toDouble().takeIf { it > 0 } ?: 25.0
        val decoders = try {
            mime?.let { MediaCodecUtil.getDecoderInfos(it, false, false) }.orEmpty().joinToString { info ->
                val supported = format.width > 0 && format.height > 0 &&
                    info.isVideoSizeAndRateSupported(format.width, format.height, frameRate)
                val kind = if (info.hardwareAccelerated) " (hw)" else ""
                "${info.name}$kind: ${if (supported) "size ok" else "size not supported"}"
            }
        } catch (_: MediaCodecUtil.DecoderQueryException) {
            "query failed"
        }
        AppLog.w(
            "Stream '$label' $reason: $mime ${format.codecs ?: ""} ${format.width}x${format.height} " +
                "rotation ${format.rotationDegrees} ${format.frameRate} fps, decoder ${videoDecoder ?: "none"}; " +
                "decoders: ${decoders.ifEmpty { "none" }}",
        )
    }

    companion object {
        private const val RTSP_TIMEOUT_MS = 5_000L
        private const val WATCHDOG_INTERVAL_MS = 1_000L

        /** About a second of video handed to a decoder that showed nothing makes it a decoder failure. */
        private const val MIN_INPUT_FOR_NO_OUTPUT = 25

        /**
         * Media3 logs exceptions whose messages can contain stream URLs with credentials.
         * CamGrid logs playback errors itself (redacted), so library logging is switched off.
         */
        fun disableLibraryLogging() {
            Media3Log.setLogLevel(Media3Log.LOG_LEVEL_OFF)
        }

        private fun lowLatencyLoadControl(): DefaultLoadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 500,
                    /* maxBufferMs = */ 2_000,
                    /* bufferForPlaybackMs = */ 250,
                    /* bufferForPlaybackAfterRebufferMs = */ 500,
                )
                .build()

        private fun buildMediaSource(context: Context, url: String): MediaSource {
            val uri = Uri.parse(url.trim())
            return when (uri.scheme?.lowercase()) {
                "rtsp" -> rtspFactory().createMediaSource(MediaItem.fromUri(uri))
                // RTSP over TLS: same source, but with TLS sockets.
                "rtsps" -> rtspFactory()
                    .setSocketFactory(SSLSocketFactory.getDefault())
                    .createMediaSource(MediaItem.fromUri(uri))
                else -> httpMediaSource(context, uri)
            }
        }

        /**
         * An http(s) media URL. ExoPlayer's HTTP stack ignores user-info, so it is taken out of
         * the URL and sent as Basic authorization, as go2rtc's API (with credentials set)
         * expects; the other schemes keep the URL as it is.
         */
        private fun httpMediaSource(context: Context, uri: Uri): MediaSource {
            val userInfo = uri.encodedUserInfo
            val http = DefaultHttpDataSource.Factory()
            val target = if (userInfo != null && uri.scheme?.lowercase() in setOf("http", "https")) {
                val credentials = Uri.decode(userInfo).toByteArray(Charsets.UTF_8)
                http.setDefaultRequestProperties(mapOf("Authorization" to "Basic " + Base64.encodeToString(credentials, Base64.NO_WRAP)))
                uri.buildUpon().encodedAuthority(uri.encodedAuthority?.substringAfterLast('@')).build()
            } else {
                uri
            }
            return DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)).createMediaSource(MediaItem.fromUri(target))
        }

        // TCP interleaving avoids lost UDP packets (smearing) on Wi-Fi and through NAT.
        private fun rtspFactory(): RtspMediaSource.Factory =
            RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(RTSP_TIMEOUT_MS)
    }
}
