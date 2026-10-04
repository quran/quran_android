package org.quran.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.quran.app.data.BundledQuranRepository

/** Real Activity screenshots plus layout checks, not snapshot fixtures or preview images. */
@RunWith(AndroidJUnit4::class)
class QuranVisualAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var modeManager: UiModeManager
    private var originalNightMode = UiModeManager.MODE_NIGHT_AUTO
    private var originalFontScale = 1f
    private var fontScaleChanged = false
    private var capturePrefix = ""
    private val quran = BundledQuranRepository()

    @Before fun rememberDeviceConfiguration() {
        Assume.assumeTrue("Application night-mode control requires Android 12+", Build.VERSION.SDK_INT >= 31)
        modeManager = checkNotNull(context.getSystemService(UiModeManager::class.java))
        // The app has no theme override preference; its baseline follows the system mode.
        originalNightMode = modeManager.nightMode
        originalFontScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        assertTrue(context.getSharedPreferences("quran.study", Context.MODE_PRIVATE).edit().clear().commit())
    }

    @After fun restoreConfigurationAndClose() {
        try {
            if (::scenario.isInitialized) scenario.close()
        } finally {
            try {
                if (fontScaleChanged) changeFontScale(originalFontScale)
            } finally {
                if (::modeManager.isInitialized && Build.VERSION.SDK_INT >= 31) modeManager.setApplicationNightMode(originalNightMode)
                assertTrue(context.getSharedPreferences("quran.study", Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
    }

    @Test fun englishAndArabicScreensRemainReadableAcrossBothThemes() {
        verifyScreens(dark = false)
        scenario.close()
        assertTrue(context.getSharedPreferences("quran.study", Context.MODE_PRIVATE).edit().clear().commit())
        verifyScreens(dark = true)
    }

    @Test fun largeSystemFontKeepsBothLanguagesControlsAndReaderTextReachable() {
        Assume.assumeTrue("Global font-scale changes are restricted to emulator tests", isEmulator())
        fontScaleChanged = true
        changeFontScale(1.5f)
        verifyScreens(dark = false, largeFont = true)
    }

    private fun verifyScreens(dark: Boolean, largeFont: Boolean = false) {
        modeManager.setApplicationNightMode(if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) {
            var ready = false
            scenario.onActivity {
                val config = it.resources.configuration
                ready = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO) &&
                    (!largeFont || kotlin.math.abs(config.fontScale - 1.5f) < 0.01f)
            }
            ready
        }
        listOf(Labels.English, Labels.Arabic).forEach { labels ->
            capturePrefix = "${if (largeFont) "font-150" else if (dark) "dark" else "light"}-${labels.id}"
            if (labels == Labels.Arabic) {
                navigate(Labels.English.settings)
                compose.onNodeWithText("Arabic").performScrollTo().performClick()
                assertReadableText(labels.settingsSubtitle)
                navigate(labels.library)
            }
            try {
                verifyLibrary(labels)
                capture("library", dark)
                compose.onNodeWithText(labels.openLastRead).performScrollTo().performClick()
                verifyReader(labels)
                capture("reader", dark)
                compose.onNodeWithTag("verse_actions_1_1").performScrollTo().performClick()
                compose.onNodeWithText(labels.practice).performClick()
                verifyPractice(labels)
                capture("practice", dark)
                navigate(labels.settings)
                assertReadableText(labels.settingsSubtitle)
                assertNavigation(labels)
                compose.onNodeWithTag("reading_arabic_large").performScrollTo().performClick().assertIsSelected()
                assertReadableText(labels.translationSize, scroll = true)
                capture("settings", dark)
                navigate(labels.library)
                compose.onNodeWithTag("library_list").performScrollToIndex(0)
            } catch (failure: Throwable) {
                // Capture while the failed screen is still open, before Activity teardown.
                runCatching { capture("failure", dark, verifyPalette = false) }
                throw failure
            }
        }
    }

    private fun verifyLibrary(labels: Labels) {
        assertReadableText(labels.continueReading)
        assertReadableText(labels.openLastRead)
        assertTouchTarget(compose.onNodeWithText(labels.openLastRead))
        assertNavigation(labels)
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("surah_open_1"))
        val chapter = quran.chapters().first()
        assertReadableText(chapter.englishName)
        assertReadableText(chapter.arabicName)
        assertTouchTarget(compose.onNodeWithTag("surah_open_1"))
        val read = compose.onNode(hasText(labels.read) and hasClickAction() and hasAnyAncestor(hasTestTag("surah_open_1")))
        read.assertIsDisplayed()
        assertTouchTarget(read)
    }

    private fun verifyReader(labels: Labels) {
        compose.onNodeWithTag("reader_verse_1_1").assertIsDisplayed()
        assertReadableText(quran.verses(1).first().arabic, scroll = true)
        val actions = compose.onNodeWithTag("verse_actions_1_1")
        actions.performScrollTo().assertIsDisplayed()
        assertTouchTarget(actions)
        assertNoTextOverflow(compose.onNode(hasText(labels.verseActions) and hasAnyAncestor(hasTestTag("verse_actions_1_1")), useUnmergedTree = true))
        assertNavigation(labels)
    }

    private fun verifyPractice(labels: Labels) {
        compose.onNodeWithTag("practice_verse_1_1").performScrollTo().assertIsDisplayed()
        assertReadableText(quran.verses(1).first().arabic, scroll = true)
        val hide = compose.onNodeWithText(labels.hide)
        hide.performScrollTo().assertIsDisplayed()
        assertTouchTarget(hide)
        assertReadableText(labels.hide)
        hide.performClick()
        assertReadableText(labels.reveal)
        compose.onNodeWithText(labels.reveal).performClick()
        assertNavigation(labels)
    }

    private fun navigate(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun assertNavigation(labels: Labels) {
        val library = compose.onNode(hasText(labels.library) and hasClickAction())
        val qibla = compose.onNode(hasText(labels.qibla) and hasClickAction())
        val settings = compose.onNode(hasText(labels.settings) and hasClickAction())
        listOf(library, qibla, settings).forEach { node -> node.assertIsDisplayed(); assertTouchTarget(node) }
        listOf(labels.library, labels.qibla, labels.settings).forEach { label ->
            // Settings has a screen heading too; inspect the navigation item's text specifically.
            val text = compose.onNode(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
            assertNoTextOverflow(text)
        }
        val libraryX = library.fetchSemanticsNode().boundsInRoot.center.x
        val settingsX = settings.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Navigation must mirror the interface language", if (labels == Labels.Arabic) settingsX < libraryX else libraryX < settingsX)
    }

    private fun assertReadableText(text: String, scroll: Boolean = false) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
        assertNoTextOverflow(node)
    }

    private fun assertNoTextOverflow(node: SemanticsNodeInteraction) {
        assertFullyVisible(node)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
        assertTrue("A visible text control must expose its text layout", layouts.isNotEmpty())
        layouts.forEach { original ->
            val input = original.layoutInput
            val result = TextMeasurer(
                defaultFontFamilyResolver = input.fontFamilyResolver,
                defaultDensity = input.density,
                defaultLayoutDirection = input.layoutDirection,
                cacheSize = 0,
            ).measure(
                text = input.text,
                style = input.style,
                overflow = input.overflow,
                softWrap = input.softWrap,
                maxLines = input.maxLines,
                placeholders = input.placeholders,
                constraints = Constraints(maxWidth = original.size.width, maxHeight = original.size.height),
                layoutDirection = input.layoutDirection,
                density = input.density,
                fontFamilyResolver = input.fontFamilyResolver,
                skipCache = true,
            )
            val diagnostic = "original=${layoutDiagnostic(original)}; measured=${layoutDiagnostic(result)}"
            // The semantics callback can rebuild a paragraph with stale wider constraints.
            // The public measurer checks identical text/fonts against the actual native box.
            val overflowsRenderedBounds = (0 until result.lineCount).any { line ->
                result.getLineLeft(line) < -1f || result.getLineRight(line) > result.size.width + 1f ||
                    result.getLineRight(line) - result.getLineLeft(line) > result.size.width + 1f ||
                    result.getLineBottom(line) > result.size.height + 1f || result.getLineTop(line) < -1f ||
                    result.isLineEllipsized(line)
            } || result.multiParagraph.didExceedMaxLines
            if (overflowsRenderedBounds) {
                android.util.Log.e("QuranVisualLayout", diagnostic)
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("visual_layout_diagnostic", diagnostic) })
            }
            assertFalse("Rendered text must fit without hidden or ellipsized lines: $diagnostic", overflowsRenderedBounds)
            val requiredEnd = result.layoutInput.text.text.trimEnd().length
            val visibleEnd = if (result.lineCount > 0) result.getLineEnd(result.lineCount - 1, visibleEnd = true) else 0
            assertTrue("Non-whitespace text must remain visible: $diagnostic", visibleEnd >= requiredEnd)
        }
    }

    private fun layoutDiagnostic(result: TextLayoutResult): String = buildString {
        append("text=${result.layoutInput.text.text}; size=${result.size}; paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}; ")
        append("overflowWidth=${result.didOverflowWidth}; overflowHeight=${result.didOverflowHeight}; constraints=${result.layoutInput.constraints}; ")
        append("fontSize=${result.layoutInput.style.fontSize}; lineHeight=${result.layoutInput.style.lineHeight}; density=${result.layoutInput.density.density}; fontScale=${result.layoutInput.density.fontScale}; ")
        append("maxLines=${result.layoutInput.maxLines}; softWrap=${result.layoutInput.softWrap}; overflow=${result.layoutInput.overflow}; ")
        (0 until result.lineCount).forEach { line ->
            append("line$line=[left=${result.getLineLeft(line)},right=${result.getLineRight(line)},top=${result.getLineTop(line)},bottom=${result.getLineBottom(line)},baseline=${result.getLineBaseline(line)},end=${result.getLineEnd(line, visibleEnd = true)},ellipsized=${result.isLineEllipsized(line)}]; ")
        }
    }

    private fun assertFullyVisible(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val unclipped = node.getUnclippedBoundsInRoot()
        val density = context.resources.displayMetrics.density
        assertEquals("Visible content must not be clipped horizontally", (unclipped.right - unclipped.left).value * density, bounds.width, 1f)
        assertEquals("Visible content must not be clipped vertically", (unclipped.bottom - unclipped.top).value * density, bounds.height, 1f)
    }

    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        assertFullyVisible(node)
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val minimum = 48f * context.resources.displayMetrics.density
        assertTrue("Action width must be at least 48dp", bounds.width + 1f >= minimum)
        assertTrue("Action height must be at least 48dp", bounds.height + 1f >= minimum)
    }

    private fun capture(screen: String, dark: Boolean, verifyPalette: Boolean = true) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Native screenshot unavailable" }
        try {
            val directory = File(checkNotNull(context.getExternalFilesDir(null)), "visual-acceptance")
            check(directory.isDirectory || directory.mkdirs())
            val file = File(directory, "$capturePrefix-$screen.png")
            file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("visual_artifact", file.absolutePath) })
            if (verifyPalette) {
                val expected = if (dark) Color.rgb(14, 25, 20) else Color.rgb(246, 242, 233)
                var matches = 0
                for (y in 0 until bitmap.height step 12) for (x in 0 until bitmap.width step 12) {
                    if ((bitmap.getPixel(x, y) and 0x00ffffff) == (expected and 0x00ffffff)) matches++
                }
                assertTrue("Native screenshot must contain the selected reading theme background", matches > 100)
            }
        } finally { bitmap.recycle() }
    }

    private fun changeFontScale(scale: Float) {
        // Android instrumentation framework controls configuration; no host UI shell automation.
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $scale").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun isEmulator() = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator") ||
        Build.MODEL.contains("sdk", ignoreCase = true) || Build.HARDWARE in setOf("goldfish", "ranchu")

    private data class Labels(
        val id: String, val library: String, val qibla: String, val settings: String,
        val continueReading: String, val openLastRead: String, val read: String,
        val verseActions: String, val practice: String, val hide: String, val reveal: String,
        val settingsSubtitle: String, val translationSize: String,
    ) {
        companion object {
            val English = Labels("en", "Library", "Qibla", "Settings", "Continue reading", "Open last read", "Read",
                "Verse actions", "Memorize", "Hide ayah", "Reveal ayah", "Reading preferences and saved verses", "Translation text size")
            val Arabic = Labels("ar", "المكتبة", "القبلة", "الإعدادات", "متابعة القراءة", "فتح آخر موضع", "قراءة",
                "إجراءات الآية", "حفظ", "إخفاء الآية", "إظهار الآية", "تفضيلات القراءة والآيات المحفوظة", "حجم نص الترجمة")
        }
    }
}
