package io.github.vandosketch.camgrid.ui

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.StreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runners.model.Statement
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraEditorScreenTest {
    val compose = createComposeRule()

    // createComposeRule launches an empty ComponentActivity. Registering it here keeps
    // ui-test-manifest, and that exported activity, out of the APKs.
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    val app = ApplicationProvider.getApplicationContext<Application>()
                    shadowOf(app.packageManager)
                        .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
                    base.evaluate()
                }
            }
        }
        .around(compose)

    private val saved = mutableListOf<Camera>()
    private var cancels = 0

    private lateinit var focusManager: FocusManager

    private fun show(camera: Camera? = null) {
        compose.setContent {
            focusManager = LocalFocusManager.current
            CamGridTheme {
                CameraEditorScreen(
                    camera = camera,
                    onSave = { saved += it },
                    onCancel = { cancels++ },
                )
            }
        }
    }

    @Test
    fun hasOneSaveAndOneCancel() {
        show()
        compose.onAllNodesWithText("Cancel").assertCountEquals(1)
        compose.onAllNodesWithText("Save").assertCountEquals(1)
    }

    @Test
    fun saveIsInTheHeaderAndCancelAtTheBottom() {
        show()
        // Unclipped: on a small screen Cancel is below the fold.
        fun top(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().positionInRoot.y
        val save = top(compose.onNodeWithText("Save"))
        val name = top(compose.onNode(hasSetTextAction() and hasText("Name")))
        val detailUrl = top(compose.onNode(hasSetTextAction() and hasText("Detail URL", substring = true)))
        val cancel = top(compose.onNodeWithText("Cancel"))
        assertTrue("Save sits above the first field", save < name)
        assertTrue("Cancel sits below the last field", cancel > detailUrl)
    }

    @Test
    fun saveRejectsAnInvalidCamera() {
        show()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<Camera>(), saved)
        compose.onNodeWithText("Enter a name.").assertExists()
    }

    @Test
    fun saveStoresAValidCamera() {
        show()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Front door")
        compose.onNode(hasSetTextAction() and hasText("Grid URL", substring = true)).performTextInput(" rtsp://camera.example/sub ")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(1, saved.size)
        assertEquals("Front door", saved[0].name)
        assertEquals("rtsp://camera.example/sub", saved[0].gridUrl)
        assertEquals(StreamType.RTSP, saved[0].streamType)
        assertEquals(0, cancels)
    }

    @Test
    fun cancelLeavesWithoutSaving() {
        show(Camera(id = "a", name = "Garden", gridUrl = "rtsp://camera.example/a", detailUrl = ""))
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, cancels)
        assertEquals(emptyList<Camera>(), saved)
    }

    // Fire TV: the editor opens on the name field. D-pad up goes to Save, and D-pad down from the
    // last field goes to Cancel. A test key event is not from a D-pad device, so text fields would
    // keep it; moveFocus runs the same geometric focus search the D-pad uses.
    @Test
    fun dpadUpFromTheNameFieldReachesSave() {
        show()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Name")).assertIsFocused()
        compose.runOnIdle { focusManager.moveFocus(FocusDirection.Up) }
        compose.onNodeWithText("Save").assertIsFocused()
    }

    @Test
    fun dpadDownFromTheLastFieldReachesCancel() {
        show()
        compose.onNode(hasSetTextAction() and hasText("Detail URL", substring = true)).performScrollTo().requestFocus()
        compose.runOnIdle { focusManager.moveFocus(FocusDirection.Down) }
        compose.onNodeWithText("Cancel").assertIsFocused()
    }
}
