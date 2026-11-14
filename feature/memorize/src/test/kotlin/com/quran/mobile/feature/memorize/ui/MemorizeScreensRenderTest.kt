package com.quran.mobile.feature.memorize.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.recording.Recording
import com.quran.mobile.feature.memorize.recording.RecordingPlayer
import com.quran.mobile.feature.memorize.recording.SessionCounts
import com.quran.mobile.feature.memorize.recording.SessionListScreen
import com.quran.mobile.feature.memorize.recording.SessionRow
import com.quran.mobile.feature.memorize.recording.SessionScreen
import com.quran.mobile.feature.memorize.recording.SessionsSideBySide
import com.quran.mobile.feature.memorize.tracker.WordId
import com.quran.recitation.pages.PagePreview
import com.quran.recitation.pages.PageRenderer
import com.quran.recitation.presenter.RecitationHighlightsPresenter.HighlightAction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.memorize.session.MemorizeState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Draws the feature's screens to PNG files (build/outputs/memorize-renders) so they can be looked at
 * without a phone: light and dark, English and Arabic, phone and tablet. Only with -Pmemorize.render.
 */
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class MemorizeScreensRenderTest {

  private val output = File("build/outputs/memorize-renders").apply { mkdirs() }

  @Before
  fun onlyWhenAsked() {
    assumeTrue(System.getProperty("memorize.render") != null)
  }

  @Test
  fun `the mode window`() {
    val all = listOf(MemorizeState.Mode.MEMORIZE, MemorizeState.Mode.RECITE)
    val withSalah = all + MemorizeState.Mode.SALAH
    eachLook("mode_window") { arabic ->
      ModeSheetOnScrim(ModeSheetState(if (arabic) "الملك، الآية ١" else "Al-Mulk, ayah 1", all, ModeSheetState.RecordLine.Off))
    }
    eachLook("mode_window_contribute") { arabic ->
      ModeSheetOnScrim(
        ModeSheetState(if (arabic) "الملك، الآية ١" else "Al-Mulk, ayah 1", withSalah, ModeSheetState.RecordLine.Contributing)
      )
    }
    eachLook("mode_window_ask", tablet = false) { arabic ->
      ModeSheetOnScrim(ModeSheetState(if (arabic) "الملك، الآية ١" else "Al-Mulk, ayah 1", all, ModeSheetState.RecordLine.Ask))
    }
  }

  @Test
  fun `the recording sessions`() {
    eachLook("sessions") { arabic ->
      SessionListScreen(
        rows = sessionRows(arabic),
        summary = if (arabic) "٤ جلسات · ٨٤ م.ب · «سجّل وساهم» مفعّل" else "4 sessions · 84 MB · Record and contribute is on",
        snackbar = remember { SnackbarHostState() },
        onOpen = {}, onDelete = {}, onExportAll = {}, onDeleteAll = {}, onBack = {},
      )
    }
    eachLook("sessions_empty", tablet = false) {
      SessionListScreen(
        rows = emptyList(), summary = null, snackbar = remember { SnackbarHostState() },
        onOpen = {}, onDelete = {}, onExportAll = {}, onDeleteAll = {}, onBack = {},
      )
    }
  }

  @Test
  fun `a session played back on its pages`() {
    eachLook("session_window") { arabic -> Session(arabic) }
  }

  @Test
  fun `the recording sessions side by side on a tablet`() {
    eachLook("sessions_side_by_side", phone = false) { arabic ->
      SessionsSideBySide(
        width = LocalConfiguration.current.screenWidthDp.dp,
        list = {
          SessionListScreen(
            rows = sessionRows(arabic),
            summary = if (arabic) "٤ جلسات · ٨٤ م.ب · «سجّل وساهم» مفعّل" else "4 sessions · 84 MB · Record and contribute is on",
            snackbar = remember { SnackbarHostState() },
            onOpen = {}, onDelete = {}, onExportAll = {}, onDeleteAll = {}, onBack = {},
            selectedId = sessionRows(arabic).first().id,
          )
        },
        session = { Session(arabic) },
      )
    }
  }

  /** Al-Mulk 1–15 at 1:04, with two words given, one skipped and one marked. */
  @Composable
  private fun Session(arabic: Boolean) {
    val recording = Recording(
      id = "s", session = "s", mode = MemorizeState.Mode.MEMORIZE, startedAt = 0,
      start = SuraAyah(67, 1), end = SuraAyah(67, 15),
      parts = listOf(Recording.Part(File("a"), 252_000)),
      words = (1..120).map {
        val status = when (it) {
          23, 71 -> Recording.WordStatus.HINTED
          96 -> Recording.WordStatus.MISSED
          else -> Recording.WordStatus.RECITED
        }
        Recording.Word(WordId(67, 1 + it / 8, 1 + it % 8), it * 2_000L, it * 2_000L + 600, status, "", mistakes = if (it == 48) 1 else 0)
      },
    )
    SessionScreen(
      title = if (arabic) "الملك ١–١٥" else "Al-Mulk 1–15",
      subtitle = if (arabic) "الحفظ · ١٢ نوفمبر ٢٠٢٦، ٩:٤٠ م" else "Memorization · 12 Nov 2026, 21:40",
      recording = recording,
      counts = SessionCounts(recited = 52, hinted = 2, missed = 1, mistakes = 1),
      pages = listOf(562, 563),
      pageOf = { if (it.id.ayah < 13) 562 else 563 },
      playback = RecordingPlayer.Playback(recording, 64_000, isPlaying = true),
      renderer = FakePages(LocalContext.current),
      onBack = {}, onExport = {}, onDelete = {}, onPlay = {}, onPause = {}, onSeek = {}, onSkip = {},
    )
  }

  private fun sessionRows(arabic: Boolean): List<SessionRow> {
    fun row(id: String, mode: MemorizeState.Mode, en: String, ar: String, detailsEn: String, detailsAr: String, day: Int, contributed: Boolean) =
      SessionRow(
        id, mode, if (arabic) ar else en, if (arabic) detailsAr else detailsEn,
        listOf(if (arabic) "اليوم" else "Today", if (arabic) "أمس" else "Yesterday", if (arabic) "الاثنين، ١٠ نوفمبر" else "Monday, November 10")[day],
        contributed,
      )
    return listOf(
      row("1", MemorizeState.Mode.MEMORIZE, "Al-Mulk 1–15", "الملك ١–١٥", "Memorization · 21:40 · 4:12", "الحفظ · ٩:٤٠ م · ٤:١٢", 0, true),
      row("2", MemorizeState.Mode.RECITE, "Al-Kahf 1–10", "الكهف ١–١٠", "Recitation · 08:05 · 6:30", "التلاوة · ٨:٠٥ ص · ٦:٣٠", 0, true),
      row("3", MemorizeState.Mode.SALAH, "Al-Ala 1 – Al-Ghashiyah 26", "الأعلى ١ – الغاشية ٢٦", "In prayer (salah) · 20:10 · 9:48", "في الصلاة · ٨:١٠ م · ٩:٤٨", 1, false),
      row("4", MemorizeState.Mode.MEMORIZE, "An-Naba 1–40", "النبأ ١–٤٠", "Memorization · 06:30 · 11:02", "الحفظ · ٦:٣٠ ص · ١١:٠٢", 2, false),
    )
  }

  /** Stands in for the app's pages: a page with lines of text. */
  private class FakePages(private val context: Context) : PageRenderer {
    override val isNightMode: Boolean = false

    override fun page(page: Int, onTapped: (SuraAyah, Int?) -> Unit): PagePreview = object : PagePreview {
      override val view: View = object : View(context) {
        private val paint = Paint().apply { color = 0xFFD8CFBE.toInt() }

        override fun onDraw(canvas: Canvas) {
          canvas.drawColor(0xFFFFFBF2.toInt())
          val lineHeight = height / 17f
          for (line in 1..15) {
            val top = line * lineHeight + lineHeight * 0.3f
            canvas.drawRect(width * 0.08f, top, width * 0.92f, top + lineHeight * 0.4f, paint)
          }
        }
      }

      override fun apply(actions: List<HighlightAction>) {}

      override fun release() {}
    }

    override fun close() {}
  }

  @Composable
  private fun ModeSheetOnScrim(state: ModeSheetState) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.BottomCenter) {
      // the sheet is at most 840dp wide (on a tablet)
      ModeSheetContent(state, Modifier.widthIn(max = 840.dp), onChoose = { _, _ -> })
    }
  }

  /** Renders [content] light and dark, in English and Arabic, on a phone (and a tablet). */
  private fun eachLook(name: String, tablet: Boolean = true, phone: Boolean = true, content: @Composable (arabic: Boolean) -> Unit) {
    // size, density
    val devices = listOfNotNull(
      Triple("phone", "w411dp-h891dp", "xxhdpi").takeIf { phone },
      Triple("tablet", "w840dp-h1280dp", "mdpi").takeIf { tablet },
      // the Huawei MatePad 12 X, 2800×1840 at xhdpi
      Triple("matepad_landscape", "w1400dp-h920dp", "xhdpi").takeIf { tablet },
      Triple("matepad_portrait", "w920dp-h1400dp", "xhdpi").takeIf { tablet },
    )
    for ((device, size, density) in devices) {
      for (arabic in listOf(false, true)) {
        for (dark in listOf(false, true)) {
          // in Android's order: locale, size, night mode, density
          val qualifiers = listOf(if (arabic) "ar" else "en", size, if (dark) "night" else "notnight", density).joinToString("-")
          render("${name}_${device}_${if (arabic) "ar" else "en"}_${if (dark) "dark" else "light"}", qualifiers, dark, arabic) {
            content(arabic)
          }
        }
      }
    }
  }

  private fun render(file: String, qualifiers: String, dark: Boolean, rtl: Boolean, content: @Composable () -> Unit) {
    RuntimeEnvironment.setQualifiers(qualifiers)
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    activity.actionBar?.hide()
    activity.setContent {
      // the app declares right-to-left support; this test's manifest doesn't
      CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        QuranTheme(useDarkTheme = dark) {
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
        }
      }
    }
    // a few frames: lazy layouts (the pager) place their content after the first
    repeat(3) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
    val view = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    File(output, "$file.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    controller.pause().stop().destroy()
  }
}
