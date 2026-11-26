package com.quran.labs.androidquran.hybrid.search

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import com.quran.labs.androidquran.ui.util.TypefaceManager

/**
 * Caches the bundled `UthmanTN1Ver10.otf` Compose [FontFamily] once per process so
 * `LazyColumn` scroll and fling perform zero asset loads or FontFamily allocations.
 */
object UthmaniFontCache {
  @Volatile
  private var cachedFontFamily: FontFamily? = null

  fun get(context: Context): FontFamily {
    cachedFontFamily?.let { return it }
    return synchronized(this) {
      cachedFontFamily ?: runCatching {
        FontFamily(TypefaceManager.getSearchArabicTypeface(context.applicationContext))
      }.getOrDefault(FontFamily.Default).also { loaded ->
        cachedFontFamily = loaded
      }
    }
  }
}
