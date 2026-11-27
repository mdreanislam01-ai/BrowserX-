package com.browserx.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that runs the real app on an emulator: the toolbar must be on top and the
 * main surfaces (start page, 3-dot menu, settings, updates section) must open.
 */
@RunWith(AndroidJUnit4::class)
class BrowserSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun startPageAndToolbarAreVisible() {
        waitForText("BrowserX³")
        // The 3-dot overflow menu lives in the toolbar.
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Privacy X-Ray").assertIsDisplayed()
    }

    @Test
    fun overflowMenuReachesSettingsAndUpdates() {
        waitForText("BrowserX³")
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Check for updates")
        composeRule.onNodeWithText("Check for updates").assertIsDisplayed()
    }

    @Test
    fun tabCounterIsShownInToolbar() {
        waitForText("BrowserX³")
        // One open tab, so the counter pill shows "1".
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
