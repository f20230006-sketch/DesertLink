package com.bitchat.android.desertlink

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.Timeout
import java.io.File

@RunWith(AndroidJUnit4::class)
class RescueOperationsDeviceTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.seconds(100)
    @get:Rule(order = 1) val ui = createEmptyComposeRule()

    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "desertlink-v02").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun createUpdateResolveAndDemo() {
        ui.waitUntil(45_000) { runCatching { ui.onAllNodesWithText("Create an SOS").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        ui.onNodeWithText("Create an SOS").performScrollTo().performClick()
        ui.onNodeWithText("Nearest landmark (optional)").performTextInput("Demo marker")
        ui.onNodeWithText("Situation / urgency").performTextInput("Synthetic test only")
        ui.onNodeWithText("Save SOS and wait for a contact").performScrollTo().performClick()
        val text = "Breakdown · 1 person. Near Demo marker. Synthetic test only"
        ui.waitUntil(10_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(text).performScrollTo().performClick()
        ui.onNodeWithText("LOCAL EVENT HISTORY").assertExists()
        capture("incident-saved")
        ui.onNodeWithText("Update situation / urgency").performScrollTo().performClick()
        ui.onNodeWithText("Updated situation").performTextReplacement("Medical · 1 person. Synthetic test only.")
        ui.onNodeWithText("Save the new revision").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("SOS · v2").fetchSemanticsNodes().isNotEmpty() }
        capture("incident-updated")
        ui.onNodeWithText("Mark resolved and notify the network").performScrollTo().performClick()
        ui.onNodeWithText("Save resolution revision").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Resolution · v3").fetchSemanticsNodes().isNotEmpty() }
        capture("incident-resolved")
        ui.onNodeWithContentDescription("Back to incidents").performClick()
        ui.onNodeWithText("Demo", useUnmergedTree = true).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Start the demo").fetchSemanticsNodes().isNotEmpty() }
        capture("demo-ready")
        ui.onNodeWithText("Start the demo").performScrollTo().performClick()
        repeat(4) { ui.onNodeWithText("Next contact / event →").performScrollTo().performClick(); ui.waitForIdle() }
        ui.onNodeWithText("Latest revision is NOT confirmed").assertExists()
        capture("demo-old-ack")
        repeat(3) { ui.onNodeWithText("Next contact / event →").performScrollTo().performClick(); ui.waitForIdle() }
        ui.onNodeWithText("✓ Latest revision confirmed").assertExists()
        capture("demo-confirmed")
    }
}
