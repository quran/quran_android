package com.quran.mobile.feature.progress.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Draws Progress on the device it runs on (e.g. Test Lab's virtual tablet and phone) and keeps a
 * screenshot of each screen in English and Arabic, by day and night, in /sdcard/progress-shots (Test
 * Lab pulls that folder). The data is [SampleStates]'.
 */
@RunWith(AndroidJUnit4::class)
class ProgressScreenshotsTest {

  @Test
  fun progressScreens() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val own = File(instrumentation.targetContext.getExternalFilesDir(null), "progress-shots").apply { mkdirs() }
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val device = with(instrumentation.targetContext.resources.configuration) { "w${screenWidthDp}dp-h${screenHeightDp}dp" }
    var taken = 0
    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      for (arabic in listOf(false, true)) {
        val locale = if (arabic) Locale.forLanguageTag("ar-EG") else Locale.US
        Locale.setDefault(locale)
        val state = SampleStates.state(arabic)
        for (dark in listOf(false, true)) {
          for ((name, selection) in SampleStates.screens) {
            scenario.onActivity { activity ->
              val config = Configuration(activity.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                  if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
              }
              val context = activity.createConfigurationContext(config)
              activity.setContent {
                CompositionLocalProvider(
                  LocalContext provides context,
                  LocalConfiguration provides config,
                  LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                  QuranTheme(useDarkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                      ProgressScreen(state, selection, ProgressActions(), dark)
                    }
                  }
                }
              }
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(SETTLE_MS)
            val shot: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: continue
            val file = File(own, "${name}_${if (arabic) "ar" else "en"}_${if (dark) "dark" else "light"}.png")
            file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            taken++
          }
        }
      }
    }
    // where Test Lab (and adb, as the shell) can pull them from
    instrumentation.uiAutomation.executeShellCommand("mkdir -p $SHARED").close()
    instrumentation.uiAutomation.executeShellCommand("cp -r ${own.absolutePath}/. $SHARED/").close()
    Log.i(TAG, "took $taken screenshots on $device into $SHARED")
  }

  private companion object {
    const val TAG = "ProgressScreenshots"
    const val SHARED = "/sdcard/progress-shots"
    // on an emulator the first switch between screens can take longer than a frame or two
    const val SETTLE_MS = 1_500L
  }
}
