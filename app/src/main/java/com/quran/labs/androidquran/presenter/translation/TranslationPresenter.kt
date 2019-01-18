package com.quran.labs.androidquran.presenter.translation

import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.QuranInfo
import com.quran.labs.androidquran.data.SuraAyah
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.di.QuranPageScope
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.ShareUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableObserver
import javax.inject.Inject

@QuranPageScope
internal class TranslationPresenter @Inject internal constructor(translationModel: TranslationModel,
                     private val quranSettings: QuranSettings,
                     translationsAdapter: TranslationsDBAdapter,
                     private val shareUtil: ShareUtil,
                     private val quranInfo: QuranInfo,
                     private val pages: Array<Int?>) :
    BaseTranslationPresenter<TranslationPresenter.TranslationScreen>(
        translationModel, translationsAdapter, quranInfo) {

  fun refresh() {
    disposable?.dispose()

    disposable = Observable.fromArray(*pages)
        .flatMap { page ->
          getVerses(quranSettings.wantArabicInTranslationView(),
              getTranslations(quranSettings), quranInfo.getVerseRangeForPage(page))
              .toObservable()
        }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableObserver<BaseTranslationPresenter.ResultHolder>() {
          override fun onNext(result: BaseTranslationPresenter.ResultHolder) {
            if (translationScreen != null && result.ayahInformation.isNotEmpty()) {
              translationScreen!!.setVerses(
                  getPage(result.ayahInformation), result.translations,
                  result.ayahInformation)
              translationScreen!!.updateScrollPosition()
            }
          }

          override fun onError(e: Throwable) {}

          override fun onComplete() {}
        })
  }

  fun onTranslationAction(activity: PagerActivity,
                          ayah: QuranAyahInfo,
                          translationNames: Array<String>,
                          actionId: Int) {
    when (actionId) {
      R.id.cab_share_ayah_link -> {
        val bounds = SuraAyah(ayah.sura, ayah.ayah)
        activity.shareAyahLink(bounds, bounds)
      }
      R.id.cab_share_ayah_text, R.id.cab_copy_ayah -> {
        val shareText = shareUtil.getShareText(activity, ayah, translationNames)
        if (actionId == R.id.cab_share_ayah_text) {
          shareUtil.shareViaIntent(activity, shareText, R.string.share_ayah_text)
        } else {
          shareUtil.copyToClipboard(activity, shareText)
        }
      }
    }
  }

  private fun getPage(result: List<QuranAyahInfo>): Int {
    return pages.firstOrNull() ?: result[0].let { ayahInfo ->
      quranInfo.getPageFromSuraAyah(ayahInfo.sura, ayahInfo.ayah)
    }
  }

  interface TranslationScreen {
    fun setVerses(page: Int, translations: Array<String>, verses: List<@JvmSuppressWildcards QuranAyahInfo>)
    fun updateScrollPosition()
  }
}
