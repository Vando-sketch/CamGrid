package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.cancel
import io.github.vandosketch.camgrid.shared.resources.editor_title_edit
import io.github.vandosketch.camgrid.shared.resources.editor_title_new
import io.github.vandosketch.camgrid.shared.resources.error_grid_url_blank
import io.github.vandosketch.camgrid.shared.resources.error_name_blank
import io.github.vandosketch.camgrid.shared.resources.error_url_invalid
import io.github.vandosketch.camgrid.shared.resources.error_webrtc_url_invalid
import io.github.vandosketch.camgrid.shared.resources.field_detail_url
import io.github.vandosketch.camgrid.shared.resources.field_grid_url
import io.github.vandosketch.camgrid.shared.resources.field_name
import io.github.vandosketch.camgrid.shared.resources.help_detail_url
import io.github.vandosketch.camgrid.shared.resources.hint_detail_url
import io.github.vandosketch.camgrid.shared.resources.hint_detail_url_webrtc
import io.github.vandosketch.camgrid.shared.resources.hint_grid_url
import io.github.vandosketch.camgrid.shared.resources.hint_grid_url_webrtc
import io.github.vandosketch.camgrid.shared.resources.hint_name
import io.github.vandosketch.camgrid.shared.resources.save
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.CameraError
import io.github.vandosketch.camgrid.core.CameraValidator
import io.github.vandosketch.camgrid.core.Go2rtc
import io.github.vandosketch.camgrid.core.StreamType
import kotlin.uuid.Uuid

/**
 * Add or edit one camera. [camera] null means a new camera (it gets a random UUID id).
 * Validation uses core's [CameraValidator]; errors appear under the fields after the first
 * Save attempt and update live from then on. Switching the stream type rewrites go2rtc URLs
 * to the same stream in the other type (see [Go2rtc.convertUrl]); other URLs stay as typed.
 */
@Composable
fun CameraEditorScreen(
    camera: Camera?,
    onSave: (Camera) -> Unit,
    onCancel: () -> Unit,
) {
    val id = rememberSaveable { camera?.id ?: Uuid.random().toString() }
    var name by rememberSaveable { mutableStateOf(camera?.name.orEmpty()) }
    var gridUrl by rememberSaveable { mutableStateOf(camera?.gridUrl.orEmpty()) }
    var detailUrl by rememberSaveable { mutableStateOf(camera?.detailUrl.orEmpty()) }
    var streamType by rememberSaveable { mutableStateOf(camera?.streamType ?: StreamType.RTSP) }
    var saveAttempted by rememberSaveable { mutableStateOf(false) }

    // A go2rtc player URL pasted from the browser becomes its WebRTC signalling URL.
    fun playable(url: String) =
        if (streamType == StreamType.WEBRTC) Go2rtc.webrtcEndpoint(url) ?: url.trim() else url.trim()
    val draft = Camera(
        id = id,
        name = name.trim(),
        gridUrl = playable(gridUrl),
        detailUrl = playable(detailUrl),
        streamType = streamType,
    )
    val webrtc = streamType == StreamType.WEBRTC
    val urlInvalidMessage = stringResource(if (webrtc) Res.string.error_webrtc_url_invalid else Res.string.error_url_invalid)
    val errors = if (saveAttempted) CameraValidator.validate(draft) else emptySet()

    val nameRequester = remember { FocusRequester() }
    InitialFocus(nameRequester)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Save sits in the header so it stays reachable without scrolling past the fields
        // (the keyboard covers the bottom on phones). Cancel is the only button at the bottom.
        ScreenHeader(
            title = stringResource(if (camera == null) Res.string.editor_title_new else Res.string.editor_title_edit),
            actionLabel = stringResource(Res.string.save),
            onAction = {
                saveAttempted = true
                if (CameraValidator.validate(draft).isEmpty()) onSave(draft)
            },
            primary = true,
        )

        EditorField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(Res.string.field_name),
            hint = stringResource(Res.string.hint_name),
            error = when {
                CameraError.NAME_BLANK in errors -> stringResource(Res.string.error_name_blank)
                else -> null
            },
            keyboardType = KeyboardType.Text,
            modifier = Modifier.focusRequester(nameRequester),
        )
        StreamTypeSelector(
            selected = streamType,
            onSelect = { type ->
                if (type != streamType) {
                    streamType = type
                    gridUrl = Go2rtc.convertUrl(gridUrl, type) ?: gridUrl
                    detailUrl = Go2rtc.convertUrl(detailUrl, type) ?: detailUrl
                }
            },
        )
        EditorField(
            value = gridUrl,
            onValueChange = { gridUrl = it },
            label = stringResource(Res.string.field_grid_url),
            hint = stringResource(if (webrtc) Res.string.hint_grid_url_webrtc else Res.string.hint_grid_url),
            error = when {
                CameraError.GRID_URL_BLANK in errors -> stringResource(Res.string.error_grid_url_blank)
                CameraError.GRID_URL_INVALID in errors -> urlInvalidMessage
                else -> null
            },
            keyboardType = KeyboardType.Uri,
        )
        EditorField(
            value = detailUrl,
            onValueChange = { detailUrl = it },
            label = stringResource(Res.string.field_detail_url),
            hint = stringResource(if (webrtc) Res.string.hint_detail_url_webrtc else Res.string.hint_detail_url),
            error = when {
                CameraError.DETAIL_URL_INVALID in errors -> urlInvalidMessage
                else -> null
            },
            help = stringResource(Res.string.help_detail_url),
            keyboardType = KeyboardType.Uri,
        )

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .padding(top = 8.dp)
                .focusBorder(shape = CircleShape),
        ) {
            Text(stringResource(Res.string.cancel))
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    error: String?,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    help: String? = null,
) {
    CamTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        isError = error != null,
        supportingText = {
            // Shown in the error colour automatically when isError is true.
            val text = error ?: help
            if (text != null) Text(text)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}
