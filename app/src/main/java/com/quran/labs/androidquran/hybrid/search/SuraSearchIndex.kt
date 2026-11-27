package com.quran.labs.androidquran.hybrid.search

import android.content.Context
import android.content.res.Configuration
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import java.text.Normalizer
import java.util.Locale
import com.quran.mobile.common.ui.core.R as UiCoreR

class SuraSearchIndex(
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData
) {
  private data class IndexedSura(
    val hit: SuraSearchHit,
    val normalizedArabic: String,
    val normalizedDisplay: String,
    val normalizedLatin: String,
    val collapsedDisplay: String,
    val collapsedLatin: String
  )

  private data class CachedIndex(
    val localeTags: String,
    val entries: List<IndexedSura>
  )

  @Volatile
  private var cachedIndex: CachedIndex? = null

  fun search(context: Context, query: String): List<SuraSearchHit> {
    val entries = getOrBuildEntries(context)
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      return entries.map { it.hit }
    }

    val asciiDigits = convertDigitsToAscii(trimmed)
    val numericQuery = asciiDigits.toIntOrNull()
    val normAr = normalizeArabic(trimmed).removePrefix("سوره ").trim()
    val normLatin = normalizeLatin(asciiDigits)
    val collapsedQueryLatin = normLatin.replace(" ", "")

    val exactNumberMatches = ArrayList<SuraSearchHit>(1)
    val exactNameMatches = ArrayList<SuraSearchHit>(2)
    val prefixMatches = ArrayList<SuraSearchHit>(8)
    val containsMatches = ArrayList<SuraSearchHit>(16)

    for (entry in entries) {
      if (numericQuery != null && entry.hit.sura == numericQuery) {
        exactNumberMatches.add(entry.hit)
        continue
      }
      val matchesArabic = normAr.isNotEmpty() && entry.normalizedArabic.contains(normAr)
      val matchesDisplay = normLatin.isNotEmpty() && entry.normalizedDisplay.contains(normLatin)
      val matchesLatin = normLatin.isNotEmpty() && entry.normalizedLatin.contains(normLatin)
      val matchesCollapsed = collapsedQueryLatin.isNotEmpty() &&
        (entry.collapsedDisplay.contains(collapsedQueryLatin) ||
          entry.collapsedLatin.contains(collapsedQueryLatin))
      if (!matchesArabic && !matchesDisplay && !matchesLatin && !matchesCollapsed) {
        continue
      }
      val isExactName = (normAr.isNotEmpty() && entry.normalizedArabic == normAr) ||
        (normLatin.isNotEmpty() &&
          (entry.normalizedDisplay == normLatin || entry.normalizedLatin == normLatin)) ||
        (collapsedQueryLatin.isNotEmpty() &&
          (entry.collapsedDisplay == collapsedQueryLatin ||
            entry.collapsedLatin == collapsedQueryLatin))
      if (isExactName) {
        exactNameMatches.add(entry.hit)
        continue
      }
      val isPrefix = (normAr.isNotEmpty() && entry.normalizedArabic.startsWith(normAr)) ||
        (normLatin.isNotEmpty() &&
          (entry.normalizedDisplay.startsWith(normLatin) ||
            entry.normalizedLatin.startsWith(normLatin))) ||
        (collapsedQueryLatin.isNotEmpty() &&
          (entry.collapsedDisplay.startsWith(collapsedQueryLatin) ||
            entry.collapsedLatin.startsWith(collapsedQueryLatin)))
      if (isPrefix) {
        prefixMatches.add(entry.hit)
      } else {
        containsMatches.add(entry.hit)
      }
    }

    if (exactNumberMatches.isEmpty() &&
      exactNameMatches.isEmpty() &&
      prefixMatches.isEmpty() &&
      containsMatches.isEmpty()
    ) {
      return emptyList()
    }
    return buildList(
      exactNumberMatches.size + exactNameMatches.size + prefixMatches.size + containsMatches.size
    ) {
      addAll(exactNumberMatches)
      addAll(exactNameMatches)
      addAll(prefixMatches)
      addAll(containsMatches)
    }
  }

  private fun getOrBuildEntries(context: Context): List<IndexedSura> {
    val localeTags = context.resources.configuration.locales.toLanguageTags()
    cachedIndex?.takeIf { it.localeTags == localeTags }?.let { return it.entries }
    return synchronized(this) {
      cachedIndex?.takeIf { it.localeTags == localeTags }?.entries ?: buildEntries(context).also {
        cachedIndex = CachedIndex(localeTags = localeTags, entries = it)
      }
    }
  }

  private fun buildEntries(context: Context): List<IndexedSura> {
    val arNames = loadLocaleSuraNames(context, Locale.forLanguageTag("ar"))
    val enNames = loadLocaleSuraNames(context, Locale.ENGLISH)

    return (Constants.SURA_FIRST..Constants.SURA_LAST).map { sura ->
      val idx = sura - 1
      val displayName = quranDisplayData.getSuraName(
        context = context,
        sura = sura,
        wantPrefix = false,
        wantTranslation = false
      )
      val startPage = quranInfo.getPageNumberForSura(sura)
      val juzNumber = quranInfo.getJuzForDisplayFromPage(startPage)
      val ayahCount = quranInfo.getNumberOfAyahs(sura)
      val isMakki = quranInfo.isMakki(sura)
      val meta = quranDisplayData.getSuraListMetaString(context, sura)

      val arabicSource = arNames.getOrElse(idx) { displayName }
      val latinSource = enNames.getOrElse(idx) { displayName }

      val normalizedDisplay = normalizeLatin(displayName)
      val normalizedLatin = normalizeLatin(latinSource)
      IndexedSura(
        hit = SuraSearchHit(
          sura = sura,
          displayName = displayName,
          metaString = meta,
          juzNumber = juzNumber,
          startPage = startPage,
          ayahCount = ayahCount,
          isMakki = isMakki
        ),
        normalizedArabic = normalizeArabic(arabicSource),
        normalizedDisplay = normalizedDisplay,
        normalizedLatin = normalizedLatin,
        collapsedDisplay = normalizedDisplay.replace(" ", ""),
        collapsedLatin = normalizedLatin.replace(" ", "")
      )
    }
  }

  private fun loadLocaleSuraNames(context: Context, locale: Locale): Array<String> {
    return runCatching {
      val config = Configuration(context.resources.configuration).apply {
        setLocale(locale)
      }
      context.createConfigurationContext(config).resources.getStringArray(UiCoreR.array.sura_names)
    }.getOrElse {
      context.resources.getStringArray(UiCoreR.array.sura_names)
    }
  }

  internal companion object {
    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun normalizeArabic(input: String): String {
      if (input.isEmpty()) return ""
      val sb = StringBuilder(input.length)
      var prevSpace = true
      for (ch in input) {
        val code = ch.code
        // Strip Arabic tashkeel, tatweel, Quranic annotation marks, and dagger alif
        if (code in 0x0610..0x061A ||
          code in 0x064B..0x065F ||
          code == 0x0670 ||
          code == 0x0640 ||
          code in 0x06D6..0x06ED
        ) {
          continue
        }
        val mapped = when (ch) {
          'أ', 'إ', 'آ', 'ٱ' -> 'ا'
          'ة' -> 'ه'
          'ى' -> 'ي'
          'ؤ' -> 'و'
          'ئ' -> 'ي'
          else -> ch
        }
        if (mapped.isWhitespace()) {
          if (!prevSpace) {
            sb.append(' ')
            prevSpace = true
          }
        } else {
          sb.append(mapped)
          prevSpace = false
        }
      }
      return sb.toString().trim()
    }

    fun normalizeLatin(input: String): String {
      if (input.isEmpty()) return ""
      val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
      val withoutMarks = DIACRITICS_REGEX.replace(decomposed, "")
      return buildString(withoutMarks.length) {
        var prevSpace = true
        for (ch in withoutMarks) {
          when {
            ch == '\u02BF' || ch == '\u02BE' || ch == '\'' || ch == '\u2018' || ch == '\u2019' -> {
              // Strip transliteration ayn/hamza modifier letters and apostrophes
            }
            ch == '-' || ch == '\u2010' || ch == '\u2011' || ch == '\u2012' ||
              ch == '\u2013' || ch == '\u2014' || ch == '_' || ch.isWhitespace() -> {
              if (!prevSpace) {
                append(' ')
                prevSpace = true
              }
            }
            ch.isLetterOrDigit() -> {
              append(ch.lowercaseChar())
              prevSpace = false
            }
          }
        }
      }.trim()
    }

    fun convertDigitsToAscii(input: String): String {
      val sb = StringBuilder(input.length)
      for (ch in input) {
        when (ch) {
          in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - 0x0660)).toChar())
          in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - 0x06F0)).toChar())
          else -> sb.append(ch)
        }
      }
      return sb.toString()
    }
  }
}
