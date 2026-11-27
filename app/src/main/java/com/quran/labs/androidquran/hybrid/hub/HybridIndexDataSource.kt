package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.os.Build
import com.quran.data.core.QuranConstants
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.presenter.data.JuzListPresenter
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
class HybridIndexDataSource @Inject constructor(
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val settings: QuranSettings,
  private val juzListPresenter: Provider<JuzListPresenter>
) {

  private data class SurasCacheKey(
    val locales: String,
    val wantPrefix: Boolean,
    val wantTranslation: Boolean
  )

  @Volatile
  private var cachedSurasKey: SurasCacheKey? = null

  @Volatile
  private var cachedSuras: List<SuraIndexItem>? = null

  private fun currentCacheKey(context: Context): SurasCacheKey {
    val config = context.resources.configuration
    val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      config.locales.toLanguageTags()
    } else {
      @Suppress("DEPRECATION")
      config.locale?.toLanguageTag().orEmpty()
    }
    return SurasCacheKey(
      locales = locales,
      wantPrefix = context.resources.getBoolean(R.bool.show_surat_prefix),
      wantTranslation = settings.isShowSuraTranslatedName
    )
  }

  fun getCachedSurasOrNull(context: Context): List<SuraIndexItem>? {
    val existing = cachedSuras ?: return null
    return if (cachedSurasKey == currentCacheKey(context)) existing else null
  }

  fun prewarmSuras(context: Context): List<SuraIndexItem> {
    return buildSuras(context)
  }

  fun buildSuras(context: Context): List<SuraIndexItem> {
    val key = currentCacheKey(context)
    if (cachedSurasKey == key) {
      cachedSuras?.let { return it }
    }
    val built = buildSurasRange(context, QuranConstants.FIRST_SURA, QuranConstants.LAST_SURA)
    cachedSurasKey = key
    cachedSuras = built
    return built
  }

  fun buildSurasRange(context: Context, startSura: Int, endSura: Int): List<SuraIndexItem> {
    val safeStart = startSura.coerceAtLeast(QuranConstants.FIRST_SURA)
    val safeEnd = endSura.coerceAtMost(QuranConstants.LAST_SURA)
    if (safeEnd < safeStart) return emptyList()
    val wantPrefix = context.resources.getBoolean(R.bool.show_surat_prefix)
    val wantTranslation = settings.isShowSuraTranslatedName
    return (safeStart..safeEnd).map { sura ->
      val startPage = quranInfo.getPageNumberForSura(sura)
      val juz = quranInfo.getJuzFromPage(startPage)
      val isMakki = quranInfo.isMakki(sura)
      val ayahCount = quranInfo.getNumberOfAyahs(sura)
      SuraIndexItem(
        sura = sura,
        name = quranDisplayData.getSuraName(
          context = context,
          sura = sura,
          wantPrefix = wantPrefix,
          wantTranslation = wantTranslation
        ),
        metaText = HybridNumberFormatter.formatSurahMeta(context, isMakki, ayahCount),
        startPage = startPage,
        juz = juz,
        isMakki = isMakki,
        ayahCount = ayahCount
      )
    }
  }

  fun buildJuzs(context: Context, quarters: Array<String>): List<JuzIndexItem> {
    val wantPrefix = context.resources.getBoolean(R.bool.show_surat_prefix)
    return (1..QuranConstants.JUZ2_COUNT).map { juz ->
      val quarterIndex = (juz - 1) * QUARTERS_PER_JUZ
      val startAyah = quranInfo.getQuarterByIndex(quarterIndex)
      val startPage = quranInfo.getStartingPageForJuz(juz)
      JuzIndexItem(
        juz = juz,
        title = quranDisplayData.getJuzDisplayString(context, juz),
        startSuraName = quranDisplayData.getSuraName(
          context = context,
          sura = startAyah.sura,
          wantPrefix = wantPrefix,
          wantTranslation = false
        ),
        startAyah = startAyah.ayah,
        startPage = startPage,
        previewText = quarters.getOrNull(quarterIndex)?.trim().orEmpty()
      )
    }
  }

  fun buildHizbs(context: Context, quarters: Array<String>): List<HizbIndexItem> {
    val wantPrefix = context.resources.getBoolean(R.bool.show_surat_prefix)
    return (1..HIZB_COUNT).map { hizb ->
      val quarterIndex = (hizb - 1) * QUARTERS_PER_HIZB
      val startAyah = quranInfo.getQuarterByIndex(quarterIndex)
      val startPage = quranInfo.getPageFromSuraAyah(startAyah.sura, startAyah.ayah)
      val juz = ((hizb - 1) / HIZBS_PER_JUZ) + 1
      HizbIndexItem(
        hizb = hizb,
        juz = juz,
        startSuraName = quranDisplayData.getSuraName(
          context = context,
          sura = startAyah.sura,
          wantPrefix = wantPrefix,
          wantTranslation = false
        ),
        startAyah = startAyah.ayah,
        startPage = startPage,
        previewText = quarters.getOrNull(quarterIndex)?.trim().orEmpty()
      )
    }
  }

  fun loadResourceQuartersOrEmpty(context: Context): Array<String> {
    return if (QuranFileConstants.FETCH_QUARTER_NAMES_FROM_DATABASE) {
      emptyArray()
    } else {
      context.resources.getStringArray(R.array.quarter_prefix_array)
    }
  }

  suspend fun loadQuarterTexts(context: Context): Array<String> {
    return if (QuranFileConstants.FETCH_QUARTER_NAMES_FROM_DATABASE) {
      withContext(Dispatchers.IO) {
        runCatching { juzListPresenter().quarters().toTypedArray() }
          .getOrDefault(emptyArray())
      }
    } else {
      context.resources.getStringArray(R.array.quarter_prefix_array)
    }
  }

  companion object {
    const val HIZB_COUNT: Int = 60
    const val QUARTERS_PER_HIZB: Int = 4
    const val QUARTERS_PER_JUZ: Int = 8
    const val HIZBS_PER_JUZ: Int = 2
  }
}
