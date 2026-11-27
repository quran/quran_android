package com.quran.labs.androidquran.memorization.tasmee

import android.content.Context
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.kidsclassroom.KidsAsrAssetDownloader
import com.quran.labs.androidquran.kidsclassroom.MinshawiJuz30Repository
import com.quran.labs.androidquran.kidsclassroom.QuranRecitationGrader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-Quran ASR reference repository (`6,236` Ayat across Surahs `1..114`) backed by `quran.json`
 * and enriched with `qari_onnx_words` from `minshawi_teacher_juz30.json` where available.
 */
data class AyahAsrReference(
  val suraAyah: SuraAyah,
  val uthmaniWords: List<String>,
  val normalizedWords: List<String>,
  val qariOnnxWords: List<String>,
  val rasmWords: List<String>,
  val qariRasmWords: List<String>,
  val wordTokenIds: List<IntArray>
) {
  val wordCount: Int
    get() = normalizedWords.size
}

/**
 * Represents the slice of an [AyahAsrReference] printed on a specific Mushaf page, mapped 1-to-1
 * onto the ordered `WordGlyph` positions on that page (handling both single-page Ayat and
 * multi-page split Ayat seamlessly).
 */
data class PageAyahTasmeeSlice(
  val page: Int,
  val suraAyah: SuraAyah,
  val uthmaniWords: List<String>,
  val normalizedWords: List<String>,
  val qariOnnxWords: List<String>,
  val rasmWords: List<String>,
  val qariRasmWords: List<String>,
  val wordTokenIds: List<IntArray>,
  val glyphPositionsByWordIdx: List<List<Int>>
) {
  val wordCount: Int
    get() = normalizedWords.size
}

object QuranAsrReferenceRepository {

  private data class RawVerseEntry(
    val surah: Int,
    val ayah: Int,
    val uthmaniWords: List<String>,
    val normalizedWords: List<String>,
    val tokenIds: IntArray
  )

  private val loadMutex = Mutex()

  @Volatile
  private var rawVersesByKey: Map<String, RawVerseEntry> = emptyMap()

  private val resolvedCache = HashMap<String, AyahAsrReference>(256)

  fun isLoaded(): Boolean = rawVersesByKey.isNotEmpty() || MinshawiJuz30Repository.isFullDatasetLoaded()

  suspend fun ensureLoaded(context: Context): Boolean = withContext(Dispatchers.IO) {
    val appCtx = context.applicationContext
    if (isLoaded()) {
      if (MinshawiJuz30Repository.reloadIfFullDatasetAvailable(appCtx)) {
        synchronized(resolvedCache) { resolvedCache.clear() }
      }
      return@withContext true
    }
    loadMutex.withLock {
      MinshawiJuz30Repository.ensureLoaded(appCtx)
      if (MinshawiJuz30Repository.reloadIfFullDatasetAvailable(appCtx)) {
        synchronized(resolvedCache) { resolvedCache.clear() }
      }
      if (MinshawiJuz30Repository.isFullDatasetLoaded()) {
        return@withContext true
      }

      val quranFile = KidsAsrAssetDownloader.getQuranJsonFile(appCtx)
      if (!quranFile.exists() || quranFile.length() <= 0L) {
        return@withContext MinshawiJuz30Repository.isFullDatasetLoaded()
      }
      try {
        val jsonText = quranFile.readText(Charsets.UTF_8)
        parseQuranJson(jsonText)
        rawVersesByKey.isNotEmpty()
      } catch (e: Exception) {
        Timber.e(e, "Failed to parse quran.json for Live Tasmee'")
        MinshawiJuz30Repository.isFullDatasetLoaded()
      }
    }
  }

  internal fun parseQuranJson(jsonText: String) {
    val root = JSONObject(jsonText)
    val versesArr = root.optJSONArray("verses") ?: return
    val map = HashMap<String, RawVerseEntry>(versesArr.length() + 16)

    for (i in 0 until versesArr.length()) {
      val obj = versesArr.optJSONObject(i) ?: continue
      val surah = obj.optInt("surah", 0)
      val ayah = obj.optInt("ayah", 0)
      if (surah <= 0 || ayah <= 0) continue

      val rawUthmani = obj.optString("uthmani", "").replace("\ufeff", "").trim()
      val rawNormalized = obj.optString("normalized", "").replace("\ufeff", "").trim()

      val normWords = splitNormalizedWords(rawNormalized)
      if (normWords.isEmpty()) continue
      val uthWords = alignUthmaniTokensToWordCount(rawUthmani, normWords)

      val tokArr = obj.optJSONArray("token_ids")
      val tokenIds = if (tokArr != null) {
        IntArray(tokArr.length()) { idx -> tokArr.optInt(idx) }
      } else {
        IntArray(0)
      }

      map["$surah:$ayah"] = RawVerseEntry(
        surah = surah,
        ayah = ayah,
        uthmaniWords = uthWords,
        normalizedWords = normWords,
        tokenIds = tokenIds
      )
    }

    synchronized(resolvedCache) {
      resolvedCache.clear()
    }
    rawVersesByKey = map
  }

  /**
   * Returns the resolved [AyahAsrReference] for [suraAyah], with per-word BPE `token_ids`
   * grouped at SentencePiece word-start (`\u2581`) boundaries.
   */
  fun getAyahReference(
    context: Context,
    suraAyah: SuraAyah,
    vocab: Array<String>?
  ): AyahAsrReference? {
    val key = "${suraAyah.sura}:${suraAyah.ayah}"
    if (vocab != null) {
      synchronized(resolvedCache) {
        resolvedCache[key]?.let { return it }
      }
    }

    val teacherSpec = MinshawiJuz30Repository.getVerse(context, suraAyah)

    val rawEntry = rawVersesByKey[key]
    val resolved: AyahAsrReference = when {
      rawEntry != null -> {
        val normWords = rawEntry.normalizedWords
        val uthWords = rawEntry.uthmaniWords
        val teacherQariWords = teacherSpec?.allQariOnnxWords.orEmpty()
        val qariWords = if (teacherQariWords.size == normWords.size) {
          teacherQariWords
        } else {
          normWords
        }
        val perWordTokens = groupVerseTokenIdsByWord(
          tokenIds = rawEntry.tokenIds,
          wordCount = normWords.size,
          vocab = vocab
        )
        AyahAsrReference(
          suraAyah = suraAyah,
          uthmaniWords = uthWords,
          normalizedWords = normWords,
          qariOnnxWords = qariWords,
          rasmWords = normWords.map { QuranRecitationGrader.phoneticRasm(it) },
          qariRasmWords = qariWords.map { QuranRecitationGrader.phoneticRasm(it) },
          wordTokenIds = perWordTokens
        )
      }

      teacherSpec != null -> {
        val uthWords = teacherSpec.allUthmaniWords
        val normWords = teacherSpec.allNormalizedWords
        val qariWords = teacherSpec.allQariOnnxWords
        val flatTokens = teacherSpec.allTokenIds
        val perWordTokens = groupVerseTokenIdsByWord(
          tokenIds = flatTokens,
          wordCount = normWords.size,
          vocab = vocab
        )
        if (normWords.isEmpty()) return null
        AyahAsrReference(
          suraAyah = suraAyah,
          uthmaniWords = if (uthWords.size == normWords.size) uthWords else normWords,
          normalizedWords = normWords,
          qariOnnxWords = qariWords,
          rasmWords = normWords.map { QuranRecitationGrader.phoneticRasm(it) },
          qariRasmWords = qariWords.map { QuranRecitationGrader.phoneticRasm(it) },
          wordTokenIds = perWordTokens
        )
      }

      else -> return null
    }

    if (vocab != null) {
      synchronized(resolvedCache) {
        resolvedCache[key] = resolved
      }
    }
    return resolved
  }

  /**
   * Maps an [AyahAsrReference] onto the ordered 1-based Mushaf `WordGlyph` positions present on [page].
   * Handles both standard single-page Ayat and multi-page Ayat cleanly.
   */
  fun buildPageAyahSlice(
    page: Int,
    ayahRef: AyahAsrReference,
    pageGlyphPositions: List<Int>,
    totalAyahGlyphCount: Int
  ): PageAyahTasmeeSlice? {
    if (ayahRef.wordCount == 0 || pageGlyphPositions.isEmpty()) return null
    val sortedGlyphs = pageGlyphPositions.distinct().sorted()
    val effectiveTotalGlyphs = max(totalAyahGlyphCount, sortedGlyphs.last()).coerceAtLeast(1)

    val isFullAyahOnPage = sortedGlyphs.first() == 1 && sortedGlyphs.size >= effectiveTotalGlyphs
    val startWordIdx: Int
    val endWordExclusive: Int

    if (isFullAyahOnPage) {
      startWordIdx = 0
      endWordExclusive = ayahRef.wordCount
    } else {
      startWordIdx = (((sortedGlyphs.first() - 1).toDouble() / effectiveTotalGlyphs.toDouble()) *
        ayahRef.wordCount.toDouble()).roundToInt().coerceIn(0, ayahRef.wordCount - 1)
      endWordExclusive = ((sortedGlyphs.last().toDouble() / effectiveTotalGlyphs.toDouble()) *
        ayahRef.wordCount.toDouble()).roundToInt().coerceIn(startWordIdx + 1, ayahRef.wordCount)
    }

    val sliceCount = (endWordExclusive - startWordIdx).coerceAtLeast(1)
    val glyphGroups = mapWordsToGlyphPositions(sliceCount, sortedGlyphs)

    return PageAyahTasmeeSlice(
      page = page,
      suraAyah = ayahRef.suraAyah,
      uthmaniWords = ayahRef.uthmaniWords.subList(startWordIdx, endWordExclusive),
      normalizedWords = ayahRef.normalizedWords.subList(startWordIdx, endWordExclusive),
      qariOnnxWords = ayahRef.qariOnnxWords.subList(startWordIdx, endWordExclusive),
      rasmWords = ayahRef.rasmWords.subList(startWordIdx, endWordExclusive),
      qariRasmWords = ayahRef.qariRasmWords.subList(startWordIdx, endWordExclusive),
      wordTokenIds = ayahRef.wordTokenIds.subList(startWordIdx, endWordExclusive),
      glyphPositionsByWordIdx = glyphGroups
    )
  }

  private fun mapWordsToGlyphPositions(
    wordCount: Int,
    sortedGlyphPositions: List<Int>
  ): List<List<Int>> {
    if (wordCount <= 0) return emptyList()
    if (sortedGlyphPositions.isEmpty()) {
      return List(wordCount) { idx -> listOf(idx + 1) }
    }
    if (wordCount == sortedGlyphPositions.size) {
      return sortedGlyphPositions.map { listOf(it) }
    }

    val gSize = sortedGlyphPositions.size
    return List(wordCount) { wIdx ->
      val gStart = ((wIdx.toDouble() * gSize.toDouble()) / wordCount.toDouble())
        .toInt()
        .coerceIn(0, gSize - 1)
      val gEndExclusive = (((wIdx + 1).toDouble() * gSize.toDouble()) / wordCount.toDouble())
        .roundToInt()
        .coerceIn(gStart + 1, gSize)
      sortedGlyphPositions.subList(gStart, gEndExclusive)
    }
  }

  internal fun groupVerseTokenIdsByWord(
    tokenIds: IntArray,
    wordCount: Int,
    vocab: Array<String>?
  ): List<IntArray> {
    if (wordCount <= 0) return emptyList()
    if (tokenIds.isEmpty() || vocab == null) {
      return List(wordCount) { IntArray(0) }
    }

    val rawGroups = ArrayList<MutableList<Int>>(wordCount + 2)
    for (tid in tokenIds) {
      val tok = if (tid in vocab.indices) vocab[tid] else ""
      if (tok.startsWith('\u2581') || rawGroups.isEmpty()) {
        rawGroups.add(mutableListOf(tid))
      } else {
        rawGroups.last().add(tid)
      }
    }

    if (rawGroups.isEmpty()) {
      return List(wordCount) { IntArray(0) }
    }
    if (rawGroups.size == wordCount) {
      return rawGroups.map { it.toIntArray() }
    }

    // Proportionally map SentencePiece word groups when a compound Uthmani token differs by 1
    val gCount = rawGroups.size
    return List(wordCount) { wIdx ->
      val startG = ((wIdx.toDouble() * gCount.toDouble()) / wordCount.toDouble())
        .toInt()
        .coerceIn(0, gCount - 1)
      val endG = min(
        gCount,
        max(startG + 1, (((wIdx + 1).toDouble() * gCount.toDouble()) / wordCount.toDouble()).roundToInt())
      )
      val combined = ArrayList<Int>(4)
      for (g in startG until endG) {
        combined.addAll(rawGroups[g])
      }
      combined.toIntArray()
    }
  }

  private fun splitNormalizedWords(normalized: String): List<String> {
    if (normalized.isBlank()) return emptyList()
    return normalized
      .split(Regex("\\s+"))
      .map { it.trim() }
      .filter { it.isNotEmpty() && containsArabicLetter(it) }
  }

  private fun alignUthmaniTokensToWordCount(
    rawUthmani: String,
    normalizedWords: List<String>
  ): List<String> {
    if (rawUthmani.isBlank()) return normalizedWords
    val rawParts = rawUthmani.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val merged = ArrayList<String>(rawParts.size)
    for (part in rawParts) {
      if (containsArabicLetter(part) || merged.isEmpty()) {
        merged.add(part)
      } else {
        // Attach standalone Quranic waqf/ornament mark onto the preceding word
        merged[merged.lastIndex] = merged.last() + " " + part
      }
    }
    return if (merged.size == normalizedWords.size) merged else normalizedWords
  }

  private fun containsArabicLetter(token: String): Boolean {
    for (ch in token) {
      if (ch in '\u0621'..'\u064A' || ch == '\u0671' || ch == '\u0670') {
        return true
      }
    }
    return false
  }
}
