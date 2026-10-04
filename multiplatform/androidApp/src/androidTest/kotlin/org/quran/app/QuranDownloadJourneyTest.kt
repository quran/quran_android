package org.quran.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuranDownloadJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun removeOneDownloadPreservesOtherAudioAndReadingProgressAfterRelaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("quran.study", Context.MODE_PRIVATE)
        // Save existing preferences and files: this fixture must not erase a user's cache.
        val originalPreferences = preferences.all.toMap()
        val directory = File(context.filesDir, "recitations")
        directory.mkdirs()
        val first = File(directory, "alafasy-001001.mp3")
        val second = File(directory, "alafasy-001002.mp3")
        val originals = listOf(first, second).associateWith { if (it.exists()) it.readBytes() else null }
        val fixture = ByteArray(834) // Synthetic inventory fixture, never played as Quran audio.
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            assertTrue(preferences.edit().clear().commit())
            first.writeBytes(fixture)
            second.writeBytes(fixture)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.onNodeWithText("Open last read").performClick()
            compose.onNodeWithTag("reader_list").performScrollToNode(hasTestTag("verse_actions_1_2"))
            compose.onNodeWithTag("verse_actions_1_2").performClick()
            compose.onNodeWithText("Mark as read").performClick()
            compose.onNode(hasText("Settings") and hasClickAction()).performClick()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Manage downloads"))
            compose.onNodeWithText("Manage downloads").performClick()
            val remove = "remove_download_alafasy_1_1"
            compose.waitUntil(10_000) { compose.onAllNodesWithTag(remove).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("downloads_list").performScrollToNode(hasTestTag(remove))
            val captureDirectory = File(context.getExternalFilesDir(null), "visual-acceptance").apply { mkdirs() }
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                File(captureDirectory, "native-download-management.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
            val removalNode = compose.onNodeWithTag(remove).fetchSemanticsNode()
            assertEquals("Remove Mishary Rashid Alafasy recording for Surah 1, Ayah 1", removalNode.config[SemanticsActions.OnClick].label)
            assertTrue("Remove must have a 48dp target", removalNode.boundsInRoot.height >= 48 * context.resources.displayMetrics.density - 1)
            compose.onNodeWithTag(remove).performClick()
            compose.waitUntil(10_000) { !first.exists() }
            val notice = compose.onNodeWithText("Download removed.").assertIsDisplayed().fetchSemanticsNode()
            assertEquals(LiveRegionMode.Polite, notice.config[SemanticsProperties.LiveRegion])
            assertArrayEquals(fixture, second.readBytes())
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.onNodeWithText("1:2").assertIsDisplayed()
            compose.onNode(hasText("Settings") and hasClickAction()).performClick()
            compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Manage downloads"))
            compose.onNodeWithText("Manage downloads").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("remove_download_alafasy_1_2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(remove).assertDoesNotExist()
            assertFalse(first.exists())
            assertArrayEquals(fixture, second.readBytes())
        } finally {
            scenario?.close()
            originals.forEach { (file, bytes) -> if (bytes == null) file.delete() else file.writeBytes(bytes) }
            val editor = preferences.edit().clear()
            originalPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            assertTrue(editor.commit())
        }
    }
}
