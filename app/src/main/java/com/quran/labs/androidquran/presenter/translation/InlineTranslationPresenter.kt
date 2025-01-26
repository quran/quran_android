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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InlineTranslationPresenter @Inject constructor(
  translationModel: TranslationModel,
  dbAdapter: TranslationsDBAdapter,
  translationUtil: TranslationUtil,
  private val quranSettings: QuranSettings,
  translationListPresenter: TranslationListPresenter,
  quranInfo: QuranInfo
) : BaseTranslationPresenter<InlineTranslationPresenter.TranslationScreen>(
  translationModel, dbAdapter, translationUtil, quranInfo, translationListPresenter
) {
  private var cachedTranslations = emptyList<LocalTranslation>()
  private var lastVerseRange: VerseRange? = null

  suspend fun refresh(verseRange: VerseRange) {
    val ayahHasBeenChanged = verseRange != lastVerseRange
    val result = withContext(Dispatchers.IO) {
      getVerses(false, getTranslations(quranSettings), verseRange)
    }
    translationScreen?.setVerses(result.translations, result.ayahInformation, ayahHasBeenChanged)
    lastVerseRange = verseRange
  }

  override fun bind(what: TranslationScreen) {
    super.bind(what)
    val translations = cachedTranslations
    what.onTranslationsUpdated(translations)
  }

  override fun onAvailableTranslationsChanged(localTranslations: List<LocalTranslation>) {
    super.onAvailableTranslationsChanged(localTranslations)
    cachedTranslations = localTranslations
    translationScreen?.onTranslationsUpdated(localTranslations)
  }

  interface TranslationScreen {
    fun setVerses(translations: Array<LocalTranslation>, verses: List<QuranAyahInfo>, ayahHasBeenChanged: Boolean)
    fun onTranslationsUpdated(translations: List<LocalTranslation>)
  }
}
