package com.quran.mobile.feature.progress.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.QuranTheme
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
import java.util.Locale
import java.util.TimeZone

/**
 * Draws Progress to PNG files (build/outputs/progress-renders) to look at without a device: the
 * Huawei MatePad 12 X (2800×1840 at xhdpi) in landscape and portrait, a medium window and a phone;
 * English and Arabic; day and night. Only with -Pprogress.render.
 */
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ProgressScreenRenderTest {

  private val output = File("build/outputs/progress-renders").apply { mkdirs() }
  private val zone = TimeZone.getTimeZone("UTC")

  @Before
  fun onlyWhenAsked() {
    assumeTrue(System.getProperty("progress.render") != null)
    TimeZone.setDefault(zone)
  }

  @Test
  fun `the bookmarks`() {
    eachLook("bookmarks", ProgressSelection(ProgressSection.BOOKMARKS))
  }

  @Test
  fun `a bookmark`() {
    eachLook("bookmark", ProgressSelection(ProgressSection.BOOKMARKS, bookmark = ReadingBookmarkType.TEAL), wide = false)
  }

  @Test
  fun `a bookmark with no history yet`() {
    eachLook("bookmark_new", ProgressSelection(ProgressSection.BOOKMARKS, bookmark = ReadingBookmarkType.RED))
  }

  @Test
  fun `the memorizing`() {
    eachLook("memorizing", ProgressSelection(ProgressSection.MEMORIZING))
  }

  @Test
  fun `the salah`() {
    eachLook("salah", ProgressSelection(ProgressSection.SALAH))
  }

  @Test
  fun `the sessions`() {
    eachLook("sessions", ProgressSelection(ProgressSection.SESSIONS, session = "s1"))
  }

  // -------------------------------------------------------------------------------- rendering

  /** [selection] in light and dark, English and Arabic, on each device ([wide]: on the wide ones too). */
  private fun eachLook(name: String, selection: ProgressSelection, wide: Boolean = true) {
    val devices = listOfNotNull(
      Triple("phone", "w411dp-h891dp", "xxhdpi"),
      Triple("matepad_landscape", "w1400dp-h920dp", "xhdpi").takeIf { wide },
      Triple("matepad_portrait", "w920dp-h1400dp", "xhdpi").takeIf { wide },
      Triple("medium", "w700dp-h1000dp", "mdpi").takeIf { wide },
    )
    for ((device, size, density) in devices) {
      for (arabic in listOf(false, true)) {
        for (dark in listOf(false, true)) {
          val qualifiers = listOf(if (arabic) "ar" else "en", size, if (dark) "night" else "notnight", density).joinToString("-")
          render("${name}_${device}_${if (arabic) "ar" else "en"}_${if (dark) "dark" else "light"}", qualifiers, dark, arabic) {
            ProgressScreen(SampleStates.state(arabic), selection, ProgressActions(), dark)
          }
        }
      }
    }
  }

  private fun render(file: String, qualifiers: String, dark: Boolean, rtl: Boolean, content: @Composable () -> Unit) {
    RuntimeEnvironment.setQualifiers(qualifiers)
    // as on a device with the app in that language: dates and numbers follow it
    Locale.setDefault(if (rtl) Locale.forLanguageTag("ar-EG") else Locale.US)
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    activity.actionBar?.hide()
    activity.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        QuranTheme(useDarkTheme = dark) {
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
        }
      }
    }
    repeat(3) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
    val view = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    File(output, "$file.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    controller.pause().stop().destroy()
  }
}
