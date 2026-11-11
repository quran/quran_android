package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.content.Intent
import com.quran.data.core.QuranInfo
import com.quran.data.core.SuggestedReadingBookmark
import com.quran.data.di.ActivityScope
import com.quran.data.model.JumpLocation
import com.quran.data.model.Page
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.mobile.di.qualifier.ActivityContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider

@ActivityScope
class QuranNavigator @Inject constructor(
  @ActivityContext private val context: Context,
  private val quranInfo: QuranInfo,
  private val suggestedReadingBookmark: SuggestedReadingBookmark
) {
  class Factory @Inject constructor(
    private val quranInfo: QuranInfo,
    private val suggestedReadingBookmark: Provider<SuggestedReadingBookmark>
  ) {
    fun create(context: Context): QuranNavigator =
      QuranNavigator(context, quranInfo, suggestedReadingBookmark())
  }

  @JvmOverloads
  fun jumpTo(
    location: JumpLocation,
    bookmarkType: ReadingBookmarkType? = null,
    showTranslation: Boolean? = null,
    intentFlags: Int = 0
  ) {
    val intent = intentFor(location, showTranslation).addFlags(intentFlags)
    suggestedReadingBookmark.onJump(location, bookmarkType)
    if (context is PagerActivity) {
      context.onNewIntent(intent)
    } else {
      context.startActivity(intent)
    }
  }

  fun intentFor(
    location: JumpLocation,
    showTranslation: Boolean? = null
  ): Intent = Intent(context, PagerActivity::class.java).apply {
    when (location) {
      is Page -> putExtra("page", location.page)
      is SuraAyah -> {
        putExtra("page", quranInfo.getPageFromSuraAyah(location.sura, location.ayah))
        putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, location.sura)
        putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, location.ayah)
      }
    }
    showTranslation?.let { putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, it) }
  }
}
