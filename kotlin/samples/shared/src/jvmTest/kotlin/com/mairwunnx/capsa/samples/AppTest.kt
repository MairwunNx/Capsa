package com.mairwunnx.capsa.samples

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {

    @Test
    fun `every sample opens and goes back`() = runComposeUiTest {
        setContent { App() }
        listOf(
            "Counter" to "+1",
            "Search" to "A fruit",
            "Like" to "128 likes",
            "Clock" to "00:00",
            "Profile form" to "Save",
            "Dashboard" to "Refresh both",
            "Crash-free" to "Throw in decide",
            "Two screens" to "Buy oat milk",
        ).forEach { (sample, content) ->
            onNode(hasScrollAction()).performScrollToNode(hasText(sample))
            onNodeWithText(sample).performClick()
            waitForIdle()
            onNodeWithText(content, substring = true).assertExists()
            onNodeWithContentDescription("Back").performClick()
            waitForIdle()
        }
    }
}
