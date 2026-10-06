package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.ImportState
import io.github.vandosketch.camgrid.R
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.UrlRedactor
import io.github.vandosketch.camgrid.data.Go2rtcException

/**
 * Fetches `/api/streams` from a go2rtc server and lets the user tick which suggested cameras
 * to import. Cameras that are already in the config (same id) are shown but cannot be ticked.
 */
@Composable
fun Go2rtcImportScreen(
    initialBaseUrl: String,
    existingIds: Set<String>,
    state: ImportState,
    onFetch: (String) -> Unit,
    onToggle: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    var baseUrl by rememberSaveable { mutableStateOf(initialBaseUrl) }
    val fieldRequester = remember { FocusRequester() }
    InitialFocus(fieldRequester)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.import_go2rtc),
                actionLabel = stringResource(R.string.back),
                onAction = onBack,
            )
        }
        item {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.field_go2rtc_url)) },
                placeholder = { Text(stringResource(R.string.hint_go2rtc_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onFetch(baseUrl) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldRequester),
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onFetch(baseUrl) },
                    enabled = baseUrl.isNotBlank(),
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(R.string.fetch))
                }
                if (state is ImportState.Loaded && state.cameras.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onImport,
                        enabled = state.selected.isNotEmpty(),
                        modifier = Modifier.focusBorder(shape = CircleShape),
                    ) {
                        Text(stringResource(R.string.import_selected, state.selected.size))
                    }
                }
            }
        }

        when (state) {
            ImportState.Idle -> Unit
            ImportState.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.import_loading))
                }
            }
            is ImportState.Failed -> item {
                Text(
                    text = failureMessage(state),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            is ImportState.Loaded -> {
                if (state.cameras.isEmpty()) {
                    item { Text(stringResource(R.string.import_empty)) }
                }
                items(state.cameras, key = { it.id }) { camera ->
                    SuggestionRow(
                        camera = camera,
                        alreadyAdded = camera.id in existingIds,
                        checked = camera.id in state.selected,
                        onToggle = { onToggle(camera.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun failureMessage(state: ImportState.Failed): String {
    // The detail is an HTTP status or exception type, never a URL; redact anyway to be safe.
    val detail = UrlRedactor.redact(state.detail)
    return when (state.reason) {
        Go2rtcException.Reason.INVALID_URL -> stringResource(R.string.import_error_invalid_url)
        Go2rtcException.Reason.NETWORK -> stringResource(R.string.import_error_network, detail)
        Go2rtcException.Reason.HTTP_STATUS -> stringResource(R.string.import_error_http, detail)
        Go2rtcException.Reason.NOT_GO2RTC -> stringResource(R.string.import_error_not_go2rtc)
    }
}

@Composable
private fun SuggestionRow(
    camera: Camera,
    alreadyAdded: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusBorder()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .toggleable(
                value = checked || alreadyAdded,
                enabled = !alreadyAdded,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked || alreadyAdded,
            onCheckedChange = null,
            enabled = !alreadyAdded,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (alreadyAdded) {
                    camera.name + " · " + stringResource(R.string.import_already_added)
                } else {
                    camera.name
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.url_grid, UrlRedactor.redact(camera.gridUrl)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (camera.detailUrl.isBlank()) {
                    stringResource(R.string.url_detail_none)
                } else {
                    stringResource(R.string.url_detail, UrlRedactor.redact(camera.detailUrl))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
