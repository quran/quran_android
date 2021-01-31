package com.quran.labs.androidquran.presenter.translation

import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.data.model.QuranText
import com.quran.labs.androidquran.common.TranslationMetadata
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.data.SuraAyahIterator
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.ArrayList
import java.util.HashMap

internal open class BaseTranslationPresenter<T> internal constructor(
    private val translationModel: TranslationModel,
    private val translationsAdapter: TranslationsDBAdapter,
    private val translationUtil: TranslationUtil,
    private val quranInfo: QuranInfo
) : Presenter<T> {
  private var lastCacheTime: Long = 0
  private val translationMap: MutableMap<String, LocalTranslation> = HashMap()

  var translationScreen: T? = null
  var disposable: Disposable? = null

  fun getVerses(getArabic: Boolean,
                translations: List<String>,
                verseRange: VerseRange
  ): Single<ResultHolder> {
    // get all the translations for these verses, using a source of the list of active translations
    val source = Observable.fromIterable(translations)

    val translationsObservable =
        source.concatMapEager { db ->
          translationModel.getTranslationFromDatabase(verseRange, db)
              .map { texts -> ensureProperTranslations(verseRange, texts) }
              .onErrorReturnItem(ArrayList())
              .toObservable()
            }
            .toList()
    val arabicObservable = if (!getArabic)
      Single.just(ArrayList())
    else
      translationModel.getArabicFromDatabase(verseRange).onErrorReturnItem(ArrayList())
    return Single.zip(arabicObservable, translationsObservable, getTranslationMapSingle(),
        { arabic: List<QuranText>,
                    texts: List<List<QuranText>>,
                    map: Map<String, LocalTranslation> ->
          val translationInfos = getTranslations(translations, map)
          val ayahInfo = combineAyahData(verseRange, arabic, texts, translationInfos)
          ResultHolder(translationInfos, ayahInfo)
        })
        .subscribeOn(Schedulers.io())
  }

  fun getTranslations(quranSettings: QuranSettings): List<String> {
    return ArrayList(quranSettings.activeTranslations)
  }

  fun getTranslations(translations: List<String>,
                      translationMap: Map<String, LocalTranslation>): Array<LocalTranslation> {
    val translationCount = translations.size

    return if (translationCount == 0) {
      // fallback to all translations when the map is empty
      translationMap.map { it.value }.toTypedArray()
    } else {
      translations.map { translationMap[it] ?: LocalTranslation(filename = it) }.toTypedArray()
    }
  }

  fun combineAyahData(verseRange: VerseRange,
                      arabic: List<QuranText>,
                      texts: List<List<QuranText>>,
                      translationInfo: Array<LocalTranslation>): List<QuranAyahInfo> {
    val arabicSize = arabic.size
    val translationCount = texts.size

    return when {
      translationCount > 0 -> {
        val result = ArrayList<QuranAyahInfo>()
        val verses = if (arabicSize == 0) verseRange.versesInRange else arabicSize
        for (i in 0 until verses) {
          val quranTexts = texts.map { if (it.size > i) it[i] else null }
          val arabicQuranText = if (arabicSize == 0) null else arabic[i]
          val element = quranTexts.findLast { it != null } ?: arabicQuranText

          if (element != null) {
            // replace with "" when a translation doesn't load to keep translations aligned
            val ayahTranslations = quranTexts.mapIndexed { index: Int, quranText: QuranText? ->
              val translation = translationInfo.getOrNull(index);
              val translationMinVersion = translation?.minimumVersion ?: 0
              val translationId = translation?.id ?: -1;
              val shouldProcess =
                  translationMinVersion >= TranslationUtil.MINIMUM_PROCESSING_VERSION
              val text = quranText ?: QuranText(element.sura, element.ayah, "")
              if (shouldProcess) {
                translationUtil.parseTranslationText(text, translationId)
              } else {
                TranslationMetadata(element.sura, element.ayah, text.text, translationId)
              }
            }

            result.add(
                QuranAyahInfo(element.sura, element.ayah, arabicQuranText?.text, ayahTranslations,
                    quranInfo.getAyahId(element.sura, element.ayah)))
          }
        }
        result
      }
      arabicSize > 0 -> arabic.map {
        QuranAyahInfo(it.sura, it.ayah, it.text, emptyList(),
            quranInfo.getAyahId(it.sura, it.ayah)) }
      else -> emptyList()
    }
  }

  /**
   * Ensures that the list of translations is valid
   * In this case, valid means that the number of verses that we have translations for is either
   * the same as the verse range, or that it's 0 (i.e. due to an error querying the database). If
   * the list has a non-zero length that is less than what the verseRange says, it adds empty
   * entries for those.
   *
   * @param verseRange the range of verses we're trying to get
   * @param inputTexts the data we got back from the database
   * @return a list of QuranText with a length of either 0 or the verse range
   */
  fun ensureProperTranslations(verseRange: VerseRange,
                               inputTexts: List<QuranText>): List<QuranText> {
    val expectedVerses = verseRange.versesInRange
    val textSize = inputTexts.size

    val texts = inputTexts.toMutableList()
    if (textSize == 0 || textSize == expectedVerses) {
      return texts
    }

    // missing some entries for some ayat - this is a work around for bad data in some databases
    // ex. ibn katheer is missing 3 records, 1 in each of suras 5, 17, and 87.
    val start =
      SuraAyah(verseRange.startSura, verseRange.startAyah)
    val end =
      SuraAyah(verseRange.endingSura, verseRange.endingAyah)
    val iterator = SuraAyahIterator(quranInfo, start, end)

    var i = 0
    while (iterator.next()) {
      val item = if (texts.size > i) texts[i] else null
      if (item == null || item.sura != iterator.sura || item.ayah != iterator.ayah) {
        texts.add(i, QuranText(iterator.sura, iterator.ayah, ""))
      }
      i++
    }
    return texts
  }

  private fun getTranslationMapSingle(): Single<Map<String, LocalTranslation>> {
    return if (this.translationMap.isEmpty() ||
        this.lastCacheTime != translationsAdapter.lastWriteTime) {
      Single.fromCallable<List<LocalTranslation>> { translationsAdapter.translations }
          .map { translations -> translations.associateBy { it.filename } }
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .doOnSuccess { map ->
            this.lastCacheTime = translationsAdapter.lastWriteTime
            this.translationMap.clear()
            this.translationMap.putAll(map)
          }
    } else {
      Single.just(this.translationMap)
    }
  }

  internal class ResultHolder(val translations: Array<LocalTranslation>,
                              val ayahInformation: List<QuranAyahInfo>)

  override fun bind(what: T) {
    translationScreen = what
  }

  override fun unbind(what: T) {
    translationScreen = null
    disposable?.dispose()
  }
}
