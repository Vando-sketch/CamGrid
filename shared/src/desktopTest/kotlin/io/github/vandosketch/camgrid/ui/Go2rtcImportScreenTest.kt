package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.ImportState
import io.github.vandosketch.camgrid.core.StreamType
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class Go2rtcImportScreenTest {

    /** More suggestions than fit on a 720 px high window. */
    private val manyStreams = ImportState.Loaded(
        baseUrl = "http://go2rtc.invalid:1984",
        streamNames = (1..30).map { "stream" + it.toString().padStart(2, '0') },
        streamType = StreamType.RTSP,
        selected = emptySet(),
    )

    @Test
    fun theHeaderStaysWhileTheSuggestionsScroll() = runComposeUiTest {
        setContent {
            CamGridTheme {
                Box(Modifier.size(width = 1024.dp, height = 720.dp)) {
                    Go2rtcImportScreen(
                        initialBaseUrl = manyStreams.baseUrl,
                        existingIds = emptySet(),
                        state = manyStreams,
                        streamType = StreamType.RTSP,
                        onStreamTypeChange = {},
                        onFetch = {},
                        onToggle = {},
                        onImport = {},
                        onBack = {},
                    )
                }
            }
        }
        onNodeWithTag(IMPORT_LIST_TAG).performScrollToNode(hasText("stream30"))
        onNodeWithText("stream30").assertIsDisplayed()
        onNodeWithText("Import from go2rtc").assertIsDisplayed()
        onNodeWithText("Back").assertIsDisplayed()
        assertTrue(onAllNodesWithTag(SCROLLBAR_TAG).fetchSemanticsNodes().isNotEmpty(), "no scrollbar")
    }
}
