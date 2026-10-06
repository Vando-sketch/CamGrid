package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.R
import io.github.vandosketch.camgrid.core.StreamType

/** "Stream type" with one chip per [StreamType] and a line on what the selected one means. */
@Composable
fun StreamTypeSelector(
    selected: StreamType,
    onSelect: (StreamType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.stream_type), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in StreamType.entries) {
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = { Text(stringResource(type.labelRes)) },
                    modifier = Modifier.focusBorder(shape = RoundedCornerShape(8.dp)),
                )
            }
        }
        Text(
            text = stringResource(selected.helpRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val StreamType.labelRes: Int
    get() = when (this) {
        StreamType.RTSP -> R.string.stream_type_rtsp
        StreamType.WEBRTC -> R.string.stream_type_webrtc
    }

private val StreamType.helpRes: Int
    get() = when (this) {
        StreamType.RTSP -> R.string.help_stream_type_rtsp
        StreamType.WEBRTC -> R.string.help_stream_type_webrtc
    }
