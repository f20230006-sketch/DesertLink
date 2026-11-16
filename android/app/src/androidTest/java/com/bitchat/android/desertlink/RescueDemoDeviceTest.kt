package com.bitchat.android.desertlink

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real UI on the connected phone; simulated-node behavior is explicitly labeled. */
@RunWith(AndroidJUnit4::class)
class RescueDemoDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "desertlink-demo").apply { mkdirs() }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun disconnectedUpdateAndVersionSpecificAcknowledgment() {
        compose.setContent { MaterialTheme(typography = DesertTypography) { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) { OfflineDemoContent() } } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Start the demo").fetchSemanticsNodes().isNotEmpty() }
        capture("00-ready")
        compose.onNodeWithText("Start the demo").performScrollTo().performClick()
        compose.waitForIdle()
        capture("01-stored")
        compose.onNodeWithText("Next contact / event →").performScrollTo().performClick()
        compose.waitForIdle()
        capture("02-carried")
        compose.onNodeWithText("Next contact / event →").performScrollTo().performClick()
        compose.waitForIdle()
        capture("03-isolated-update")
        compose.onNodeWithText("Next contact / event →").performScrollTo().performClick()
        compose.waitForIdle()
        capture("04-old-version-delivered")
        compose.onNodeWithText("Next contact / event →").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Latest revision is NOT confirmed").assertExists()
        compose.onNodeWithText("Old ACK returns — v2 stays unconfirmed").assertExists()
        capture("05-old-ack-rejected")
        repeat(3) {
            compose.onNodeWithText("Next contact / event →").performScrollTo().performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("✓ Latest revision confirmed").assertExists()
        capture("08-current-version-confirmed")
    }
}
