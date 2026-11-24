package com.mairwunnx.capsa.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mairwunnx.capsa.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [Slot] on a phone: a real composition in a real window, with layout, drawing, and touches.
 *
 * Host tests run `SlotTest` under Robolectric with an empty `Applier`. Here the slot draws, the user taps, and the model changes.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SlotOnDeviceTest {

    private data class LikeClicked(val likes: Int) : Intent

    private val likeSlot = Slot<Int> { ui, onIntent, modifier ->
        BasicText(text = "Likes: $ui", modifier = modifier.clickable { onIntent(LikeClicked(ui)) })
    }

    @Test
    fun aSlotDrawsItsModelWithTheCallersModifierAndSendsTheTap() = runComposeUiTest {
        val sent = mutableListOf<Intent>()
        setContent {
            likeSlot(1, { sent += it }, Modifier.testTag(TAG))
        }

        onNodeWithTag(TAG).assertTextEquals("Likes: 1").performClick()

        assertEquals(listOf<Intent>(LikeClicked(1)), sent)
    }

    @Test
    fun aSlotRedrawsWhenTheModelChanges() = runComposeUiTest {
        var likes by mutableIntStateOf(1)
        setContent {
            likeSlot(likes, { likes++ }, Modifier.testTag(TAG))
        }

        onNodeWithTag(TAG).performClick()
        onNodeWithTag(TAG).performClick()

        onNodeWithTag(TAG).assertTextEquals("Likes: 3")
    }

    @Test
    fun theOverloadWithoutAModifierDrawsTheSlot() = runComposeUiTest {
        setContent {
            likeSlot(7, {})
        }

        onNodeWithText("Likes: 7").assertExists()
    }

    private companion object {
        const val TAG = "like"
    }
}
