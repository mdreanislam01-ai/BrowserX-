package com.browserx.app

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

class BrowserSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        ui.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val file = File(inst.targetContext.getExternalFilesDir("smoke"), "$name.png")
        inst.uiAutomation.takeScreenshot()?.let { bmp ->
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
    }

    private fun command(text: String) {
        ui.onNodeWithText("Search or enter an address").performClick()
        ui.onNodeWithContentDescription("Address input").performTextReplacement(text)
        ui.onNodeWithContentDescription("Address input").performImeAction()
    }

    @Test
    fun useTheBrowserFromColdStart() {
        ui.onNodeWithContentDescription("Add favorite").performClick()
        ui.onNodeWithText("Name (optional)").performTextInput("My site")
        ui.onNodeWithText("Web address").performTextInput("example.org")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithText("My site").assertIsDisplayed()
        capture("01-start")
        command(">new")
        ui.onNodeWithContentDescription("Tab overview").performClick()
        ui.onNodeWithText("2 tabs").assertIsDisplayed()
        capture("02-coverflow")
        ui.onNodeWithContentDescription("Tab options").performClick()
        ui.onNodeWithText("List").performClick()
        ui.onAllNodesWithContentDescription("Pin tab").onFirst().performClick()
        ui.onAllNodesWithContentDescription("Close tab").filter(isEnabled()).onFirst().performClick()
        ui.onNodeWithText("1 tab").assertIsDisplayed()
        ui.onNodeWithContentDescription("Back to browsing").performClick()
        ui.onNodeWithContentDescription("More options").performClick()
        capture("03-menu")
        ui.onNodeWithContentDescription("Settings").performClick()
        capture("04-settings")
        ui.onNodeWithText("About BrowserX³").performScrollTo().performClick()
        ui.onNodeWithText("Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}")
            .performScrollTo()
            .assertIsDisplayed()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithContentDescription("Back").performClick()
        // Navigate through the real address field. Then supply a local page to avoid depending on a live
        // website.
        command("https://example.test/article")
        fun findWeb(v: View): WebView? {
            if (v is WebView) return v
            if (v is ViewGroup)
                for (i in 0 until v.childCount) findWeb(v.getChildAt(i))?.let {
                    return it
                }
            return null
        }
        val browserActivity = ui.activity
        val url = "https://example.test/article"
        ui.runOnIdle {
            checkNotNull(findWeb(browserActivity.window.decorView))
                .loadDataWithBaseURL(
                    url,
                    "<title>Local article</title><article><h1>Offline fixture</h1><p>A paragraph long enough to verify the real Reader Studio extraction.</p></article>",
                    "text/html",
                    "UTF-8",
                    url,
                )
        }
        ui.waitUntil(15000) {
            ui.runOnUiThread { findWeb(browserActivity.window.decorView)?.title == "Local article" }
        }
        ui.onNodeWithText("example.test").performClick()
        ui.onNodeWithContentDescription("Address input").performTextReplacement(">reader")
        ui.onNodeWithContentDescription("Address input").performImeAction()
        ui.waitUntil(10000) { ui.onAllNodesWithText("Offline fixture").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("A paragraph long enough to verify the real Reader Studio extraction.")
            .assertIsDisplayed()
        capture("05-reader")
        ui.onNodeWithText("Save offline").performClick()
        ui.onNodeWithContentDescription("Close reader").performClick()
        ui.onNodeWithText("example.test").performClick()
        ui.onNodeWithContentDescription("Address input").performTextReplacement(">reading")
        ui.onNodeWithContentDescription("Address input").performImeAction()
        ui.waitUntil(10000) { ui.onAllNodesWithText("Offline fixture").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Offline fixture").performClick()
        ui.onNodeWithText("A paragraph long enough to verify the real Reader Studio extraction.")
            .assertIsDisplayed()
        ui.onNodeWithText("Save offline").assertDoesNotExist()
    }
}
