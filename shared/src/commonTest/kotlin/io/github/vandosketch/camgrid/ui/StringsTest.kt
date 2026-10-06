package io.github.vandosketch.camgrid.ui

import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.bk_cameras
import io.github.vandosketch.camgrid.shared.resources.bk_replace_message
import io.github.vandosketch.camgrid.shared.resources.bk_views
import io.github.vandosketch.camgrid.shared.resources.delete_message
import io.github.vandosketch.camgrid.shared.resources.fullscreen_hint
import io.github.vandosketch.camgrid.shared.resources.page_indicator_named
import io.github.vandosketch.camgrid.shared.resources.status_offline
import io.github.vandosketch.camgrid.shared.resources.ve_delete_message
import io.github.vandosketch.camgrid.shared.resources.ve_stream_count
import io.github.vandosketch.camgrid.shared.resources.view_summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** The texts moved from Android string resources to Compose resources render exactly as before. */
class StringsTest {

    @Test
    fun formattedStringsRenderAsOnAndroid() = runTest {
        assertEquals("\"Kitchen\" will be removed from the grid.", getString(Res.string.delete_message, "Kitchen"))
        assertEquals("“Living” will be removed. Cameras are not deleted.", getString(Res.string.ve_delete_message, "Living"))
        assertEquals("Offline · retry in 8 s", getString(Res.string.status_offline, 8))
        assertEquals("ID main · 4 tiles on 2×2 cells", getString(Res.string.view_summary, "main", 4, 2, 2))
        assertEquals("Garden · 2 / 3", getString(Res.string.page_indicator_named, "Garden", "2 / 3"))
        assertEquals(
            "◀ ▶ or swipe: switch camera · OK: sound on/off · Back: grid",
            getString(Res.string.fullscreen_hint),
        )
    }

    @Test
    fun pluralsRenderAsOnAndroid() = runTest {
        assertEquals("1 camera", getPluralString(Res.plurals.bk_cameras, 1, 1))
        assertEquals("3 cameras", getPluralString(Res.plurals.bk_cameras, 3, 3))
        assertEquals("0 views", getPluralString(Res.plurals.bk_views, 0, 0))
        assertEquals("Plays 1 stream at once", getPluralString(Res.plurals.ve_stream_count, 1, 1))
        assertEquals("Plays 6 streams at once", getPluralString(Res.plurals.ve_stream_count, 6, 6))
        assertEquals(
            "The backup has 2 cameras and 1 view. It replaces your current 0 cameras and 1 view.",
            getString(Res.string.bk_replace_message, "2 cameras", "1 view", "0 cameras", "1 view"),
        )
    }
}
