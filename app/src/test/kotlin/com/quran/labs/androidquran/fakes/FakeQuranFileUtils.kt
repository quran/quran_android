package com.quran.labs.androidquran.fakes

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.quran.data.source.DisplaySize
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo

/**
 * Factory for creating a real QuranFileUtils instance backed by Robolectric.
 *
 * QuranFileUtils is a final class and cannot be subclassed. This factory constructs
 * a real instance using the Robolectric context so that:
 * - getQuranDatabaseDirectory() returns a real (Robolectric-managed) path
 * - No translation .db files exist at that path in tests
 * - translationExists=false for all server translations in mergeWithServerTranslations
 */
object FakeQuranFileUtils {
  fun create(context: Context): QuranFileUtils {
    val pageProvider = FakePageProvider()
    val display = context.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)!!
    val screenInfo = QuranScreenInfo(
      context,
      display,
      pageProvider.getPageSizeCalculator(DisplaySize(0, 0))
    )
    return QuranFileUtils(context, pageProvider, screenInfo)
  }
}
