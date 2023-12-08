package com.quran.labs.androidquran.presenter.translation

import com.quran.data.core.QuranInfo
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class InlineTranslationPresenter @Inject constructor(
  translationModel: TranslationModel,
  dbAdapter: TranslationsDBAdapter,
  translationUtil: TranslationUtil,
  private val quranSettings: QuranSettings,
  private val translationListPresenter: TranslationListPresenter,
  quranInfo: QuranInfo
) : BaseTranslationPresenter<InlineTranslationPresenter.TranslationScreen>(
  translationModel, dbAdapter, translationUtil, quranInfo
) {
  private val scope = MainScope()
  private var cachedTranslations = emptyList<LocalTranslation>()

  init {
    translationListPresenter.translations()
      .onEach { translations ->
        cachedTranslations = translations
        translationScreen?.onTranslationsUpdated(translations)
      }
      .launchIn(scope)
  }

  fun refresh(verseRange: VerseRange) {
    disposable?.dispose()
    disposable = getVerses(false, getTranslations(quranSettings), verseRange)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeWith(object : DisposableSingleObserver<ResultHolder>() {
        override fun onSuccess(result: ResultHolder) {
          translationScreen?.setVerses(result.translations, result.ayahInformation)
        }

        override fun onError(e: Throwable) {}
      })
  }

  override fun bind(what: TranslationScreen) {
    super.bind(what)
    val translations = cachedTranslations
    if (translations.isNotEmpty()) {
      what.onTranslationsUpdated(translations)
    }
  }

  interface TranslationScreen {
    fun setVerses(translations: Array<LocalTranslation>, verses: List<QuranAyahInfo>)
    fun onTranslationsUpdated(translations: List<LocalTranslation>)
  }
}
