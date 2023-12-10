package com.quran.labs.androidquran.presenter.translation

import com.quran.data.core.QuranInfo
import com.quran.data.di.QuranPageScope
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.observers.DisposableObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@QuranPageScope
internal class TranslationPresenter @Inject internal constructor(
  translationModel: TranslationModel,
  private val quranSettings: QuranSettings,
  translationsAdapter: TranslationsDBAdapter,
  translationUtil: TranslationUtil,
  private val quranInfo: QuranInfo,
  private val pages: IntArray
) :
  BaseTranslationPresenter<TranslationPresenter.TranslationScreen>(
    translationModel, translationsAdapter, translationUtil, quranInfo
  ) {

  private val scope = MainScope()

  fun legacyRefresh() {
    scope.launch {
      refresh()
    }
  }

  suspend fun refresh() {
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
          screen.updateScrollPosition()
        }
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

    fun updateScrollPosition()
  }
}
