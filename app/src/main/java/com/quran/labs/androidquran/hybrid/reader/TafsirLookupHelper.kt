package com.quran.labs.androidquran.hybrid.reader

import androidx.collection.LruCache
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.mobile.translation.verse.TranslationModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

class TafsirLookupHelper(
  private val translationsDBAdapter: TranslationsDBAdapter,
  private val translationModel: TranslationModel,
  private val translationUtil: TranslationUtil,
  private val arabicTextLoader: suspend (SuraAyah) -> String? = { null },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val entryCache = LruCache<CacheKey, CachedTafsirEntry>(24)
  private val arabicCache = LruCache<SuraAyah, String>(24)

  fun invalidateCache() {
    entryCache.evictAll()
  }

  suspend fun loadTafsirForAyah(
    sura: Int,
    ayah: Int,
    suraName: String,
    preferredFilename: String? = null,
    activeFilenames: Set<String> = emptySet()
  ): TafsirDisplayState = withContext(ioDispatcher) {
    val installed = runCatching { translationsDBAdapter.getTranslations().first() }
      .onFailure { Timber.w(it, "Failed to query installed tafsir/translation sources") }
      .getOrDefault(emptyList())

    val cachedArabic = arabicCache[SuraAyah(sura, ayah)] ?: run {
      val loaded = runCatching { arabicTextLoader(SuraAyah(sura, ayah)) }.getOrNull()
        ?.takeIf { it.isNotBlank() }
      if (loaded != null) {
        arabicCache.put(SuraAyah(sura, ayah), loaded)
      }
      loaded
    }

    if (installed.isEmpty()) {
      return@withContext TafsirDisplayState(
        loadState = TafsirLoadState.EMPTY,
        sura = sura,
        ayah = ayah,
        suraName = suraName,
        ayahArabicText = cachedArabic,
        sourceName = "",
        selectedFilename = null,
        availableSources = emptyList(),
        snippetText = "",
        fullText = ""
      )
    }

    val sources = installed.map { it.toTafsirSourceItem() }
    val selected = selectPreferredSource(installed, activeFilenames, preferredFilename)
      ?: installed.first()

    val key = CacheKey(selected.filename, sura, ayah)
    val cached = entryCache[key]
    val resolvedText = if (cached != null) {
      cached.text
    } else {
      val raw = runCatching {
        val range = VerseRange(sura, ayah, sura, ayah, 1)
        translationModel.getTranslationFromDatabase(range, selected.filename)
      }.onFailure {
        Timber.w(it, "Failed to load tafsir %s for %d:%d", selected.filename, sura, ayah)
      }.getOrDefault(emptyList())
      val text = extractDisplayText(raw, selected, translationUtil)
      if (text.isNotEmpty()) {
        entryCache.put(key, CachedTafsirEntry(text))
      }
      text
    }

    TafsirDisplayState(
      loadState = TafsirLoadState.LOADED,
      sura = sura,
      ayah = ayah,
      suraName = suraName,
      ayahArabicText = cachedArabic,
      sourceName = selected.displayTitle(),
      selectedFilename = selected.filename,
      availableSources = sources,
      snippetText = resolvedText,
      fullText = resolvedText
    )
  }

  companion object {
    private val FOOTNOTE_REGEX = Regex("""\[\[[\s\S]*?]]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun selectPreferredSource(
      sources: List<LocalTranslation>,
      activeFilenames: Set<String> = emptySet(),
      preferredFilename: String? = null
    ): LocalTranslation? {
      if (sources.isEmpty()) return null
      if (!preferredFilename.isNullOrBlank()) {
        sources.firstOrNull { it.filename == preferredFilename }?.let { return it }
      }

      // 1. Prioritize Al-Tafsir Al-Muyassar
      sources.firstOrNull { it.isMuyassar() }?.let { return it }

      // 2. Any Arabic tafsir
      sources.firstOrNull { it.isArabicTafsir() }?.let { return it }

      // 3. Any Arabic source
      sources.firstOrNull { it.isArabic() }?.let { return it }

      // 4. Currently active translation
      if (activeFilenames.isNotEmpty()) {
        sources.firstOrNull { it.filename in activeFilenames }?.let { return it }
      }

      // 5. Fallback to first installed source
      return sources.first()
    }

    internal fun extractDisplayText(
      verses: List<QuranText>,
      source: LocalTranslation,
      translationUtil: TranslationUtil
    ): String {
      val first = verses.firstOrNull() ?: return ""
      val parsed = if (source.minimumVersion >= TranslationUtil.MINIMUM_PROCESSING_VERSION) {
        translationUtil.parseTranslationText(first, source.id.toInt()).text
      } else {
        first.extraData?.takeIf { it.isNotBlank() } ?: first.text
      }
      return FOOTNOTE_REGEX.replace(parsed, "")
        .replace(WHITESPACE_REGEX, " ")
        .trim()
    }

    fun isMuyassarName(filename: String?, sourceName: String?): Boolean {
      return listOfNotNull(filename, sourceName).any { field ->
        val lower = field.lowercase()
        "muyassar" in lower || "moyassar" in lower || "الميسر" in field
      }
    }

    fun hasArabicScript(text: String?): Boolean {
      return text != null && text.any { it in '\u0600'..'\u06FF' }
    }

    fun localizedArabicTafsirTitle(
      filename: String?,
      name: String?,
      translator: String? = null,
      translatorForeign: String? = null,
      languageCode: String? = null
    ): String? {
      if (hasArabicScript(name)) return name!!.trim()
      if (hasArabicScript(translatorForeign)) return translatorForeign!!.trim()

      val fn = filename.orEmpty().lowercase()
      val nm = name.orEmpty().lowercase()
      val tr = translator.orEmpty().lowercase()
      val isExplicitEnglish = fn.startsWith("en.") || nm.startsWith("english")
      val isArabicSource = !isExplicitEnglish && (
        languageCode.equals("ar", ignoreCase = true) ||
          fn.startsWith("ar.") ||
          fn.contains("_ar") ||
          "arabic" in nm
        )
      if (!isArabicSource) return null

      val combined = "$fn $nm $tr"
      return when {
        "muyassar" in combined || "moyassar" in combined -> "التفسير الميسر"
        "katheer" in combined || "kathir" in combined -> "تفسير ابن كثير"
        "saadi" in combined || "sa3di" in combined || "sadi" in combined -> "تفسير السعدي"
        "baghawy" in combined || "baghawi" in combined -> "تفسير البغوي"
        "jalalayn" in combined || "jalalain" in combined -> "تفسير الجلالين"
        "qurtubi" in combined || "qortobi" in combined || "qortoby" in combined -> "تفسير القرطبي"
        "tabari" in combined || "tabary" in combined -> "تفسير الطبري"
        "waseet" in combined || "wasit" in combined || "wasyt" in combined -> "التفسير الوسيط"
        "tanweer" in combined || "tanwir" in combined -> "التحرير والتنوير"
        "mukhtasar" in combined || "mokhtasar" in combined -> "المختصر في التفسير"
        "e3rab" in combined || "irab" in combined || "i3rab" in combined -> "إعراب القرآن"
        else -> null
      }
    }

    private fun LocalTranslation.isMuyassar(): Boolean {
      val fields = listOfNotNull(filename, name, translator, translatorForeign)
      return fields.any { field ->
        val lower = field.lowercase()
        "muyassar" in lower || "moyassar" in lower || "الميسر" in field
      }
    }

    private fun LocalTranslation.isArabicTafsir(): Boolean {
      val fields = listOfNotNull(filename, name, translator, translatorForeign)
      return fields.any { field ->
        val lower = field.lowercase()
        "تفسير" in field || (("tafsir" in lower || "tafseer" in lower) && isArabic())
      }
    }

    private fun LocalTranslation.isArabic(): Boolean {
      return languageCode.equals("ar", ignoreCase = true) ||
        filename.startsWith("ar.", ignoreCase = true) ||
        hasArabicScript(name) ||
        hasArabicScript(translatorForeign)
    }

    private fun LocalTranslation.displayTitle(): String {
      localizedArabicTafsirTitle(
        filename = filename,
        name = name,
        translator = translator,
        translatorForeign = translatorForeign,
        languageCode = languageCode
      )?.let { return it }
      return when {
        name.isNotBlank() -> name
        !translatorForeign.isNullOrBlank() -> translatorForeign!!
        !translator.isNullOrBlank() -> translator!!
        else -> filename
      }
    }

    private fun LocalTranslation.toTafsirSourceItem(): TafsirSourceItem {
      return TafsirSourceItem(
        filename = filename,
        displayName = displayTitle(),
        isArabic = isArabic()
      )
    }
  }

  private data class CacheKey(val filename: String, val sura: Int, val ayah: Int)
  private data class CachedTafsirEntry(val text: String)
}
