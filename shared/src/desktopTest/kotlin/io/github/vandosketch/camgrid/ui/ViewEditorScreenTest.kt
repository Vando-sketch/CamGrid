package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ViewEditorScreenTest {

    private val cameras = listOf(
        Camera(id = "front", name = "Front door", gridUrl = "rtsp://camera.invalid/front"),
        Camera(id = "garden", name = "Garden", gridUrl = "rtsp://camera.invalid/garden"),
        Camera(id = "garage", name = "Garage", gridUrl = "rtsp://camera.invalid/garage"),
    )

    /** 2x2 grid of auto tiles: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
    private val grid = CamView.uniform("main", "Living room", 2, 2)

    /** Every edit [onChange] reported, in order; the host feeds each back as the new view. */
    private val changes = mutableListOf<CamView>()

    /** [phoneWidth] squeezes the editor into a portrait phone's width: one column. */
    private fun ComposeUiTest.showEditor(
        initial: CamView = grid,
        canDelete: Boolean = true,
        phoneWidth: Boolean = false,
    ) {
        setContent {
            var view by remember { mutableStateOf(initial) }
            val focusManager = LocalFocusManager.current
            CamGridTheme {
                // Compose desktop moves focus only on Tab; Android also on an arrow that no
                // element used. This does the latter, so the tests see what a TV does.
                Box(
                    (if (phoneWidth) Modifier.width(400.dp) else Modifier).onKeyEvent { event ->
                        val direction = event.key.toFocusDirection()
                        event.type == KeyEventType.KeyDown && direction != null && focusManager.moveFocus(direction)
                    },
                ) {
                    ViewEditorScreen(
                        view = view,
                        cameras = cameras,
                        canDelete = canDelete,
                        onChange = {
                            changes += it
                            view = it
                        },
                        onDelete = {},
                        onDone = {},
                    )
                }
            }
        }
    }

    private fun Key.toFocusDirection(): FocusDirection? = when (this) {
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        else -> null
    }

    private fun ComposeUiTest.preview(): SemanticsNodeInteraction = onNodeWithTag(VE_TAG_PREVIEW)

    private fun ComposeUiTest.press(key: Key, node: SemanticsNodeInteraction = preview()) {
        node.performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.focusPreview() {
        preview().requestFocus()
        waitForIdle()
    }

    private fun ComposeUiTest.dialogNode(text: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(VE_TAG_TILE_DIALOG)))

    @Test
    fun arrowsChangeTheSelectedTile() = runComposeUiTest {
        showEditor()
        focusPreview()
        onNodeWithTag(veTileTag(0)).assertIsSelected()

        press(Key.DirectionRight)
        onNodeWithTag(veTileTag(1)).assertIsSelected()
        onNodeWithTag(veTileTag(0)).assertIsNotSelected()

        press(Key.DirectionDown)
        onNodeWithTag(veTileTag(3)).assertIsSelected()
        assertTrue(changes.isEmpty(), "selecting a tile is not an edit")
    }

    @Test
    fun okOnThePreviewOpensTheTileDialog() = runComposeUiTest {
        showEditor()
        focusPreview()
        press(Key.DirectionRight)
        onNodeWithTag(VE_TAG_TILE_DIALOG).assertDoesNotExist()

        press(Key.Enter)
        onNodeWithTag(VE_TAG_TILE_DIALOG).assertExists()
        onNodeWithText("Tile 2").assertExists()
        // The tile's current camera (Auto) is focused first, so OK keeps it.
        dialogNode("Auto").assertIsFocused()
    }

    @Test
    fun choosingACameraSetsItOnThatTileAndClosesTheDialog() = runComposeUiTest {
        showEditor()
        focusPreview()
        press(Key.DirectionRight)
        press(Key.Enter)

        dialogNode("Garden").performClick()
        waitForIdle()

        assertEquals("garden", changes.last().tiles[1].camera)
        assertEquals(listOf(null, null, null), changes.last().tiles.filterIndexed { i, _ -> i != 1 }.map { it.camera })
        onNodeWithTag(VE_TAG_TILE_DIALOG).assertDoesNotExist()
    }

    @Test
    fun theDialogCameraListIsDpadNavigable() = runComposeUiTest {
        showEditor()
        focusPreview()
        press(Key.Enter)

        // Auto is focused; two steps on reach the second camera, OK picks it. Tab stands in
        // for Down: the dialog is its own window, outside the test host's arrow mapping.
        press(Key.Tab, dialogNode("Auto"))
        dialogNode("Front door").assertIsFocused()
        press(Key.Tab, dialogNode("Front door"))
        dialogNode("Garden").assertIsFocused()
        press(Key.Enter, dialogNode("Garden"))

        assertEquals("garden", changes.last().tiles[0].camera)
        onNodeWithTag(VE_TAG_TILE_DIALOG).assertDoesNotExist()
    }

    @Test
    fun moveFromTheDialogThenAnArrowMovesTheTile() = runComposeUiTest {
        val view = CamView("main", "", columns = 3, rows = 2, tiles = listOf(Tile(0, 0), Tile(2, 1)))
        showEditor(view)
        focusPreview()
        press(Key.Enter)

        dialogNode("Move").performClick()
        waitForIdle()
        onNodeWithTag(VE_TAG_TILE_DIALOG).assertDoesNotExist()
        preview().assertIsFocused()
        onNodeWithText("Mode: Move").assertExists()

        press(Key.DirectionRight)
        assertEquals(Tile(1, 0), changes.last().tiles[0])
        assertEquals(Tile(2, 1), changes.last().tiles[1])
        // Still moving: the next arrow moves on; into the other tile's column is fine (row 0).
        press(Key.DirectionRight)
        assertEquals(Tile(2, 0), changes.last().tiles[0])
        // Off the canvas is impossible: no edit is reported.
        press(Key.DirectionRight)
        assertEquals(2, changes.size)
    }

    @Test
    fun resizeFromTheDialogThenAnArrowGrowsTheTile() = runComposeUiTest {
        val view = CamView("main", "", columns = 3, rows = 2, tiles = listOf(Tile(0, 0), Tile(2, 1)))
        showEditor(view)
        focusPreview()
        press(Key.Enter)

        dialogNode("Resize").performClick()
        waitForIdle()
        press(Key.DirectionDown)
        assertEquals(Tile(0, 0, w = 1, h = 2), changes.last().tiles[0])
    }

    @Test
    fun escapeInMoveModeReturnsToSelect() = runComposeUiTest {
        val view = CamView("main", "", columns = 3, rows = 2, tiles = listOf(Tile(0, 0), Tile(2, 1)))
        showEditor(view)
        focusPreview()
        press(Key.Enter)
        dialogNode("Move").performClick()
        waitForIdle()

        press(Key.Escape)
        onNodeWithText("Mode: Select").assertExists()
        // Back in Select mode the arrows pick tiles again instead of moving them.
        press(Key.DirectionDown)
        assertTrue(changes.isEmpty())
        onNodeWithTag(veTileTag(1)).assertIsSelected()
    }

    @Test
    fun okInMoveModeReturnsToSelect() = runComposeUiTest {
        val view = CamView("main", "", columns = 3, rows = 2, tiles = listOf(Tile(0, 0), Tile(2, 1)))
        showEditor(view)
        focusPreview()
        press(Key.Enter)
        dialogNode("Move").performClick()
        waitForIdle()

        press(Key.Enter)
        onNodeWithText("Mode: Select").assertExists()
        onNodeWithTag(VE_TAG_TILE_DIALOG).assertDoesNotExist()
    }

    @Test
    fun tappingTheSelectedTileOpensTheDialog() = runComposeUiTest {
        showEditor()
        onNodeWithTag(veTileTag(2)).performClick()
        waitForIdle()
        onNodeWithTag(veTileTag(2)).assertIsSelected()
        onNodeWithTag(VE_TAG_TILE_DIALOG).assertDoesNotExist()

        onNodeWithTag(veTileTag(2)).performClick()
        waitForIdle()
        onNodeWithText("Tile 3").assertExists()
    }

    @Test
    fun rightFromThePreviewEdgeLandsOnTheSelectedTileSection() = runComposeUiTest {
        showEditor()
        focusPreview()
        press(Key.DirectionRight)
        // Tile 1 is in the right column: one more Right leaves the preview.
        press(Key.DirectionRight)
        onNodeWithTag(VE_TAG_CHANGE_CAMERA).assertIsFocused()
    }

    @Test
    fun onAPhoneDownFromThePreviewBottomLandsOnTheSelectedTileSection() = runComposeUiTest {
        showEditor(phoneWidth = true)
        focusPreview()
        press(Key.DirectionDown)
        onNodeWithTag(veTileTag(2)).assertIsSelected()
        press(Key.DirectionDown)
        onNodeWithTag(VE_TAG_CHANGE_CAMERA).assertIsFocused()
        // One column: the preview sits above the sections, which keep their order.
        val previewBottom = preview().getUnclippedBoundsInRoot().bottom
        val tops = listOf("Selected tile", "Layout", "View").map {
            onNodeWithText(it).getUnclippedBoundsInRoot().top
        }
        assertTrue(previewBottom < tops[0])
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun changeButtonOpensTheDialogForTheSelectedTile() = runComposeUiTest {
        showEditor()
        onNodeWithTag(VE_TAG_CHANGE_CAMERA).performClick()
        waitForIdle()
        onNodeWithText("Tile 1").assertExists()
        dialogNode("Front door").performClick()
        waitForIdle()
        assertEquals("front", changes.last().tiles[0].camera)
    }

    @Test
    fun sectionsAreOrderedSelectedTileLayoutView() = runComposeUiTest {
        showEditor()
        val tops = listOf("Selected tile", "Layout", "View").map {
            onNodeWithText(it).getUnclippedBoundsInRoot().top
        }
        assertEquals(tops.sorted(), tops, "section tops: $tops")
        // The settings that belong to each section sit below its title, before the next one.
        val nameTop = onNodeWithText("Name").getUnclippedBoundsInRoot().top
        val columnsTop = onNodeWithText("Columns").getUnclippedBoundsInRoot().top
        assertTrue(columnsTop > tops[1] && columnsTop < tops[2], "Columns belongs to Layout")
        assertTrue(nameTop > tops[2], "Name belongs to View")
    }

    @Test
    fun deleteDialogFocusesCancelFirst() = runComposeUiTest {
        showEditor()
        onNodeWithText("Delete view").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Cancel").assertIsFocused()
    }
}
