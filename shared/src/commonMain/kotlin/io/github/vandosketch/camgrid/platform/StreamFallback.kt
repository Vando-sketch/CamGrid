package io.github.vandosketch.camgrid.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.core.StreamSourcePlan
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.stream_note_lower_resolution
import io.github.vandosketch.camgrid.shared.resources.stream_note_mp4
import io.github.vandosketch.camgrid.shared.resources.stream_note_mp4_codec
import org.jetbrains.compose.resources.stringResource

/**
 * A platform's stream ([stream]) that plays a fallback [source] of the camera instead of its own
 * URL; [FallbackSurface] shows a note saying so over the video.
 */
class FallbackLiveStream internal constructor(
    val stream: LiveStream,
    val source: StreamSourcePlan.Source,
) : LiveStream by stream

/** The platform's own stream: [stream] itself, or the one a [FallbackLiveStream] wraps. */
fun LiveStream?.platformStream(): LiveStream? = (this as? FallbackLiveStream)?.stream ?: this

/**
 * The [VideoPlatform.rememberLiveStream] of every platform: plays [url] through [play] (the
 * platform's stream for one URL and type, keyed by both) and switches to the next source of a
 * [StreamSourcePlan] when the stream fails in a way that retrying cannot fix: go2rtc's MP4 for a
 * WebRTC codec rejection (issue #17), [lowerResolutionUrl] for a decoder that cannot play the
 * stream (issue #16). Other failures are left to the platform's own reconnecting. A fallback
 * stream is returned as a [FallbackLiveStream]. The plan starts over when an argument changes.
 */
@Composable
fun rememberStreamWithFallback(
    url: String,
    type: StreamType,
    label: String,
    audioEnabled: Boolean,
    lowerResolutionUrl: String?,
    play: @Composable (url: String, type: StreamType) -> LiveStream?,
): LiveStream? {
    val plan = remember(url, type, audioEnabled, lowerResolutionUrl) {
        StreamSourcePlan(url, type, audioEnabled, lowerResolutionUrl)
    }
    var source by remember(plan) { mutableStateOf(plan.current) }
    val stream = play(source.url, source.type)
    LaunchedEffect(stream, plan) {
        val current = stream ?: return@LaunchedEffect
        // Emits each failure once: the reason stays the same while the retry counts down.
        snapshotFlow {
            when (val status = current.status) {
                is StreamStatus.Offline -> status.reason
                StreamStatus.Playing -> PLAYING
                StreamStatus.Connecting -> null
            }
        }.collect { reason ->
            when (reason) {
                null -> Unit
                PLAYING -> plan.onPlaying()
                else -> {
                    val next = plan.onFailure(reason) ?: return@collect
                    AppLog.w("Stream '$label' failed: $reason; playing $next instead")
                    source = next
                }
            }
        }
    }
    return remember(stream, source) {
        if (stream != null && source.isFallback) FallbackLiveStream(stream, source) else stream
    }
}

/**
 * A platform's [VideoPlatform.Surface] around [video], which draws the platform's own stream
 * (see [platformStream]) filling the bounds: adds the note of a [FallbackLiveStream] in the
 * top end corner ("H.265 via MP4", "Lower resolution").
 */
@Composable
fun FallbackSurface(
    stream: LiveStream?,
    modifier: Modifier,
    video: @Composable (stream: LiveStream?, modifier: Modifier) -> Unit,
) {
    Box(modifier) {
        video(stream.platformStream(), Modifier.fillMaxSize())
        val source = (stream as? FallbackLiveStream)?.source ?: return@Box
        Text(
            text = noteText(source),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun noteText(source: StreamSourcePlan.Source): String {
    val parts = mutableListOf<String>()
    if (source.viaMp4) {
        parts += source.codec?.let { stringResource(Res.string.stream_note_mp4_codec, codecName(it)) }
            ?: stringResource(Res.string.stream_note_mp4)
    }
    if (source.lowerResolution) parts += stringResource(Res.string.stream_note_lower_resolution)
    return parts.joinToString(" · ")
}

// Stands for StreamStatus.Playing among the failure reasons, which are upper snake case codes.
private const val PLAYING = "playing"

/** go2rtc's codec name as people write it: H265 becomes H.265, others stay. */
internal fun codecName(codec: String): String =
    if (codec.length == 4 && codec.startsWith("H26")) "H.26" + codec.last() else codec
