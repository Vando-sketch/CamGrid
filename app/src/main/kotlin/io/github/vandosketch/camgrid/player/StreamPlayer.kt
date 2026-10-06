package io.github.vandosketch.camgrid.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import io.github.vandosketch.camgrid.core.ReconnectPolicy
import io.github.vandosketch.camgrid.core.UrlRedactor
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
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(lowLatencyLoadControl())
        .build()

    /** Current state; Compose snapshot state, so composables reading it recompose on change. */
    override var status by mutableStateOf<StreamStatus>(StreamStatus.Connecting)
        private set

    private val reconnectPolicy = ReconnectPolicy()
    private var attempt = 0
    private val scope = MainScope()
    private var retryJob: Job? = null
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    attempt = 0
                    status = StreamStatus.Playing
                }
                Player.STATE_BUFFERING -> {
                    if (status !is StreamStatus.Offline) status = StreamStatus.Connecting
                }
                // A live stream should never end; treat it like a dropped connection.
                Player.STATE_ENDED -> scheduleReconnect("ENDED")
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val reason = error.errorCodeName.removePrefix("ERROR_CODE_")
            // Never log error.message or the URL: either may contain credentials.
            Log.w(TAG, "Stream '$label' failed: $reason (attempt $attempt)")
            scheduleReconnect(reason)
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
        player.setMediaSource(buildMediaSource(context, url))
        player.playWhenReady = true
        player.prepare()
    }

    override fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    override fun release() {
        if (released) return
        released = true
        scope.cancel()
        player.removeListener(listener)
        player.release()
    }

    private fun scheduleReconnect(reason: String) {
        if (released) return
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
        }
    }

    companion object {
        private const val TAG = "CamGrid"
        private const val RTSP_TIMEOUT_MS = 5_000L

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
            val mediaItem = MediaItem.fromUri(url)
            return when (Uri.parse(url).scheme?.lowercase()) {
                "rtsp" -> rtspFactory().createMediaSource(mediaItem)
                // RTSP over TLS: same source, but with TLS sockets.
                "rtsps" -> rtspFactory()
                    .setSocketFactory(SSLSocketFactory.getDefault())
                    .createMediaSource(mediaItem)
                else -> DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
            }
        }

        // TCP interleaving avoids lost UDP packets (smearing) on Wi-Fi and through NAT.
        private fun rtspFactory(): RtspMediaSource.Factory =
            RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(RTSP_TIMEOUT_MS)
    }
}
