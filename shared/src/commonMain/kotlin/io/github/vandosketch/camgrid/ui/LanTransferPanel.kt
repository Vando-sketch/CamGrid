package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.LanTransferState
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.bk_lan_download_ready
import io.github.vandosketch.camgrid.shared.resources.bk_lan_how
import io.github.vandosketch.camgrid.shared.resources.bk_lan_locked
import io.github.vandosketch.camgrid.shared.resources.bk_lan_open
import io.github.vandosketch.camgrid.shared.resources.bk_lan_pin
import io.github.vandosketch.camgrid.shared.resources.bk_lan_qr_description
import io.github.vandosketch.camgrid.shared.resources.bk_lan_starting
import io.github.vandosketch.camgrid.shared.resources.bk_lan_unavailable
import io.github.vandosketch.camgrid.shared.resources.bk_lan_unencrypted
import io.github.vandosketch.camgrid.transfer.QrCode
import org.jetbrains.compose.resources.stringResource

/**
 * The transfer section of the backup screen on a TV: the address of the transfer page as text
 * and QR code, the PIN, and what to do there. [downloadName] is the export waiting on the page.
 */
@Composable
internal fun LanTransferPanel(state: LanTransferState, downloadName: String?) {
    when (state) {
        is LanTransferState.Running -> Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QrCodeImage(text = state.url, modifier = Modifier.size(176.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HelpText(stringResource(Res.string.bk_lan_open))
                Text(
                    text = state.url,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.bk_lan_pin, state.pin),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (downloadName != null) {
                    Text(
                        text = stringResource(Res.string.bk_lan_download_ready, downloadName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HelpText(stringResource(Res.string.bk_lan_how))
                HelpText(stringResource(Res.string.bk_lan_unencrypted))
            }
        }
        LanTransferState.Starting -> HelpText(stringResource(Res.string.bk_lan_starting))
        LanTransferState.Unavailable -> HelpText(stringResource(Res.string.bk_lan_unavailable))
        LanTransferState.Locked -> Text(
            text = stringResource(Res.string.bk_lan_locked),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        LanTransferState.Off -> Unit
    }
}

/**
 * [text] as a QR code: dark modules on white with the standard four-module quiet zone, in any
 * theme, because scanners expect dark on light. Draws nothing if the text is too long.
 */
@Composable
internal fun QrCodeImage(text: String, modifier: Modifier = Modifier) {
    val code = remember(text) { runCatching { QrCode.encode(text) }.getOrNull() } ?: return
    val description = stringResource(Res.string.bk_lan_qr_description)
    Canvas(
        modifier = modifier
            .testTag(QR_TAG)
            .semantics { contentDescription = description },
    ) {
        drawRect(Color.White)
        val modules = code.size + 2 * QUIET_ZONE
        val cell = minOf(size.width, size.height) / modules
        // Overlap by a fraction of a pixel so no hairlines appear between neighbouring modules.
        val module = Size(cell + 0.5f, cell + 0.5f)
        for (y in 0 until code.size) for (x in 0 until code.size) {
            if (code.isDark(x, y)) {
                drawRect(Color.Black, Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell), module)
            }
        }
    }
}

/** Test tag of [QrCodeImage]. */
internal const val QR_TAG = "transfer-qr"

private const val QUIET_ZONE = 4
