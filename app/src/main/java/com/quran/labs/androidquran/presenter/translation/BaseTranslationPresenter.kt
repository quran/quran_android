package com.quran.labs.androidquran.presenter.translation

import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.SuraAyahIterator
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.common.TranslationMetadata
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

open class BaseTranslationPresenter<T : Any> internal constructor(
    private val translationModel: TranslationModel,
    private val translationsAdapter: TranslationsDBAdapter,
    private val translationUtil: TranslationUtil,
    private val quranInfo: QuranInfo
) : Presenter<T> {
  private val translationMap: MutableMap<String, LocalTranslation> = HashMap()

  var translationScreen: T? = null

  suspend fun getVerses(
    getArabic: Boolean,
    translationsFileNames: List<String>,
    verseRange: VerseRange
  ): ResultHolder {
    return withContext(Dispatchers.IO) {
      val translations = translationsAdapter.getTranslations().first()
      val sortedTranslations: List<LocalTranslation> = translations.sortedBy { it.displayOrder }

      val orderedTranslationsFileNames = sortedTranslations
        .filter { translationsFileNames.contains(it.filename) }
        .map { it.filename }

      val job = SupervisorJob()
      // get all the translations for these verses, using a source of the list of ordered active translations
      val translationData = orderedTranslationsFileNames.map {
        async(job) {
          val initialTexts = translationModel.getTranslationFromDatabase(verseRange, it)
          ensureProperTranslations(verseRange, initialTexts)
        }
      }

      val arabic = async(job) {
        if (getArabic) {
          translationModel.getArabicFromDatabase(verseRange)
        } else {
          emptyList()
        }
      }

      val arabicText =
        try {
          arabic.await()
        } catch (e: Exception) {
          emptyList()
        }

      val translationTexts = translationData.map { deferred ->
        try {
          deferred.await()
        } catch (e: Exception) {
          emptyList()
        }
      }
      val translationMap = getTranslationMap()

      val translationInfos = getTranslations(orderedTranslationsFileNames, translationMap)
      val ayahInfo = combineAyahData(verseRange, arabicText, translationTexts, translationInfos)
      ResultHolder(translationInfos, ayahInfo)
    }
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
              val translation = translationInfo.getOrNull(index)
              val translationMinVersion = translation?.minimumVersion ?: 0
              val translationId = translation?.id ?: -1
              val shouldProcess =
                  translationMinVersion >= TranslationUtil.MINIMUM_PROCESSING_VERSION
              val text = quranText ?: QuranText(element.sura, element.ayah, "")
              if (shouldProcess) {
                translationUtil.parseTranslationText(text, translationId.toInt())
              } else {
                TranslationMetadata(element.sura, element.ayah, text.text, translationId.toInt())
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

  private suspend fun getTranslationMap(): Map<String, LocalTranslation> {
    val currentTranslationMap = translationMap
    return withContext(Dispatchers.IO) {
      if (currentTranslationMap.isEmpty()) {
        val updatedTranslations = translationsAdapter.getTranslations()
          .map { it.associateBy { it.filename } }
          .first()
        translationMap.clear()
        translationMap.putAll(updatedTranslations)
        updatedTranslations
      } else {
        currentTranslationMap
      }
    }
  }

  class ResultHolder(val translations: Array<LocalTranslation>,
                     val ayahInformation: List<QuranAyahInfo>)

  override fun bind(what: T) {
    translationScreen = what
  }

  override fun unbind(what: T) {
    translationScreen = null
  }
}
