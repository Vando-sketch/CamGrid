package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.about.AppPlatform
import io.github.vandosketch.camgrid.about.ComponentNote
import io.github.vandosketch.camgrid.about.License
import io.github.vandosketch.camgrid.about.LicenseText
import io.github.vandosketch.camgrid.about.ThirdPartyComponent
import io.github.vandosketch.camgrid.about.ThirdPartyComponents
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.lic_back
import io.github.vandosketch.camgrid.shared.resources.lic_intro
import io.github.vandosketch.camgrid.shared.resources.lic_loading
import io.github.vandosketch.camgrid.shared.resources.lic_name_version
import io.github.vandosketch.camgrid.shared.resources.lic_note_ffmpeg_desktop
import io.github.vandosketch.camgrid.shared.resources.lic_note_jna
import io.github.vandosketch.camgrid.shared.resources.lic_note_vlckit_ios
import io.github.vandosketch.camgrid.shared.resources.lic_note_webrtc_ffmpeg_desktop
import io.github.vandosketch.camgrid.shared.resources.lic_platform_android
import io.github.vandosketch.camgrid.shared.resources.lic_platform_desktop
import io.github.vandosketch.camgrid.shared.resources.lic_platform_ios
import io.github.vandosketch.camgrid.shared.resources.lic_platforms
import io.github.vandosketch.camgrid.shared.resources.lic_section_components
import io.github.vandosketch.camgrid.shared.resources.lic_section_texts
import io.github.vandosketch.camgrid.shared.resources.lic_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Test tag of the Licenses screen's list. */
internal const val LICENSES_LIST_TAG = "licenses"

/** Test tag of a component's row on the Licenses screen. */
internal fun componentTag(name: String) = "component:$name"

/** Test tag of a license's row in the "License texts" section. */
internal fun licenseTag(license: License) = "license:${license.name}"

/**
 * Every third-party component of the apps with its license, version, the apps that ship it and
 * any LGPL notes, then the license texts. Selecting a component opens its (first) license text.
 *
 * Built for the D-pad like the other screens: each row is focusable, so Up and Down walk the
 * whole list and the lazy list scrolls the focused row into view. All components are listed
 * whichever app shows the screen, with the apps that contain each one.
 */
@Composable
fun LicensesScreen(onOpenLicense: (License) -> Unit, onBack: () -> Unit) {
    val backRequester = remember { FocusRequester() }
    InitialFocus(backRequester)

    val listState = rememberLazyListState()
    ScrollbarBox(listState, Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(LICENSES_LIST_TAG),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(Res.string.lic_title),
                    actionLabel = stringResource(Res.string.lic_back),
                    onAction = onBack,
                    actionRequester = backRequester,
                )
            }
            item {
                Text(
                    text = stringResource(Res.string.lic_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionTitle(stringResource(Res.string.lic_section_components)) }
            items(ThirdPartyComponents.all, key = { "component:" + it.name }) { component ->
                ComponentRow(component, onClick = { onOpenLicense(component.licenses.first()) })
            }
            item { SectionTitle(stringResource(Res.string.lic_section_texts)) }
            items(License.entries, key = { "license:" + it.name }) { license ->
                FocusableCard(onClick = { onOpenLicense(license) }, modifier = Modifier.testTag(licenseTag(license))) {
                    Text(license.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = license.spdxId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(component: ThirdPartyComponent, onClick: () -> Unit) {
    FocusableCard(onClick = onClick, modifier = Modifier.testTag(componentTag(component.name))) {
        Text(
            text = component.version?.let { stringResource(Res.string.lic_name_version, component.name, it) }
                ?: component.name,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = component.licenses.joinToString(", ") { it.title },
            style = MaterialTheme.typography.bodyMedium,
        )
        val platforms = component.platforms.sortedBy { it.ordinal }.map { stringResource(it.label) }
        Text(
            text = stringResource(Res.string.lic_platforms, platforms.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = component.url.removePrefix("https://"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        component.note?.let { note ->
            Text(
                text = stringResource(note.text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val AppPlatform.label: StringResource
    get() = when (this) {
        AppPlatform.ANDROID -> Res.string.lic_platform_android
        AppPlatform.DESKTOP -> Res.string.lic_platform_desktop
        AppPlatform.IOS -> Res.string.lic_platform_ios
    }

private val ComponentNote.text: StringResource
    get() = when (this) {
        ComponentNote.FFMPEG_DESKTOP -> Res.string.lic_note_ffmpeg_desktop
        ComponentNote.WEBRTC_FFMPEG_DESKTOP -> Res.string.lic_note_webrtc_ffmpeg_desktop
        ComponentNote.VLCKIT_IOS -> Res.string.lic_note_vlckit_ios
        ComponentNote.JNA_DUAL_LICENSE -> Res.string.lic_note_jna
    }

/** A list row that takes D-pad focus (with the focus border) and opens something on OK or tap. */
@Composable
private fun FocusableCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusBorder()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        content()
    }
}

/**
 * The full text of [license], read from the Compose resources. Every paragraph is focusable,
 * so the D-pad scrolls through the text a paragraph at a time; touch scrolls as usual.
 */
@Composable
fun LicenseTextScreen(license: License, onBack: () -> Unit) {
    var paragraphs by remember(license) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(license) {
        paragraphs = LicenseText.paragraphs(Res.readBytes(license.resourcePath).decodeToString())
    }
    val backRequester = remember { FocusRequester() }
    InitialFocus(backRequester)

    val listState = rememberLazyListState()
    ScrollbarBox(listState, Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                ScreenHeader(
                    title = license.title,
                    actionLabel = stringResource(Res.string.lic_back),
                    onAction = onBack,
                    actionRequester = backRequester,
                )
            }
            val loaded = paragraphs
            if (loaded == null) {
                item { Text(stringResource(Res.string.lic_loading), style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(loaded) { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusBorder()
                            .focusable()
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}
