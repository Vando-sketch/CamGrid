package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.SourceSetupState
import io.github.vandosketch.camgrid.core.ConfigSource
import io.github.vandosketch.camgrid.core.RemoteConfig
import io.github.vandosketch.camgrid.core.RemoteConfigException
import io.github.vandosketch.camgrid.core.SourceUrlCheck
import io.github.vandosketch.camgrid.core.UrlRedactor
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.back
import io.github.vandosketch.camgrid.shared.resources.cancel
import io.github.vandosketch.camgrid.shared.resources.source_connecting
import io.github.vandosketch.camgrid.shared.resources.source_error_credentials
import io.github.vandosketch.camgrid.shared.resources.source_error_encrypted
import io.github.vandosketch.camgrid.shared.resources.source_error_http
import io.github.vandosketch.camgrid.shared.resources.source_error_invalid_url
import io.github.vandosketch.camgrid.shared.resources.source_error_network
import io.github.vandosketch.camgrid.shared.resources.source_error_newer_version
import io.github.vandosketch.camgrid.shared.resources.source_error_too_large
import io.github.vandosketch.camgrid.shared.resources.source_error_unreadable
import io.github.vandosketch.camgrid.shared.resources.source_field_token
import io.github.vandosketch.camgrid.shared.resources.source_field_url
import io.github.vandosketch.camgrid.shared.resources.source_help
import io.github.vandosketch.camgrid.shared.resources.source_hint_url
import io.github.vandosketch.camgrid.shared.resources.source_load
import io.github.vandosketch.camgrid.shared.resources.source_replace_confirm
import io.github.vandosketch.camgrid.shared.resources.source_replace_message
import io.github.vandosketch.camgrid.shared.resources.source_replace_title
import io.github.vandosketch.camgrid.shared.resources.source_stop
import io.github.vandosketch.camgrid.shared.resources.source_stop_help
import io.github.vandosketch.camgrid.shared.resources.source_title
import io.github.vandosketch.camgrid.shared.resources.source_token_help
import io.github.vandosketch.camgrid.shared.resources.source_token_invalid
import io.github.vandosketch.camgrid.shared.resources.source_url_credentials
import io.github.vandosketch.camgrid.shared.resources.source_url_invalid
import io.github.vandosketch.camgrid.shared.resources.source_url_local_http
import io.github.vandosketch.camgrid.shared.resources.source_url_public_http
import org.jetbrains.compose.resources.stringResource

/** Test tags of the URL and token fields. */
internal const val SOURCE_URL_TAG = "source:url"
internal const val SOURCE_TOKEN_TAG = "source:token"

/**
 * Sets up, changes or stops the config URL: cameras and views loaded from a hosted file
 * ([RemoteConfig]). The URL is checked while it is typed; Load tries it ([onConnect]) and only a
 * file that loads replaces anything, which the first time ([current] null) is confirmed first.
 * The progress and why a try failed come from the ViewModel ([setup]). [onRemove] stops using
 * the URL ([current] set); the cameras stay.
 */
@Composable
fun ConfigSourceScreen(
    current: ConfigSource?,
    setup: SourceSetupState,
    onConnect: (url: String, token: String) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(current?.url.orEmpty()) }
    // Plain remember, like the backup passwords: the token stays out of the saved state.
    var token by remember { mutableStateOf(current?.token.orEmpty()) }
    var confirmReplace by remember { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }
    InitialFocus(fieldRequester)

    val check = RemoteConfig.checkUrl(url)
    val tokenValid = RemoteConfig.isValidToken(token.trim())
    val canLoad = RemoteConfig.isUsableUrl(url) && tokenValid
    val connecting = setup == SourceSetupState.Connecting
    fun load() {
        // Ignored while a try runs, rather than disabling the button: that would drop the D-pad focus.
        if (!canLoad || connecting) return
        if (current == null) confirmReplace = true else onConnect(url, token)
    }

    val scrollState = rememberScrollState()
    ScrollbarBox(scrollState, Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenHeader(
                title = stringResource(Res.string.source_title),
                actionLabel = stringResource(Res.string.back),
                onAction = onBack,
            )

            val urlProblem = when (check) {
                SourceUrlCheck.HTTPS, SourceUrlCheck.LOCAL_HTTP -> null
                SourceUrlCheck.PUBLIC_HTTP -> stringResource(Res.string.source_url_public_http)
                SourceUrlCheck.CREDENTIALS -> stringResource(Res.string.source_url_credentials)
                SourceUrlCheck.INVALID -> if (url.isBlank()) null else stringResource(Res.string.source_url_invalid)
            }
            CamTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(Res.string.source_field_url)) },
                placeholder = { Text(stringResource(Res.string.source_hint_url)) },
                isError = urlProblem != null,
                supportingText = {
                    when {
                        urlProblem != null -> Text(urlProblem)
                        check == SourceUrlCheck.LOCAL_HTTP -> Text(
                            text = stringResource(Res.string.source_url_local_http),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldRequester)
                    .testTag(SOURCE_URL_TAG),
            )
            CamTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(Res.string.source_field_token)) },
                isError = !tokenValid,
                supportingText = {
                    Text(stringResource(if (tokenValid) Res.string.source_token_help else Res.string.source_token_invalid))
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { load() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SOURCE_TOKEN_TAG),
            )
            Button(
                onClick = { load() },
                enabled = canLoad,
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(Res.string.source_load))
            }
            SetupMessage(setup)
            HelpText(stringResource(Res.string.source_help))

            if (current != null) {
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = {
                        onRemove()
                        onBack()
                    },
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(Res.string.source_stop))
                }
                HelpText(stringResource(Res.string.source_stop_help))
            }
        }
    }

    if (confirmReplace) {
        val cancelRequester = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(stringResource(Res.string.source_replace_title)) },
            text = { Text(stringResource(Res.string.source_replace_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplace = false
                        onConnect(url, token)
                    },
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(Res.string.source_replace_confirm))
                }
            },
            dismissButton = {
                // Focused first, so an accidental OK press does not replace the cameras.
                LaunchedEffect(Unit) { cancelRequester.requestFocusAfterLayout() }
                TextButton(
                    onClick = { confirmReplace = false },
                    modifier = Modifier
                        .focusBorder(shape = CircleShape)
                        .focusRequester(cancelRequester),
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/** The running try, or why the last one failed; nothing otherwise. */
@Composable
private fun SetupMessage(setup: SourceSetupState) {
    when (setup) {
        SourceSetupState.Idle -> Unit
        SourceSetupState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(Res.string.source_connecting))
        }
        is SourceSetupState.Failed -> Text(
            text = setupFailure(setup.reason, setup.detail),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun setupFailure(reason: RemoteConfigException.Reason, detail: String): String = when (reason) {
    // The detail is an HTTP status, never the URL or the token; redact anyway to be safe.
    RemoteConfigException.Reason.HTTP_STATUS -> stringResource(Res.string.source_error_http, UrlRedactor.redact(detail))
    RemoteConfigException.Reason.NETWORK -> stringResource(Res.string.source_error_network)
    RemoteConfigException.Reason.UNREADABLE -> stringResource(Res.string.source_error_unreadable)
    RemoteConfigException.Reason.ENCRYPTED -> stringResource(Res.string.source_error_encrypted)
    RemoteConfigException.Reason.NEWER_VERSION -> stringResource(Res.string.source_error_newer_version)
    RemoteConfigException.Reason.CREDENTIALS -> stringResource(Res.string.source_error_credentials)
    RemoteConfigException.Reason.TOO_LARGE -> stringResource(Res.string.source_error_too_large)
    RemoteConfigException.Reason.INVALID_URL -> stringResource(Res.string.source_error_invalid_url)
}
