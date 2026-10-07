package io.github.vandosketch.camgrid.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class UiTestHarnessTest {
    @Test
    fun rendersInTheme() = runComposeUiTest {
        setContent { CamGridTheme { Text("hello") } }
        onNodeWithText("hello").assertExists()
    }
}
