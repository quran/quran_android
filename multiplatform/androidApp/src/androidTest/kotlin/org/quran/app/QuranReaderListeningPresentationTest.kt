package org.quran.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.quran.app.data.BundledQuranRepository
import org.quran.app.designsystem.QuranTheme
import org.quran.app.model.AppLanguage
import org.quran.app.model.StudyProgress
import org.quran.app.model.VerseId
import org.quran.app.reader.ReaderListeningState
import org.quran.app.reader.ReaderScreen
import java.util.concurrent.atomic.AtomicReference

/** Exercises native reader presentation; playback/download behavior is tested separately. */
@RunWith(AndroidJUnit4::class)
class QuranReaderListeningPresentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun verseListenActionTargetsExactAyahAndOffersPauseAndStop() {
        val selected = AtomicReference<VerseId?>(null)
        val state = mutableStateOf(ReaderListeningState())
        val quran = BundledQuranRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    QuranTheme {
                        CompositionLocalProvider(AppLocale provides AppLanguage.ENGLISH) {
                            ReaderScreen(
                                chapter = quran.chapters().first(), verses = quran.verses(1), initialAyah = 2,
                                progress = StudyProgress(), translationSummary = {}, translationForVerse = {},
                                onRead = {}, onBookmark = {}, onPractice = {}, onStudy = {},
                                listeningState = state.value, reciterName = "Mishary Rashid Alafasy",
                                onListen = { id -> selected.set(id); state.value = ReaderListeningState(verseId = id, isPlaying = true, isReady = true) },
                                onTogglePlayback = { state.value = state.value.copy(isPlaying = !state.value.isPlaying) },
                                onStop = { state.value = ReaderListeningState() },
                            )
                        }
                    }
                }
            }
            compose.onNodeWithTag("reader_list").performScrollToNode(hasTestTag("verse_actions_1_2"))
            compose.onNodeWithTag("verse_actions_1_2").performClick()
            compose.onNodeWithText("Listen").performClick()
            assertEquals(VerseId(1, 2), selected.get())
            compose.onNodeWithText("Pause").performScrollTo().performClick()
            compose.onNodeWithText("Play").assertIsDisplayed()
            compose.onNodeWithText("Stop").performClick()
            compose.onNodeWithText("Pause").assertDoesNotExist()
            compose.onNodeWithText("Stop").assertDoesNotExist()
            compose.onNodeWithTag("reader_list").assertIsDisplayed()
        } finally { scenario.close() }
    }

    @Test fun largeFontControlsRemainReachableInAConstrainedReaderViewport() {
        val state = mutableStateOf(ReaderListeningState(VerseId(1, 1), isReady = true, isPlaying = true))
        val quran = BundledQuranRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current
                    QuranTheme {
                        CompositionLocalProvider(AppLocale provides AppLanguage.ENGLISH, LocalDensity provides Density(density.density, 1.5f)) {
                            Box(Modifier.height(240.dp).fillMaxWidth()) {
                                ReaderScreen(
                                    chapter = quran.chapters().first(), verses = quran.verses(1), initialAyah = 1,
                                    progress = StudyProgress(), translationSummary = {}, translationForVerse = {},
                                    onRead = {}, onBookmark = {}, onPractice = {}, onStudy = {},
                                    listeningState = state.value, reciterName = "Mishary Rashid Alafasy",
                                    onStop = { state.value = ReaderListeningState() },
                                )
                            }
                        }
                    }
                }
            }
            val density = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            assertTrue("At least half the constrained viewport must remain available for reading", compose.onNodeWithTag("reader_list").fetchSemanticsNode().boundsInRoot.height >= 120 * density - 1)
            compose.onNodeWithText("Stop").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Stop").assertDoesNotExist()
            compose.onNodeWithTag("reader_list").assertIsDisplayed()
        } finally { scenario.close() }
    }

}
