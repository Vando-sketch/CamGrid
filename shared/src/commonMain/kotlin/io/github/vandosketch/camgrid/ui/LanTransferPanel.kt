package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.LanTransferState
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.bk_lan_address
import io.github.vandosketch.camgrid.shared.resources.bk_lan_download_ready
import io.github.vandosketch.camgrid.shared.resources.bk_lan_how
import io.github.vandosketch.camgrid.shared.resources.bk_lan_locked
import io.github.vandosketch.camgrid.shared.resources.bk_lan_open
import io.github.vandosketch.camgrid.shared.resources.bk_lan_pin
import io.github.vandosketch.camgrid.shared.resources.bk_lan_qr_description
import io.github.vandosketch.camgrid.shared.resources.bk_lan_starting
import io.github.vandosketch.camgrid.shared.resources.bk_lan_unavailable
import io.github.vandosketch.camgrid.shared.resources.bk_lan_unencrypted
import io.github.vandosketch.camgrid.transfer.LanTransferProtocol
import io.github.vandosketch.camgrid.transfer.QrCode
import org.jetbrains.compose.resources.stringResource

/**
 * The transfer section of the backup screen on a TV, as one card: a QR code that opens the
 * transfer page with the PIN filled in, the address and PIN for typing them by hand, and what to
 * do there. [downloadName] is the export waiting on the page.
 */
@Composable
internal fun LanTransferPanel(state: LanTransferState, downloadName: String?) {
    when (state) {
        is LanTransferState.Running -> Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(20.dp),
            ) {
                // White and rounded behind the code's own quiet zone, so it reads as a card on the dark theme.
                Box(Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
                    QrCodeImage(
                        text = LanTransferProtocol.linkWithPin(state.url, state.pin),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.bk_lan_open),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    HelpText(stringResource(Res.string.bk_lan_address))
                    Text(
                        text = state.url,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(Res.string.bk_lan_pin, state.pin),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    if (downloadName != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = stringResource(Res.string.bk_lan_download_ready, downloadName),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    HelpText(stringResource(Res.string.bk_lan_how))
                    HelpText(stringResource(Res.string.bk_lan_unencrypted))
                }
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
            .semantics {
                contentDescription = description
                qrText = text
            },
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

/** The text a [QrCodeImage] encodes, for tests. */
internal val QrText = SemanticsPropertyKey<String>("QrText")
private var SemanticsPropertyReceiver.qrText by QrText

private const val QUIET_ZONE = 4
