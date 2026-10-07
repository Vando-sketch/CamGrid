package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BackDispatchTest {

    @Test
    fun goesToTheInnermostEnabledBackHandler() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var innerEnabled by mutableStateOf(true)
        lateinit var dispatchBack: () -> Unit
        setContent {
            BackHandler { calls += "outer" }
            BackHandler(enabled = innerEnabled) { calls += "inner" }
            dispatchBack = rememberBackDispatcher()
        }
        runOnIdle { dispatchBack() }
        assertEquals(listOf("inner"), calls)
        innerEnabled = false
        waitForIdle()
        runOnIdle { dispatchBack() }
        assertEquals(listOf("inner", "outer"), calls)
    }
}
