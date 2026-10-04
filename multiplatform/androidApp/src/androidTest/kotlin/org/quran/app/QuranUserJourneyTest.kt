package org.quran.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.quran.app.data.platformAudioPlayer
import org.quran.app.domain.RepeatSession
import org.quran.app.memorization.RepeatPlaybackController
import org.quran.app.model.VerseId
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Exercises the real Activity, Navigation 3 stack, settings adapter and feature screens. */
@RunWith(AndroidJUnit4::class)
class QuranUserJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launchWithFreshProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.getSharedPreferences("quran.study", Context.MODE_PRIVATE).edit().clear().commit())
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun closeActivity() { scenario.close() }

    private fun navigateTo(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun openVerseActions(surah: Int = 1, ayah: Int = 1) {
        val actions = "verse_actions_${surah}_${ayah}"
        compose.onNodeWithTag("reader_list").performScrollToNode(hasTestTag(actions))
        compose.onNodeWithTag(actions).performClick()
    }

    @Test fun libraryReaderPracticeAndBackRetainSavedVerse() {
        compose.onNodeWithText("Open last read").performClick()
        openVerseActions()
        compose.onNodeWithText("Bookmark").performClick()
        openVerseActions()
        compose.onNodeWithText("Remove bookmark").assertIsDisplayed()
        compose.onNodeWithText("Memorize").performClick()
        compose.onNodeWithText("One ayah at a time").assertIsDisplayed()
        compose.onNodeWithText("Hide ayah").performScrollTo().performClick()
        compose.onNodeWithText("Recite from memory, then reveal the ayah to check.").assertIsDisplayed()
        compose.onNodeWithText("Reveal ayah").performClick()
        compose.onNodeWithText("Hide ayah").assertIsDisplayed()
        compose.onNodeWithText("I have memorized this").performScrollTo().performClick()
        compose.onNodeWithText("I have memorized this").assertIsNotEnabled()
        compose.onNodeWithText("‹ Back").performClick()
        openVerseActions()
        compose.onNodeWithText("Remove bookmark").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithText("Continue reading").assertIsDisplayed()
        compose.onNodeWithText("Bookmarks").performClick()
        compose.onNodeWithText("Read").performClick()
        openVerseActions()
        compose.onNodeWithText("Remove bookmark").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        navigateTo("Settings")
        compose.onNodeWithText("1 ayat marked memorized").performScrollTo().assertIsDisplayed()
    }

    @Test fun arabicDirectionAndLocaleSurviveActivityRecreation() {
        navigateTo("Settings")
        val englishSettings = compose.onNode(hasText("Settings") and hasClickAction()).fetchSemanticsNode().boundsInRoot.center.x
        val englishQibla = compose.onNodeWithText("Qibla").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("English navigation should read left to right", englishSettings > englishQibla)
        compose.onNodeWithText("Arabic").performClick()
        compose.onNodeWithText("تفضيلات القراءة والآيات المحفوظة").assertIsDisplayed()
        val arabicSettings = compose.onNode(hasText("الإعدادات") and hasClickAction()).fetchSemanticsNode().boundsInRoot.center.x
        val arabicQibla = compose.onNodeWithText("القبلة").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Arabic navigation should mirror right to left", arabicSettings < arabicQibla)
        scenario.recreate()
        navigateTo("الإعدادات")
        compose.onNodeWithText("تفضيلات القراءة والآيات المحفوظة").assertIsDisplayed()
        compose.onNodeWithText("الإنجليزية").performClick()
        compose.onNodeWithText("Reading preferences and saved verses").assertIsDisplayed()
    }

    @Test fun childrenModePersistsAndStartsGuidedPracticeWithThreeRepetitions() {
        navigateTo("Settings")
        compose.onNode(isToggleable()).performClick().assertIsOn()
        scenario.recreate()
        navigateTo("Settings")
        compose.onNode(isToggleable()).assertIsOn()
        navigateTo("Library")
        compose.onNodeWithText("Open last read").performClick()
        openVerseActions()
        compose.onNodeWithText("Memorize").performClick()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("I repeated this verse").performScrollTo().performClick()
        compose.onNodeWithText("Repetitions: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I have memorized this").performScrollTo().assertIsEnabled()
    }

    @Test fun lastReadAyahResumesAtItsTopAfterRelaunch() {
        compose.onNodeWithText("Search surah").performScrollTo().performTextInput("2")
        compose.onNodeWithText("Read").performScrollTo().performClick()
        openVerseActions(surah = 2, ayah = 5)
        compose.onNodeWithText("Mark as read").performClick()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithText("2:5").assertIsDisplayed()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("2:5").assertIsDisplayed()
        compose.onNodeWithText("Open last read").performClick()
        // Do not scroll before asserting: the persisted ayah must be the initial visible item.
        compose.onNodeWithTag("reader_verse_2_5").assertIsDisplayed()
        compose.onNodeWithTag("reader_verse_2_4").assertIsNotDisplayed()
        val readerTop = compose.onNodeWithTag("reader_list").fetchSemanticsNode().boundsInRoot.top
        val ayahTop = compose.onNodeWithTag("reader_verse_2_5").fetchSemanticsNode().boundsInRoot.top
        assertEquals("Resume must start at ayah 5, accounting for the chapter header", readerTop, ayahTop, 1f)
    }

    @Test fun verseActionsRemainReachableInLandscape() {
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitUntil(10_000) {
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.onNodeWithText("Open last read").performScrollTo().performClick()
        openVerseActions()
        compose.onNodeWithText("Study").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.onNodeWithTag("reader_list").assertIsDisplayed()
    }

    @Test fun juzSelectionOpensItsCanonicalMidSurahStart() {
        compose.onNodeWithText("Juz").performClick()
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("juz_open_16"))
        compose.onNodeWithTag("juz_open_16").performClick()
        // Observe initial position without a scroll: Juz 16 starts inside Al-Kahf, not surah 1.
        compose.onNodeWithTag("reader_verse_18_75").assertIsDisplayed()
        compose.onNodeWithTag("reader_verse_18_74").assertIsNotDisplayed()
        val readerTop = compose.onNodeWithTag("reader_list").fetchSemanticsNode().boundsInRoot.top
        val ayahTop = compose.onNodeWithTag("reader_verse_18_75").fetchSemanticsNode().boundsInRoot.top
        assertEquals(readerTop, ayahTop, 1f)
        navigateTo("Library")
        compose.onNodeWithTag("library_list").performScrollToIndex(0)
        compose.onNodeWithText("Juz").performClick()
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("juz_open_30"))
        compose.onNodeWithTag("juz_open_30").performClick()
        compose.onNodeWithTag("reader_verse_78_1").assertIsDisplayed()
    }

    @Test fun practiceRangeMarksEachAyahAndRetainsProgressAfterRelaunch() {
        compose.onNodeWithText("Open last read").performClick()
        openVerseActions()
        compose.onNodeWithText("Memorize").performClick()
        compose.onNodeWithContentDescription("Add the next ayah to the range").performScrollTo().performClick()
        compose.onNodeWithTag("practice_verse_1_1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I have memorized this").performScrollTo().performClick()
        compose.onNodeWithTag("practice_verse_1_2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I have memorized this").performScrollTo().performClick()
        compose.onNodeWithText("Session complete. Memorization is self-assessed.").performScrollTo().assertIsDisplayed()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        navigateTo("Settings")
        compose.onNodeWithText("2 ayat marked memorized").performScrollTo().assertIsDisplayed()
    }

    @Test fun readingSizesPersistAndEnlargeCanonicalReaderText() {
        compose.onNodeWithText("Open last read").performClick()
        val originalHeight = compose.onNodeWithTag("reader_verse_1_1").fetchSemanticsNode().boundsInRoot.height
        navigateTo("Settings")
        compose.onNodeWithTag("reading_arabic_large").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("reading_translation_large").performScrollTo().performClick().assertIsSelected()
        scenario.recreate()
        navigateTo("Settings")
        compose.onNodeWithTag("reading_arabic_large").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("reading_translation_large").performScrollTo().assertIsSelected()
        navigateTo("Library")
        compose.onNodeWithText("Open last read").performClick()
        val enlargedHeight = compose.onNodeWithTag("reader_verse_1_1").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Larger Arabic preference must affect the native reader", enlargedHeight > originalHeight)
    }

    @Test fun nativeMediaServiceCompletesExactlyTwoRealPlaybacks() {
        // Synthetic silent PCM is a player test fixture; it is never Quran recitation.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "qa-synthetic-player-fixture.wav")
        val pcmSize = 8_000 // 0.5 seconds of mono 16-bit PCM at 8 kHz.
        val wav = ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + pcmSize).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(pcmSize).put(ByteArray(pcmSize))
        file.writeBytes(wav.array())
        val completed = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        val observedRepetitions = AtomicReference(0)
        var controller: RepeatPlaybackController? = null
        val player = compose.runOnIdle { platformAudioPlayer() }
        try {
            compose.runOnIdle {
                player.loadLocal("file://${file.absolutePath}")
                controller = RepeatPlaybackController(
                    RepeatSession(listOf(VerseId(1, 1)), 2), player,
                    onChanged = { state, playing ->
                        observedRepetitions.set(state.completedRepetitions)
                        if (state.complete && !playing) completed.countDown()
                    },
                    onError = { error.set("Native player reported a playback error"); completed.countDown() },
                )
                controller?.play()
            }
            assertTrue("Actual playback must complete within 20 seconds", completed.await(20, TimeUnit.SECONDS))
            assertNull("Native media error: ${error.get()}", error.get())
            assertEquals(2, observedRepetitions.get())
        } finally {
            compose.runOnIdle { controller?.dispose(); player.release() }
            file.delete()
        }
    }
}
