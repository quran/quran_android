package org.quran.app

import android.content.Context
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

    @Test fun libraryReaderPracticeAndBackRetainSavedVerse() {
        compose.onNodeWithText("Open verse").performClick()
        compose.onAllNodesWithText("Save")[0].performClick()
        compose.onAllNodesWithText("Saved")[0].assertIsDisplayed()
        compose.onAllNodesWithText("Practice")[0].performClick()
        compose.onNodeWithText("One verse at a time").assertIsDisplayed()
        compose.onNodeWithText("Hide verse").performClick()
        compose.onNodeWithText("Recite from memory, then reveal to check.").assertIsDisplayed()
        compose.onNodeWithText("Reveal verse").performClick()
        compose.onNodeWithText("Hide verse").assertIsDisplayed()
        compose.onNodeWithText("I have memorized it").performScrollTo().performClick()
        compose.onNodeWithText("I have memorized it").assertIsNotEnabled()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onAllNodesWithText("Saved")[0].assertIsDisplayed()
        compose.onNodeWithText("‹ Back").performClick()
        compose.onNodeWithText("Your daily companion").assertIsDisplayed()
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithText("1 verses marked memorized").assertIsDisplayed()
        compose.onNodeWithText("Saved verses").performScrollTo().assertIsDisplayed()
    }

    @Test fun arabicDirectionAndLocaleSurviveActivityRecreation() {
        compose.onNodeWithText("You").performClick()
        val englishYou = compose.onNodeWithText("You").fetchSemanticsNode().boundsInRoot.center.x
        val englishQibla = compose.onNodeWithText("Qibla").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("English navigation should read left to right", englishYou > englishQibla)
        compose.onNodeWithText("العربية").performClick()
        compose.onNodeWithText("مساحتك للقراءة").assertIsDisplayed()
        val arabicYou = compose.onNodeWithText("حسابك").fetchSemanticsNode().boundsInRoot.center.x
        val arabicQibla = compose.onNodeWithText("القبلة").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Arabic navigation should mirror right to left", arabicYou < arabicQibla)
        scenario.recreate()
        compose.onNodeWithText("حسابك").assertIsDisplayed()
        compose.onNodeWithText("حسابك").performClick()
        compose.onNodeWithText("مساحتك للقراءة").assertIsDisplayed()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Your reading space").assertIsDisplayed()
    }

    @Test fun childrenModePersistsAndStartsGuidedPracticeWithThreeRepetitions() {
        compose.onNodeWithText("You").performClick()
        compose.onNode(isToggleable()).performClick().assertIsOn()
        scenario.recreate()
        compose.onNodeWithText("You").performClick()
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithText("Read").performClick()
        compose.onNodeWithText("Open verse").performClick()
        compose.onAllNodesWithText("Practice")[0].performClick()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("I repeated this verse").performScrollTo().performClick()
        compose.onNodeWithText("Repetitions: 1").assertIsDisplayed()
        compose.onNodeWithText("I have memorized it").assertIsEnabled()
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
