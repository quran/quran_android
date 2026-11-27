package com.quran.labs.androidquran.memorization

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.data.core.QuranInfo
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.data.model.SuraAyahIterator
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

enum class RevealMode {
  FULL_AYAH,
  WORD_BY_WORD,
  WORD_WINDOW,
  SINGLE_WORD,
  SINGLE_WORD_TAJWEED;

  val isSingleActiveWordMode: Boolean
    get() = this == SINGLE_WORD || this == SINGLE_WORD_TAJWEED
}

data class FadingWord(
  val suraAyah: SuraAyah,
  val wordPosition: Int
) {
  fun key(): String = "${suraAyah.sura}:${suraAyah.ayah}:$wordPosition"
}

data class RevealTransition(
  val id: Long,
  val page: Int,
  val suraAyah: SuraAyah,
  val wordPositions: Set<Int>,
  val isFullAyah: Boolean,
  val fadingOutWords: Set<FadingWord> = emptySet(),
  val startTimeMs: Long = System.currentTimeMillis()
)

data class PageMemorizationProgress(
  val page: Int = 1,
  val totalAyahs: Int = 0,
  val revealedAyahs: Int = 0,
  val totalWords: Int = 0,
  val revealedWords: Int = 0
) {
  val isPageFullyRevealed: Boolean
    get() = totalWords > 0 && revealedWords >= totalWords
}

data class MemorizationRange(
  val startAyah: SuraAyah,
  val endAyah: SuraAyah,
  val startPage: Int,
  val endPage: Int,
  val targetRepetitions: Int = QuranMemorizationManager.REPEAT_UNLIMITED,
  val completedRepetitions: Int = 0
)

sealed interface MemorizationTapOutcome {
  data object NotInHiddenMode : MemorizationTapOutcome
  data class Revealed(val transition: RevealTransition) : MemorizationTapOutcome
  data class TurnToNextPage(val nextPage: Int) : MemorizationTapOutcome
  data class LoopBackToRangeStart(
    val targetPage: Int,
    val completedRepetitions: Int,
    val targetRepetitions: Int
  ) : MemorizationTapOutcome
  data class RangeRepetitionsCompleted(
    val endPage: Int,
    val completedRepetitions: Int
  ) : MemorizationTapOutcome
  data class EndOfPageReached(val page: Int) : MemorizationTapOutcome
}

data class MemorizationUiState(
  val isHiddenModeEnabled: Boolean = false,
  val revealMode: RevealMode = RevealMode.FULL_AYAH,
  val previousAyahWindowWords: Int = QuranMemorizationManager.WINDOW_DEFAULT_WORDS,
  val activeRange: MemorizationRange? = null,
  val currentPage: Int = 1,
  val pageProgress: PageMemorizationProgress = PageMemorizationProgress(),
  val lastTransition: RevealTransition? = null,
  val version: Long = 0L
)

/**
 * Central state manager for hiding Quran text and revealing Full Ayah or Word by Word in-place.
 *
 * Hidden words are rendered with the Mushaf paper background color while end-of-ayah
 * medallions (﴿١﴾) remain visible in place. When a word or full ayah is revealed, its text
 * color transitions in-place from the Mushaf paper background color to the visible text color.
 */
object QuranMemorizationManager {

  interface Listener {
    fun onMemorizationStateChanged(state: MemorizationUiState)
  }

  private val listeners = CopyOnWriteArraySet<Listener>()

  private val _uiState = MutableStateFlow(MemorizationUiState())
  val uiState: StateFlow<MemorizationUiState> = _uiState.asStateFlow()

  private var versionCounter = 1L
  private var transitionIdCounter = 1L

  // Per-page ordered list of ayahs
  private val pageAyahsMap = mutableMapOf<Int, List<SuraAyah>>()

  // Per-page and per-ayah ordered list of 1-based word positions on that page
  private val pageWordPositionsMap = mutableMapOf<Int, MutableMap<SuraAyah, List<Int>>>()

  // Per-page and per-ayah set of revealed 1-based word positions
  private val revealedWordsMap = mutableMapOf<Int, MutableMap<SuraAyah, MutableSet<Int>>>()

  // Tracks the single most recently pressed word per page for RevealMode.SINGLE_WORD
  private val lastRevealedWordByPage = mutableMapOf<Int, FadingWord>()

  // Tracks words skipped via Skip-Ahead Recovery in Live Tasmee' mode so a subtle hint dot can be drawn
  private val skippedHintWordsByPage = mutableMapOf<Int, MutableSet<FadingWord>>()

  const val WINDOW_NONE = 0
  const val WINDOW_DEFAULT_WORDS = 4
  const val WINDOW_FULL_AYAH = -1
  const val PREVIOUS_AYAH_WINDOW_WORDS = WINDOW_DEFAULT_WORDS
  const val REPEAT_UNLIMITED = -1

  val WORD_WINDOW_OPTIONS: List<Int> = listOf(
    WINDOW_NONE, 1, 2, 3, 4, 5, 6, 8, 10, WINDOW_FULL_AYAH
  )

  val REPETITION_OPTIONS: List<Int> = listOf(
    1, 2, 3, 5, 10, REPEAT_UNLIMITED
  )

  private const val PREFS_NAME = "quran_memorization_prefs"
  private const val KEY_GLOBAL_HIDDEN_MODE = "global_hidden_mode"
  private const val KEY_REVEAL_MODE = "reveal_mode"
  private const val KEY_PREVIOUS_AYAH_WINDOW_WORDS = "previous_ayah_window_words"

  // Cache of word counts per SuraAyah read from assets/word_alignment.db
  private val globalAyahWordCountCache = mutableMapOf<SuraAyah, Int>()
  private var wordAlignmentDbInitialized = false
  private var wordAlignmentDb: SQLiteDatabase? = null
  private var appContextRef: Context? = null
  private var prefsLoaded = false

  @JvmStatic
  fun isHiddenModeEnabled(): Boolean = _uiState.value.isHiddenModeEnabled

  @JvmStatic
  fun getRevealMode(): RevealMode = _uiState.value.revealMode

  @JvmStatic
  fun getPreviousAyahWindowWords(): Int = _uiState.value.previousAyahWindowWords

  @JvmStatic
  fun getActiveRange(): MemorizationRange? = _uiState.value.activeRange

  @Synchronized
  fun ensurePreferencesLoaded(context: Context?) {
    if (context == null) return
    val appCtx = context.applicationContext ?: context
    appContextRef = appCtx
    if (prefsLoaded) return
    prefsLoaded = true
    try {
      val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      if (prefs.contains(KEY_GLOBAL_HIDDEN_MODE) ||
        prefs.contains(KEY_REVEAL_MODE) ||
        prefs.contains(KEY_PREVIOUS_AYAH_WINDOW_WORDS)
      ) {
        val savedHidden = prefs.getBoolean(KEY_GLOBAL_HIDDEN_MODE, _uiState.value.isHiddenModeEnabled)
        val savedModeName = prefs.getString(KEY_REVEAL_MODE, _uiState.value.revealMode.name)
        val savedMode = RevealMode.entries.firstOrNull { it.name == savedModeName }
          ?: _uiState.value.revealMode
        val rawWindow = prefs.getInt(KEY_PREVIOUS_AYAH_WINDOW_WORDS, _uiState.value.previousAyahWindowWords)
        val savedWindow = normalizeWindowWords(rawWindow)
        if (savedHidden != _uiState.value.isHiddenModeEnabled ||
          savedMode != _uiState.value.revealMode ||
          savedWindow != _uiState.value.previousAyahWindowWords
        ) {
          publishState(
            isHiddenModeEnabled = savedHidden,
            revealMode = savedMode,
            previousAyahWindowWords = savedWindow,
            lastTransition = null
          )
        }
      }
    } catch (_: Exception) {
      // Ignore if SharedPreferences unavailable
    }
  }

  private fun normalizeWindowWords(windowWords: Int): Int {
    return if (windowWords == WINDOW_FULL_AYAH) WINDOW_FULL_AYAH else windowWords.coerceAtLeast(WINDOW_NONE)
  }

  private fun persistPreferences(
    isHiddenModeEnabled: Boolean = _uiState.value.isHiddenModeEnabled,
    revealMode: RevealMode = _uiState.value.revealMode,
    previousAyahWindowWords: Int = _uiState.value.previousAyahWindowWords
  ) {
    val appCtx = appContextRef ?: return
    try {
      appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_GLOBAL_HIDDEN_MODE, isHiddenModeEnabled)
        .putString(KEY_REVEAL_MODE, revealMode.name)
        .putInt(KEY_PREVIOUS_AYAH_WINDOW_WORDS, previousAyahWindowWords)
        .apply()
    } catch (_: Exception) {
      // Ignore if SharedPreferences unavailable
    }
  }

  @JvmStatic
  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  @JvmStatic
  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  @Synchronized
  fun ensureWordAlignmentLoaded(context: Context) {
    if (wordAlignmentDbInitialized) return
    wordAlignmentDbInitialized = true
    try {
      val dbFile = File(context.cacheDir, "memorization_word_alignment.db")
      if (!dbFile.exists() || dbFile.length() == 0L) {
        context.assets.open("word_alignment.db").use { input ->
          dbFile.outputStream().use { output ->
            input.copyTo(output)
          }
        }
      }
      wordAlignmentDb = SQLiteDatabase.openDatabase(
        dbFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
      )
    } catch (_: Exception) {
      // Fallback word count estimation will be used if asset copy fails
    }
  }

  @Synchronized
  fun getWordCountForAyah(suraAyah: SuraAyah): Int {
    globalAyahWordCountCache[suraAyah]?.let { return it }
    val db = wordAlignmentDb
    if (db != null && db.isOpen) {
      try {
        db.rawQuery(
          "SELECT glyph_word_numbers FROM word_alignment WHERE sura = ? AND ayah = ?",
          arrayOf(suraAyah.sura.toString(), suraAyah.ayah.toString())
        ).use { cursor ->
          var maxWord = 0
          while (cursor.moveToNext()) {
            val raw = cursor.getString(0) ?: continue
            raw.split(",").forEach { part ->
              val num = part.trim().toIntOrNull() ?: 0
              if (num > maxWord) maxWord = num
            }
          }
          if (maxWord > 0) {
            globalAyahWordCountCache[suraAyah] = maxWord
            return maxWord
          }
        }
      } catch (_: Exception) {
        // ignore and use fallback
      }
    }
    val fallback = defaultWordCountForAyah(suraAyah)
    globalAyahWordCountCache[suraAyah] = fallback
    return fallback
  }

  private fun defaultWordCountForAyah(suraAyah: SuraAyah): Int {
    com.quran.labs.androidquran.kidsclassroom.MinshawiJuz30Repository
      .peekVerse(suraAyah)
      ?.allUthmaniWords
      ?.size
      ?.takeIf { it > 0 }
      ?.let { return it }

    if (suraAyah.sura == 1) {
      return when (suraAyah.ayah) {
        1 -> 4
        2 -> 4
        3 -> 2
        4 -> 3
        5 -> 4
        6 -> 3
        7 -> 9
        else -> 4
      }
    }
    return 6
  }

  @Synchronized
  fun ensurePageInitialized(page: Int, quranInfo: QuranInfo?, context: Context? = null) {
    if (context != null) {
      ensurePreferencesLoaded(context)
      ensureWordAlignmentLoaded(context)
    }
    if (!pageAyahsMap.containsKey(page) && quranInfo != null && quranInfo.isValidPage(page)) {
      val bounds = quranInfo.getPageBounds(page)
      val start = SuraAyah(bounds[0], bounds[1])
      val end = SuraAyah(bounds[2], bounds[3])
      val iterator = SuraAyahIterator(quranInfo, start, end)
      val ayahs = mutableListOf<SuraAyah>()
      while (iterator.next()) {
        ayahs.add(SuraAyah(iterator.sura, iterator.ayah))
      }
      pageAyahsMap[page] = ayahs
    }
    val ayahs = pageAyahsMap[page] ?: emptyList()
    val wordPosMap = pageWordPositionsMap.getOrPut(page) { mutableMapOf() }
    for (ayah in ayahs) {
      if (!wordPosMap.containsKey(ayah)) {
        val count = getWordCountForAyah(ayah).coerceAtLeast(1)
        wordPosMap[ayah] = (1..count).toList()
      }
    }
    revealedWordsMap.getOrPut(page) { mutableMapOf() }
  }

  @Synchronized
  fun registerPageGlyphs(
    page: Int,
    pageGlyphsCoords: PageGlyphsCoords?,
    ayahCoordinatesKeys: Set<String>? = null
  ) {
    val wordPosMap = pageWordPositionsMap.getOrPut(page) { mutableMapOf() }
    val orderedAyahs = mutableListOf<SuraAyah>()

    if (pageGlyphsCoords != null && pageGlyphsCoords.glyphsByAyah.isNotEmpty()) {
      for ((suraAyah, glyphCoordsList) in pageGlyphsCoords.glyphsByAyah) {
        orderedAyahs.add(suraAyah)
        val wordPositions = glyphCoordsList
          .mapNotNull { (it.glyph as? WordGlyph)?.wordPosition }
          .distinct()
          .sorted()
        if (wordPositions.isNotEmpty()) {
          wordPosMap[suraAyah] = wordPositions
          val maxPos = wordPositions.maxOrNull() ?: wordPositions.size
          globalAyahWordCountCache[suraAyah] = maxPos
        } else if (!wordPosMap.containsKey(suraAyah)) {
          val count = getWordCountForAyah(suraAyah).coerceAtLeast(1)
          wordPosMap[suraAyah] = (1..count).toList()
        }
      }
      pageAyahsMap[page] = orderedAyahs
    } else if (!ayahCoordinatesKeys.isNullOrEmpty()) {
      val parsedAyahs = ayahCoordinatesKeys.mapNotNull { key ->
        val parts = key.split(":")
        if (parts.size >= 2) {
          val s = parts[0].toIntOrNull()
          val a = parts[1].toIntOrNull()
          if (s != null && a != null) SuraAyah(s, a) else null
        } else null
      }.sorted()
      if (parsedAyahs.isNotEmpty()) {
        pageAyahsMap[page] = parsedAyahs
        for (ayah in parsedAyahs) {
          if (!wordPosMap.containsKey(ayah)) {
            val count = getWordCountForAyah(ayah).coerceAtLeast(1)
            wordPosMap[ayah] = (1..count).toList()
          }
        }
      }
    }
    revealedWordsMap.getOrPut(page) { mutableMapOf() }
    if (_uiState.value.currentPage == page || _uiState.value.isHiddenModeEnabled) {
      publishState(lastTransition = _uiState.value.lastTransition)
    }
  }

  @Synchronized
  fun registerAyahWordCountFromText(page: Int, suraAyah: SuraAyah, wordCount: Int) {
    if (wordCount <= 0) return
    globalAyahWordCountCache[suraAyah] = wordCount
    val wordPosMap = pageWordPositionsMap.getOrPut(page) { mutableMapOf() }
    val existing = wordPosMap[suraAyah]
    if (existing == null || existing.size != wordCount) {
      wordPosMap[suraAyah] = (1..wordCount).toList()
    }
    val ayahs = pageAyahsMap[page]?.toMutableList() ?: mutableListOf()
    if (suraAyah !in ayahs) {
      ayahs.add(suraAyah)
      ayahs.sort()
      pageAyahsMap[page] = ayahs
    }
  }

  @Synchronized
  fun setCurrentPage(page: Int, quranInfo: QuranInfo? = null, context: Context? = null) {
    ensurePageInitialized(page, quranInfo, context)
    if (_uiState.value.currentPage != page) {
      publishState(currentPage = page, lastTransition = null)
    } else {
      publishState(lastTransition = _uiState.value.lastTransition)
    }
  }

  @Synchronized
  fun toggleHiddenMode(page: Int = _uiState.value.currentPage, quranInfo: QuranInfo? = null, context: Context? = null) {
    ensurePageInitialized(page, quranInfo, context)
    val nextEnabled = !_uiState.value.isHiddenModeEnabled
    if (nextEnabled) {
      // Global hide: clear revealed words across ALL pages, not just the current page
      revealedWordsMap.clear()
      lastRevealedWordByPage.clear()
      skippedHintWordsByPage.clear()
      revealedWordsMap.getOrPut(page) { mutableMapOf() }
    } else {
      lastRevealedWordByPage.clear()
      skippedHintWordsByPage.clear()
    }
    val nextRange = if (nextEnabled) _uiState.value.activeRange else null
    persistPreferences(nextEnabled, _uiState.value.revealMode)
    publishState(
      isHiddenModeEnabled = nextEnabled,
      activeRange = nextRange,
      currentPage = page,
      lastTransition = null
    )
  }

  @Synchronized
  fun setHiddenModeEnabled(
    enabled: Boolean,
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ) {
    ensurePageInitialized(page, quranInfo, context)
    if (enabled) {
      // Global hide: clear revealed words across ALL pages
      revealedWordsMap.clear()
      lastRevealedWordByPage.clear()
      skippedHintWordsByPage.clear()
      revealedWordsMap.getOrPut(page) { mutableMapOf() }
    } else {
      lastRevealedWordByPage.clear()
      skippedHintWordsByPage.clear()
    }
    val nextRange = if (enabled) _uiState.value.activeRange else null
    persistPreferences(enabled, _uiState.value.revealMode)
    publishState(
      isHiddenModeEnabled = enabled,
      activeRange = nextRange,
      currentPage = page,
      lastTransition = null
    )
  }

  /**
   * Called when one of the prominent memorization mode buttons ("Reveal Full Ayah",
   * "Word by Word", "Word Window", or "Single Word") is pressed.
   * - If hidden mode is currently OFF, activates hidden mode globally across all pages in [mode].
   * - If hidden mode is already ON and in a different mode, switches to [mode].
   * - If hidden mode is already ON and already in [mode], reveals the next ayah or word on [page].
   */
  @Synchronized
  fun onProminentModeButtonPressed(
    mode: RevealMode,
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ) {
    ensurePageInitialized(page, quranInfo, context)
    val current = _uiState.value
    if (mode == RevealMode.SINGLE_WORD_TAJWEED && context != null) {
      try {
        com.quran.labs.androidquran.util.QuranSettings.getInstance(context).pageType = "tajweed"
      } catch (_: Exception) {
        // Ignore if settings unavailable in unit test environment
      }
    }
    if (!current.isHiddenModeEnabled) {
      revealedWordsMap.clear()
      lastRevealedWordByPage.clear()
      skippedHintWordsByPage.clear()
      revealedWordsMap.getOrPut(page) { mutableMapOf() }
      persistPreferences(true, mode)
      publishState(
        isHiddenModeEnabled = true,
        revealMode = mode,
        currentPage = page,
        lastTransition = null
      )
    } else if (current.revealMode != mode) {
      if (mode.isSingleActiveWordMode) {
        seedSpotlightFromLatestRevealedOnPage(page)
      }
      persistPreferences(true, mode)
      publishState(
        isHiddenModeEnabled = true,
        revealMode = mode,
        currentPage = page,
        lastTransition = null
      )
    } else {
      // Already in this active mode -> also advance reveal on the page
      revealNextOnPage(page, quranInfo, context)
    }
  }

  @Synchronized
  fun setRevealMode(mode: RevealMode, context: Context? = null) {
    if (context != null) {
      ensurePreferencesLoaded(context)
      if (mode == RevealMode.SINGLE_WORD_TAJWEED) {
        try {
          com.quran.labs.androidquran.util.QuranSettings.getInstance(context).pageType = "tajweed"
        } catch (_: Exception) {
          // Ignore if settings unavailable
        }
      }
    }
    persistPreferences(_uiState.value.isHiddenModeEnabled, mode)
    if (_uiState.value.revealMode != mode) {
      if (mode.isSingleActiveWordMode) {
        seedSpotlightFromLatestRevealedOnPage(_uiState.value.currentPage)
      }
      publishState(revealMode = mode, lastTransition = null)
    }
  }

  private fun seedSpotlightFromLatestRevealedOnPage(page: Int) {
    if (lastRevealedWordByPage.containsKey(page)) return
    val pageRevealed = revealedWordsMap[page] ?: return
    val ayahs = pageAyahsMap[page]?.takeIf { it.isNotEmpty() } ?: pageRevealed.keys.sorted()
    val latestAyah = ayahs.lastOrNull { !pageRevealed[it].isNullOrEmpty() } ?: return
    val latestWordPos = pageRevealed[latestAyah]?.maxOrNull() ?: return
    lastRevealedWordByPage[page] = FadingWord(latestAyah, latestWordPos)
  }

  @Synchronized
  fun setPreviousAyahWindowWords(windowWords: Int, context: Context? = null) {
    if (context != null) {
      ensurePreferencesLoaded(context)
    }
    val normalized = normalizeWindowWords(windowWords)
    persistPreferences(
      isHiddenModeEnabled = _uiState.value.isHiddenModeEnabled,
      revealMode = _uiState.value.revealMode,
      previousAyahWindowWords = normalized
    )
    if (_uiState.value.previousAyahWindowWords != normalized) {
      val currentPage = _uiState.value.currentPage
      val shouldAnimateWindowShrink =
        _uiState.value.isHiddenModeEnabled && _uiState.value.revealMode == RevealMode.WORD_WINDOW
      val previouslyVisible = if (shouldAnimateWindowShrink) {
        collectVisibleWordsOnPage(currentPage)
      } else {
        emptySet()
      }

      val nowVisible = if (shouldAnimateWindowShrink) {
        collectVisibleWordsOnPage(currentPage, windowWordsOverride = normalized)
      } else {
        emptySet()
      }
      val removedWords = previouslyVisible - nowVisible
      val shrinkTransition = if (removedWords.isNotEmpty()) {
        RevealTransition(
          id = transitionIdCounter++,
          page = currentPage,
          suraAyah = removedWords.first().suraAyah,
          wordPositions = emptySet(),
          isFullAyah = false,
          fadingOutWords = removedWords
        )
      } else {
        null
      }
      publishState(previousAyahWindowWords = normalized, lastTransition = shrinkTransition)
    }
  }

  private fun findRegisteredPageForAyah(ayah: SuraAyah): Int? {
    return pageAyahsMap.entries
      .sortedBy { it.key }
      .firstOrNull { ayah in it.value }
      ?.key
  }

  @Synchronized
  fun startRangeMemorization(
    startAyah: SuraAyah,
    endAyah: SuraAyah,
    targetRepetitions: Int = REPEAT_UNLIMITED,
    quranInfo: QuranInfo? = null,
    context: Context? = null,
    startPageOverride: Int? = null,
    endPageOverride: Int? = null
  ): MemorizationRange {
    if (context != null) {
      ensurePreferencesLoaded(context)
      ensureWordAlignmentLoaded(context)
    }
    val (orderedStart, orderedEnd) = if (startAyah <= endAyah) {
      startAyah to endAyah
    } else {
      endAyah to startAyah
    }
    val rawStartPage = startPageOverride
      ?: quranInfo?.getPageFromSuraAyah(orderedStart.sura, orderedStart.ayah)?.takeIf { it > 0 }
      ?: findRegisteredPageForAyah(orderedStart)
      ?: _uiState.value.currentPage
    val rawEndPage = endPageOverride
      ?: quranInfo?.getPageFromSuraAyah(orderedEnd.sura, orderedEnd.ayah)?.takeIf { it > 0 }
      ?: findRegisteredPageForAyah(orderedEnd)
      ?: rawStartPage

    val resolvedStartPage = minOf(rawStartPage, rawEndPage)
    val resolvedEndPage = maxOf(rawStartPage, rawEndPage)

    for (p in resolvedStartPage..resolvedEndPage) {
      ensurePageInitialized(p, quranInfo, context)
    }

    val normalizedRepetitions = if (targetRepetitions <= 0) {
      REPEAT_UNLIMITED
    } else {
      targetRepetitions
    }

    val range = MemorizationRange(
      startAyah = orderedStart,
      endAyah = orderedEnd,
      startPage = resolvedStartPage,
      endPage = resolvedEndPage,
      targetRepetitions = normalizedRepetitions,
      completedRepetitions = 0
    )

    revealedWordsMap.clear()
    lastRevealedWordByPage.clear()
    skippedHintWordsByPage.clear()
    revealedWordsMap.getOrPut(resolvedStartPage) { mutableMapOf() }
    persistPreferences(isHiddenModeEnabled = true, revealMode = _uiState.value.revealMode)
    publishState(
      isHiddenModeEnabled = true,
      activeRange = range,
      currentPage = resolvedStartPage,
      lastTransition = null
    )
    return range
  }

  @Synchronized
  fun clearMemorizationRange() {
    if (_uiState.value.activeRange != null) {
      publishState(activeRange = null, lastTransition = null)
    }
  }

  @Synchronized
  fun resetPage(
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ) {
    resetAllPages(page, quranInfo, context)
  }

  @Synchronized
  fun resetAllPages(
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ) {
    val targetPage = _uiState.value.activeRange?.startPage ?: page
    ensurePageInitialized(targetPage, quranInfo, context)
    revealedWordsMap.clear()
    lastRevealedWordByPage.clear()
    skippedHintWordsByPage.clear()
    revealedWordsMap.getOrPut(targetPage) { mutableMapOf() }
    val resetRange = _uiState.value.activeRange?.copy(completedRepetitions = 0)
    persistPreferences(true, _uiState.value.revealMode)
    publishState(
      isHiddenModeEnabled = true,
      activeRange = resetRange,
      currentPage = targetPage,
      lastTransition = null
    )
  }

  /**
   * Handles a user tap on [page] while in Memorization Mode, supporting:
   * - Starting directly at [MemorizationRange.startAyah] when a repetition range is active
   * - Turning forward across multi-page sequences
   * - Automatically flipping back to [MemorizationRange.startPage], re-hiding, and repeating
   *   for the configured number of repetitions (or unlimited)
   * - Turning to the next page once the final ayah is revealed in regular memorization mode
   */
  @Synchronized
  fun onMemorizationPageTap(
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null,
    targetAyah: SuraAyah? = null
  ): MemorizationTapOutcome {
    ensurePageInitialized(page, quranInfo, context)
    if (!_uiState.value.isHiddenModeEnabled) {
      return MemorizationTapOutcome.NotInHiddenMode
    }

    val range = _uiState.value.activeRange
    if (range != null) {
      if (page < range.startPage || page > range.endPage) {
        ensurePageInitialized(range.startPage, quranInfo, context)
        publishState(currentPage = range.startPage, lastTransition = null)
        return MemorizationTapOutcome.LoopBackToRangeStart(
          targetPage = range.startPage,
          completedRepetitions = range.completedRepetitions,
          targetRepetitions = range.targetRepetitions
        )
      }

      val transition = revealNextOnPage(page, quranInfo, context, targetAyah)
      if (transition != null) {
        return MemorizationTapOutcome.Revealed(transition)
      }

      if (page < range.endPage) {
        return MemorizationTapOutcome.TurnToNextPage(page + 1)
      }

      // All ayahs up to range.endAyah on range.endPage have been revealed -> repeat or finish!
      if (range.targetRepetitions == REPEAT_UNLIMITED ||
        range.completedRepetitions < range.targetRepetitions
      ) {
        val updatedRange = range.copy(completedRepetitions = range.completedRepetitions + 1)
        revealedWordsMap.clear()
        lastRevealedWordByPage.clear()
        skippedHintWordsByPage.clear()
        revealedWordsMap.getOrPut(range.startPage) { mutableMapOf() }
        publishState(
          isHiddenModeEnabled = true,
          activeRange = updatedRange,
          currentPage = range.startPage,
          lastTransition = null
        )
        return MemorizationTapOutcome.LoopBackToRangeStart(
          targetPage = range.startPage,
          completedRepetitions = updatedRange.completedRepetitions,
          targetRepetitions = updatedRange.targetRepetitions
        )
      } else {
        publishState(activeRange = null, lastTransition = null)
        return MemorizationTapOutcome.RangeRepetitionsCompleted(
          endPage = range.endPage,
          completedRepetitions = range.completedRepetitions
        )
      }
    }

    val transition = revealNextOnPage(page, quranInfo, context, targetAyah)
    if (transition != null) {
      return MemorizationTapOutcome.Revealed(transition)
    }

    if (isFinalAyahRevealed(page, quranInfo, context)) {
      val nextPage = page + 1
      return if (quranInfo == null || quranInfo.isValidPage(nextPage)) {
        MemorizationTapOutcome.TurnToNextPage(nextPage)
      } else {
        MemorizationTapOutcome.EndOfPageReached(page)
      }
    }

    return MemorizationTapOutcome.EndOfPageReached(page)
  }

  /**
   * Reveals either the next full ayah or the next word on [page] depending on [revealMode].
   * When an [activeRange] is active, restricts revealing strictly to `[startAyah..endAyah]`
   * so the first press reveals the first ayah/word in the user's range and stops at `endAyah`.
   */
  @Synchronized
  fun revealNextOnPage(
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null,
    targetAyah: SuraAyah? = null
  ): RevealTransition? {
    ensurePageInitialized(page, quranInfo, context)
    if (!_uiState.value.isHiddenModeEnabled) {
      return null
    }

    val allPageAyahs = pageAyahsMap[page].orEmpty()
    if (allPageAyahs.isEmpty()) return null

    val range = _uiState.value.activeRange
    val candidateAyahs = if (range != null) {
      allPageAyahs.filter { it >= range.startAyah && it <= range.endAyah }
    } else {
      allPageAyahs
    }
    if (candidateAyahs.isEmpty()) return null

    val wordPosMap = pageWordPositionsMap.getOrPut(page) { mutableMapOf() }
    val pageRevealed = revealedWordsMap.getOrPut(page) { mutableMapOf() }
    val mode = _uiState.value.revealMode

    val nextUnrevealedAyah = candidateAyahs.firstOrNull { ayah ->
      val allWords = wordPosMap[ayah] ?: (1..getWordCountForAyah(ayah).coerceAtLeast(1)).toList()
      val revealed = pageRevealed[ayah].orEmpty()
      revealed.size < allWords.size
    } ?: targetAyah?.takeIf { ayah ->
      (range == null || (ayah >= range.startAyah && ayah <= range.endAyah)) && run {
        val allWords = wordPosMap[ayah] ?: (1..getWordCountForAyah(ayah).coerceAtLeast(1)).toList()
        val revealed = pageRevealed[ayah].orEmpty()
        revealed.size < allWords.size
      }
    } ?: return null

    val allWordsForAyah = wordPosMap.getOrPut(nextUnrevealedAyah) {
      (1..getWordCountForAyah(nextUnrevealedAyah).coerceAtLeast(1)).toList()
    }
    val revealedForAyah = pageRevealed.getOrPut(nextUnrevealedAyah) { mutableSetOf() }

    val transition: RevealTransition = when (mode) {
      RevealMode.FULL_AYAH -> {
        val newlyRevealed = allWordsForAyah.filter { it !in revealedForAyah }.toSet()
        if (newlyRevealed.isEmpty()) return null
        revealedForAyah.addAll(newlyRevealed)
        lastRevealedWordByPage.remove(page)
        RevealTransition(
          id = transitionIdCounter++,
          page = page,
          suraAyah = nextUnrevealedAyah,
          wordPositions = newlyRevealed,
          isFullAyah = true
        )
      }

      RevealMode.WORD_BY_WORD -> {
        val nextWord = allWordsForAyah.firstOrNull { it !in revealedForAyah } ?: return null
        revealedForAyah.add(nextWord)
        lastRevealedWordByPage[page] = FadingWord(nextUnrevealedAyah, nextWord)
        RevealTransition(
          id = transitionIdCounter++,
          page = page,
          suraAyah = nextUnrevealedAyah,
          wordPositions = setOf(nextWord),
          isFullAyah = false
        )
      }

      RevealMode.WORD_WINDOW -> {
        val nextWord = allWordsForAyah.firstOrNull { it !in revealedForAyah } ?: return null
        val previouslyVisible = collectVisibleWordsOnPage(page)
        revealedForAyah.add(nextWord)
        lastRevealedWordByPage[page] = FadingWord(nextUnrevealedAyah, nextWord)
        val nowVisible = collectVisibleWordsOnPage(page)
        val removedFromWindow = previouslyVisible - nowVisible
        RevealTransition(
          id = transitionIdCounter++,
          page = page,
          suraAyah = nextUnrevealedAyah,
          wordPositions = setOf(nextWord),
          isFullAyah = false,
          fadingOutWords = removedFromWindow
        )
      }

      RevealMode.SINGLE_WORD,
      RevealMode.SINGLE_WORD_TAJWEED -> {
        val nextWord = allWordsForAyah.firstOrNull { it !in revealedForAyah } ?: return null
        revealedForAyah.add(nextWord)
        skippedHintWordsByPage[page]?.remove(FadingWord(nextUnrevealedAyah, nextWord))
        lastRevealedWordByPage[page] = FadingWord(nextUnrevealedAyah, nextWord)
        RevealTransition(
          id = transitionIdCounter++,
          page = page,
          suraAyah = nextUnrevealedAyah,
          wordPositions = setOf(nextWord),
          isFullAyah = false
        )
      }
    }

    publishState(
      currentPage = page,
      lastTransition = transition
    )
    return transition
  }

  private fun collectVisibleWordsOnPage(
    page: Int,
    windowWordsOverride: Int? = null
  ): Set<FadingWord> {
    val pageRevealed = revealedWordsMap[page] ?: return emptySet()
    val visible = LinkedHashSet<FadingWord>()
    for ((ayah, wordSet) in pageRevealed) {
      for (wordPos in wordSet) {
        if (isWordRevealedInternal(page, ayah, wordPos, windowWordsOverride)) {
          visible.add(FadingWord(ayah, wordPos))
        }
      }
    }
    return visible
  }

  @Synchronized
  fun isWordRevealed(page: Int, suraAyah: SuraAyah, wordPosition: Int): Boolean {
    return isWordRevealedInternal(page, suraAyah, wordPosition, windowWordsOverride = null)
  }

  private fun isWordRevealedInternal(
    page: Int,
    suraAyah: SuraAyah,
    wordPosition: Int,
    windowWordsOverride: Int?
  ): Boolean {
    if (!_uiState.value.isHiddenModeEnabled) return true
    val pageRevealed = revealedWordsMap[page] ?: return false
    val revealedForAyah = pageRevealed[suraAyah] ?: return false
    if (wordPosition !in revealedForAyah) return false

    val mode = _uiState.value.revealMode
    if (mode.isSingleActiveWordMode) {
      val singleWord = lastRevealedWordByPage[page] ?: return false
      return singleWord.suraAyah == suraAyah && singleWord.wordPosition == wordPosition
    }

    if (mode != RevealMode.WORD_WINDOW) {
      return true
    }

    // In WORD_WINDOW mode:
    // - Current active ayah shows its revealed words.
    // - Immediately preceding ayah shows:
    //   * 0 words when previousAyahWindowWords == WINDOW_NONE (0)
    //   * All revealed words when previousAyahWindowWords == WINDOW_FULL_AYAH (-1)
    //   * Only its last N words when previousAyahWindowWords == N > 0
    // - All earlier ayahs (2+ back) are hidden.
    val ayahs = pageAyahsMap[page]?.takeIf { it.isNotEmpty() }
      ?: pageRevealed.keys.sorted()
    val activeAyahIndex = ayahs.indexOfLast { !pageRevealed[it].isNullOrEmpty() }
    if (activeAyahIndex < 0) return false

    return when (ayahs.indexOf(suraAyah)) {
      activeAyahIndex -> true
      activeAyahIndex - 1 -> {
        when (val windowWords = windowWordsOverride ?: _uiState.value.previousAyahWindowWords) {
          WINDOW_NONE -> false
          WINDOW_FULL_AYAH -> true
          else -> {
            val allWords = pageWordPositionsMap[page]?.get(suraAyah)
              ?: (1..getWordCountForAyah(suraAyah).coerceAtLeast(1)).toList()
            val lastWindowWords = allWords.takeLast(windowWords.coerceAtLeast(0))
            wordPosition in lastWindowWords
          }
        }
      }
      else -> false
    }
  }

  @Synchronized
  fun isFinalAyahRevealed(
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ): Boolean {
    ensurePageInitialized(page, quranInfo, context)
    if (!_uiState.value.isHiddenModeEnabled) return false
    val ayahs = pageAyahsMap[page].orEmpty()
    if (ayahs.isEmpty()) return false
    return getPageProgress(page).isPageFullyRevealed
  }

  @Synchronized
  fun isAyahFullyRevealed(page: Int, suraAyah: SuraAyah): Boolean {
    if (!_uiState.value.isHiddenModeEnabled) return true
    val allWords = pageWordPositionsMap[page]?.get(suraAyah)
      ?: (1..getWordCountForAyah(suraAyah).coerceAtLeast(1)).toList()
    val revealed = revealedWordsMap[page]?.get(suraAyah) ?: return false
    return allWords.isNotEmpty() && revealed.containsAll(allWords)
  }

  @Synchronized
  fun getAyahsOnPage(page: Int): List<SuraAyah> {
    return pageAyahsMap[page].orEmpty()
  }

  @Synchronized
  fun getWordPositionsForAyah(page: Int, suraAyah: SuraAyah): List<Int> {
    return pageWordPositionsMap[page]?.get(suraAyah)
      ?: (1..getWordCountForAyah(suraAyah).coerceAtLeast(1)).toList()
  }

  @Synchronized
  fun getCandidateAyahsOnPage(page: Int): List<SuraAyah> {
    val allAyahs = pageAyahsMap[page].orEmpty()
    val range = _uiState.value.activeRange
    return if (range != null) {
      allAyahs.filter { it >= range.startAyah && it <= range.endAyah }
    } else {
      allAyahs
    }
  }

  @Synchronized
  fun getRevealedWordPositionsForAyah(page: Int, suraAyah: SuraAyah): Set<Int> {
    return revealedWordsMap[page]?.get(suraAyah)?.toSet().orEmpty()
  }

  @Synchronized
  fun isWordSkippedHint(page: Int, suraAyah: SuraAyah, wordPosition: Int): Boolean {
    if (!_uiState.value.isHiddenModeEnabled) return false
    val pageHints = skippedHintWordsByPage[page] ?: return false
    return FadingWord(suraAyah, wordPosition) in pageHints
  }

  /**
   * Prepares Memorization Mode for Live Recitation Follow-Along ("Tasmee' / تسميع")
   * supporting **Word by Word** ([RevealMode.WORD_BY_WORD]) by default as well as
   * [RevealMode.SINGLE_WORD], [RevealMode.WORD_WINDOW], and [RevealMode.SINGLE_WORD_TAJWEED]:
   * - If hidden mode is OFF, enables hidden mode in [RevealMode.WORD_BY_WORD] (or the user's saved
   *   word-level mode if not [RevealMode.FULL_AYAH]).
   * - If hidden mode is already ON in [RevealMode.FULL_AYAH], upgrades to [RevealMode.WORD_BY_WORD]
   *   so spoken words reveal progressively one by one.
   * - If hidden mode is already ON in [RevealMode.WORD_BY_WORD], [RevealMode.WORD_WINDOW],
   *   [RevealMode.SINGLE_WORD], or [RevealMode.SINGLE_WORD_TAJWEED], preserves that exact mode!
   */
  @Synchronized
  fun startTasmeeSession(
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ) {
    ensurePageInitialized(page, quranInfo, context)
    val current = _uiState.value
    if (!current.isHiddenModeEnabled) {
      val initialMode = RevealMode.WORD_BY_WORD
      revealedWordsMap.clear()
      lastRevealedWordByPage.clear()
      skippedHintWordsByPage.clear()
      revealedWordsMap.getOrPut(page) { mutableMapOf() }
      persistPreferences(true, initialMode)
      publishState(
        isHiddenModeEnabled = true,
        revealMode = initialMode,
        currentPage = page,
        lastTransition = null
      )
    } else if (current.revealMode == RevealMode.FULL_AYAH) {
      persistPreferences(true, RevealMode.WORD_BY_WORD)
      publishState(
        isHiddenModeEnabled = true,
        revealMode = RevealMode.WORD_BY_WORD,
        currentPage = page,
        lastTransition = null
      )
    } else {
      if (current.revealMode.isSingleActiveWordMode) {
        seedSpotlightFromLatestRevealedOnPage(page)
      }
      publishState(currentPage = page, lastTransition = null)
    }
  }

  /**
   * Prepares Memorization Mode for Live Recitation Follow-Along ("Tasmee' / تسميع")
   * explicitly using One Word Mode ([RevealMode.SINGLE_WORD]).
   */
  @Synchronized
  fun startTasmeeOneWordSession(
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ) {
    ensurePageInitialized(page, quranInfo, context)
    val current = _uiState.value
    if (!current.isHiddenModeEnabled) {
      revealedWordsMap.clear()
      lastRevealedWordByPage.clear()
      skippedHintWordsByPage.clear()
      revealedWordsMap.getOrPut(page) { mutableMapOf() }
      persistPreferences(true, RevealMode.SINGLE_WORD)
      publishState(
        isHiddenModeEnabled = true,
        revealMode = RevealMode.SINGLE_WORD,
        currentPage = page,
        lastTransition = null
      )
    } else if (current.revealMode != RevealMode.SINGLE_WORD) {
      seedSpotlightFromLatestRevealedOnPage(page)
      persistPreferences(true, RevealMode.SINGLE_WORD)
      publishState(
        isHiddenModeEnabled = true,
        revealMode = RevealMode.SINGLE_WORD,
        currentPage = page,
        lastTransition = null
      )
    } else {
      publishState(currentPage = page, lastTransition = null)
    }
  }

  /**
   * Monotonically unlocks words on [page] for [suraAyah] during Live Tasmee' streaming:
   * - Latch-on state: never removes previously revealed words.
   * - Updates [lastRevealedWordByPage] to the latest spoken word (for One Word Mode) so only
   *   the active spoken word is shown while skipped words receive a subtle hint dot via
   *   [skippedHintWordsByPage].
   */
  @Synchronized
  fun advanceTasmeeMonotonicCursor(
    page: Int,
    suraAyah: SuraAyah,
    unlockedGlyphPositions: List<Int>,
    skippedGlyphPositions: Set<Int> = emptySet(),
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ): RevealTransition? {
    ensurePageInitialized(page, quranInfo, context)
    if (!_uiState.value.isHiddenModeEnabled || unlockedGlyphPositions.isEmpty()) {
      return null
    }

    val pageRevealed = revealedWordsMap.getOrPut(page) { mutableMapOf() }
    val revealedForAyah = pageRevealed.getOrPut(suraAyah) { mutableSetOf() }
    val mode = _uiState.value.revealMode

    val previouslyVisible = if (mode == RevealMode.WORD_WINDOW) {
      collectVisibleWordsOnPage(page)
    } else {
      emptySet()
    }

    val newlyAdded = LinkedHashSet<Int>()
    for (pos in unlockedGlyphPositions) {
      if (revealedForAyah.add(pos)) {
        newlyAdded.add(pos)
      }
    }

    if (skippedGlyphPositions.isNotEmpty()) {
      val hintSet = skippedHintWordsByPage.getOrPut(page) { mutableSetOf() }
      for (skipPos in skippedGlyphPositions) {
        hintSet.add(FadingWord(suraAyah, skipPos))
      }
    }

    val latestSpokenGlyph = unlockedGlyphPositions
      .filter { it !in skippedGlyphPositions }
      .maxOrNull()
      ?: unlockedGlyphPositions.maxOrNull()
      ?: return null

    // Clear hint dot on the actively spoken word itself
    skippedHintWordsByPage[page]?.remove(FadingWord(suraAyah, latestSpokenGlyph))

    val prevSpotlight = lastRevealedWordByPage[page]
    lastRevealedWordByPage[page] = FadingWord(suraAyah, latestSpokenGlyph)

    if (newlyAdded.isEmpty() && prevSpotlight == lastRevealedWordByPage[page]) {
      return null
    }

    val removedFromWindow = if (mode == RevealMode.WORD_WINDOW) {
      previouslyVisible - collectVisibleWordsOnPage(page)
    } else {
      emptySet()
    }

    val transitionWords = if (mode.isSingleActiveWordMode) {
      setOf(latestSpokenGlyph)
    } else {
      newlyAdded.ifEmpty { setOf(latestSpokenGlyph) }
    }

    val transition = RevealTransition(
      id = transitionIdCounter++,
      page = page,
      suraAyah = suraAyah,
      wordPositions = transitionWords,
      isFullAyah = false,
      fadingOutWords = removedFromWindow
    )

    publishState(
      currentPage = page,
      lastTransition = transition
    )
    return transition
  }

  /**
   * Reveals a specific set of 1-based [wordPositions] for [suraAyah] on [page] (used by Kids Mode
   * Memorization when grading a turn or advancing live words) while keeping uncompleted words hidden.
   */
  @Synchronized
  fun revealWordRangeForAyah(
    suraAyah: SuraAyah,
    wordPositions: Collection<Int>,
    page: Int = _uiState.value.currentPage,
    quranInfo: QuranInfo? = null,
    context: Context? = null
  ): RevealTransition? {
    ensurePageInitialized(page, quranInfo, context)
    if (!_uiState.value.isHiddenModeEnabled || wordPositions.isEmpty()) {
      return null
    }
    val pageRevealed = revealedWordsMap.getOrPut(page) { mutableMapOf() }
    val revealedForAyah = pageRevealed.getOrPut(suraAyah) { mutableSetOf() }
    val newlyAdded = LinkedHashSet<Int>()
    for (pos in wordPositions) {
      if (pos >= 1 && revealedForAyah.add(pos)) {
        newlyAdded.add(pos)
      }
    }
    val lastPos = wordPositions.maxOrNull() ?: return null
    lastRevealedWordByPage[page] = FadingWord(suraAyah, lastPos)
    if (newlyAdded.isEmpty()) {
      publishState(currentPage = page, lastTransition = null)
      return null
    }
    val transition = RevealTransition(
      id = transitionIdCounter++,
      page = page,
      suraAyah = suraAyah,
      wordPositions = newlyAdded,
      isFullAyah = false
    )
    publishState(currentPage = page, lastTransition = transition)
    return transition
  }

  /**
   * Re-hides a specific set of 1-based [wordPositions] for [suraAyah] on [page] (used by Kids Mode
   * Memorization when starting or retrying a turn from memory).
   */
  @Synchronized
  fun hideWordRangeForAyah(
    suraAyah: SuraAyah,
    wordPositions: Collection<Int>,
    page: Int = _uiState.value.currentPage
  ) {
    if (!_uiState.value.isHiddenModeEnabled || wordPositions.isEmpty()) {
      return
    }
    val revealedForAyah = revealedWordsMap[page]?.get(suraAyah) ?: return
    val removedAny = revealedForAyah.removeAll(wordPositions.toSet())
    val spotlight = lastRevealedWordByPage[page]
    if (spotlight != null && spotlight.suraAyah == suraAyah && spotlight.wordPosition in wordPositions) {
      lastRevealedWordByPage.remove(page)
    }
    if (removedAny) {
      publishState(currentPage = page, lastTransition = null)
    }
  }

  @Synchronized
  fun getPageProgress(page: Int): PageMemorizationProgress {
    val allAyahs = pageAyahsMap[page].orEmpty()
    val range = _uiState.value.activeRange
    val ayahs = if (range != null) {
      allAyahs.filter { it >= range.startAyah && it <= range.endAyah }.ifEmpty { allAyahs }
    } else {
      allAyahs
    }
    val wordPosMap = pageWordPositionsMap[page].orEmpty()
    val pageRevealed = revealedWordsMap[page].orEmpty()

    var totalWords = 0
    var revealedWords = 0
    var revealedAyahs = 0

    for (ayah in ayahs) {
      val words = wordPosMap[ayah] ?: (1..getWordCountForAyah(ayah).coerceAtLeast(1)).toList()
      val rev = pageRevealed[ayah].orEmpty()
      totalWords += words.size
      val revCount = words.count { it in rev }
      revealedWords += revCount
      if (words.isNotEmpty() && revCount >= words.size) {
        revealedAyahs++
      }
    }

    return PageMemorizationProgress(
      page = page,
      totalAyahs = ayahs.size,
      revealedAyahs = revealedAyahs,
      totalWords = totalWords,
      revealedWords = revealedWords
    )
  }

  private fun publishState(
    isHiddenModeEnabled: Boolean = _uiState.value.isHiddenModeEnabled,
    revealMode: RevealMode = _uiState.value.revealMode,
    previousAyahWindowWords: Int = _uiState.value.previousAyahWindowWords,
    activeRange: MemorizationRange? = _uiState.value.activeRange,
    currentPage: Int = _uiState.value.currentPage,
    lastTransition: RevealTransition? = _uiState.value.lastTransition
  ) {
    val progress = getPageProgress(currentPage)
    val newState = MemorizationUiState(
      isHiddenModeEnabled = isHiddenModeEnabled,
      revealMode = revealMode,
      previousAyahWindowWords = previousAyahWindowWords,
      activeRange = activeRange,
      currentPage = currentPage,
      pageProgress = progress,
      lastTransition = lastTransition,
      version = versionCounter++
    )
    _uiState.value = newState
    for (listener in listeners) {
      listener.onMemorizationStateChanged(newState)
    }
  }
}
