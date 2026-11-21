package com.quran.labs.androidquran.presenter.translation

import com.quran.data.core.QuranInfo
import com.quran.data.di.QuranPageScope
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.mobile.translation.verse.TranslationModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@QuranPageScope
class TranslationPresenter @Inject internal constructor(
  translationModel: TranslationModel,
  private val quranSettings: QuranSettings,
  translationsAdapter: TranslationsDBAdapter,
  translationUtil: TranslationUtil,
  translationListPresenter: TranslationListPresenter,
  private val quranInfo: QuranInfo,
  private val pages: IntArray
) :
  BaseTranslationPresenter<TranslationPresenter.TranslationScreen>(
    translationModel, translationsAdapter, translationUtil, quranInfo, translationListPresenter
  ) {

  // the saved scroll position is only restored the first time the verses are shown. later
  // refreshes (ex when coming back from the share sheet) keep the position the person scrolled to.
  private var isScrollPositionRestored = false

  suspend fun refresh() {
    var didShowVerses = false
    pages
      .map {
        withContext(Dispatchers.IO) {
          getVerses(
            quranSettings.wantArabicInTranslationView(),
            getTranslations(quranSettings), quranInfo.getVerseRangeForPage(it)
          )
        }
      }
      .onEach { result ->
        val screen = translationScreen
        if (screen != null && result.ayahInformation.isNotEmpty()) {
          screen.setVerses(
            getPage(result.ayahInformation), result.translations,
            result.ayahInformation
          )
          if (!isScrollPositionRestored) {
            screen.updateScrollPosition()
          }
          didShowVerses = true
        }
      }

    if (didShowVerses) {
      isScrollPositionRestored = true
    }
  }

  private fun getPage(result: List<QuranAyahInfo>): Int {
    val firstPage = pages.firstOrNull()
    return if (pages.size == 1 && firstPage != null) {
      firstPage
    } else {
      quranInfo.getPageFromSuraAyah(result[0].sura, result[0].ayah)
    }
  }

  interface TranslationScreen {
    fun setVerses(
      page: Int,
      translations: Array<LocalTranslation>,
      verses: List<@JvmSuppressWildcards QuranAyahInfo>
    )

    /**
     * Restores the saved scroll position. Only called the first time verses are shown, so that
     * refreshing the page doesn't move the list away from where the person scrolled to.
     */
    fun updateScrollPosition()
  }
}
