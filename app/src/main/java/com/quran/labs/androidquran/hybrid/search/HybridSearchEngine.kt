package com.quran.labs.androidquran.hybrid.search

import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.database.CursorWrapper
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Process
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.quran.common.search.RustArabicSearchEngine
import com.quran.common.search.RustSearchResultsCursor
import com.quran.common.search.arabic.ArabicCompiledQuery
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.NativeUthmaniTypefaceCache
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.min

@SingleIn(AppScope::class)
class HybridSearchEngine internal constructor(
  private val context: Context,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val quranFileUtils: QuranFileUtils,
  private val translationsDBAdapter: TranslationsDBAdapter,
  private val recentSearchRepository: RecentSearchRepository,
  private val hybridBookmarkToggler: HybridBookmarkToggler,
  private val scope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val quranSettings: QuranSettings? = null
) {

  @Inject
  constructor(
    @ApplicationContext context: Context,
    quranInfo: QuranInfo,
    quranDisplayData: QuranDisplayData,
    quranFileUtils: QuranFileUtils,
    translationsDBAdapter: TranslationsDBAdapter,
    recentSearchRepository: RecentSearchRepository,
    hybridBookmarkToggler: HybridBookmarkToggler,
    appCoroutineScope: AppCoroutineScope,
    quranSettings: QuranSettings
  ) : this(
    context = context,
    quranInfo = quranInfo,
    quranDisplayData = quranDisplayData,
    quranFileUtils = quranFileUtils,
    translationsDBAdapter = translationsDBAdapter,
    recentSearchRepository = recentSearchRepository,
    hybridBookmarkToggler = hybridBookmarkToggler,
    scope = appCoroutineScope,
    quranSettings = quranSettings
  )

  private sealed interface PendingAyahSource {
    val totalCount: Int

    class Rust(
      val packedMatches: IntArray,
      val rustGeneration: Int,
      val compiledArabic: ArabicCompiledQuery?,
      val useRawArabic: Boolean,
      val containsArabic: Boolean
    ) : PendingAyahSource {
      override val totalCount: Int = packedMatches.size / RustArabicSearchEngine.MATCH_STRIDE
    }

    class CursorRows(
      val ids: LongArray,
      val suras: IntArray,
      val ayahs: IntArray,
      val rawTexts: Array<String>,
      val compiledArabic: ArabicCompiledQuery?,
      val useRawArabic: Boolean,
      val containsArabic: Boolean
    ) : PendingAyahSource {
      override val totalCount: Int = ids.size
    }
  }

  private class PendingTafsirSource(
    val ids: LongArray,
    val suras: IntArray,
    val ayahs: IntArray,
    val rawTexts: Array<String>,
    val translatorNames: Array<String>,
    val translationIds: LongArray,
    val languageCodes: Array<String>,
    val occurrenceIndices: IntArray,
    val arabicFallback: ArabicCompiledQuery?
  ) {
    val totalCount: Int = ids.size
  }

  private val suraSearchIndex = SuraSearchIndex(quranInfo, quranDisplayData)
  private val rustSearchDispatcher: CoroutineDispatcher =
    if (ioDispatcher === Dispatchers.IO) defaultRustSearchDispatcher else ioDispatcher
  private val queryGeneration = AtomicLong(0L)
  private var activeSearchJob: Job? = null
  private var loadMoreJob: Job? = null
  private var progressivePrebuildJob: Job? = null

  @Volatile
  private var pendingAyahSource: PendingAyahSource? = null

  @Volatile
  private var pendingTafsirSource: PendingTafsirSource? = null

  @Volatile
  private var highlightColorArgb: Int = 0

  @Volatile
  private var highlightBgArgb: Int = 0

  private val staticLayoutCache = LruCache<Int, StaticLayout>(STATIC_LAYOUT_CACHE_SIZE)
  private val refLayoutCache = LruCache<Int, StaticLayout>(REF_LAYOUT_CACHE_SIZE)
  private val pagePillLayoutCache = LruCache<Long, StaticLayout>(PAGE_PILL_LAYOUT_CACHE_SIZE)

  @Volatile
  private var staticLayoutConfigSig: String = ""

  @Volatile
  private var staticLayoutCacheGen: Long = -1L

  @Volatile
  private var measuredAvailWidthPx: Int = 0

  private data class ResolvedPaletteColors(
    val highlightColor: Color,
    val highlightBg: Color,
    val versePrimaryArgb: Int,
    val refAccentArgb: Int,
    val pillSecondaryArgb: Int
  )

  private val _uiState = MutableStateFlow(
    HybridSearchUiState(
      recentQueries = recentSearchRepository.recentQueriesFlow.value
    )
  )
  val uiState: StateFlow<HybridSearchUiState> = _uiState.asStateFlow()

  init {
    scope.launch {
      recentSearchRepository.recentQueriesFlow.collect { recents ->
        _uiState.update { it.copy(recentQueries = recents) }
      }
    }
    scope.launch {
      hybridBookmarkToggler.bookmarkedKeysFlow.collect { keys ->
        _uiState.update { it.copy(bookmarkedKeys = keys) }
      }
    }
  }

  fun updateHighlightPalette(highlightColor: Color, highlightBg: Color) {
    highlightColorArgb = highlightColor.toArgb()
    highlightBgArgb = highlightBg.toArgb()
  }

  private fun resolvePaletteColors(): ResolvedPaletteColors {
    val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    val isDark = runCatching {
      val themePref = quranSettings?.currentTheme()
      if (themePref != null) ThemeUtil.isDarkAppearance(themePref, systemDark) else systemDark
    }.getOrDefault(systemDark)
    val style = runCatching {
      quranSettings?.appThemeStyle?.let { AppThemeStyle.fromPreference(it) } ?: AppThemeStyle.current
    }.getOrDefault(AppThemeStyle.current)
    val palette = style.palette(isDark)

    val cachedColor = highlightColorArgb
    val cachedBg = highlightBgArgb
    val highlightColor = if (cachedColor != 0 && cachedBg != 0) Color(cachedColor) else palette.searchHighlight
    val highlightBg = if (cachedColor != 0 && cachedBg != 0) {
      Color(cachedBg)
    } else {
      Color(ColorUtils.setAlphaComponent(palette.editorialAccent.toArgb(), if (isDark) 51 else 41))
    }
    return ResolvedPaletteColors(
      highlightColor = highlightColor,
      highlightBg = highlightBg,
      versePrimaryArgb = palette.textPrimary.toArgb(),
      refAccentArgb = palette.editorialAccent.toArgb(),
      pillSecondaryArgb = palette.accent.toArgb()
    )
  }

  private fun resolveHighlightPalette(): Pair<Color, Color> {
    val resolved = resolvePaletteColors()
    return resolved.highlightColor to resolved.highlightBg
  }

  fun getCachedStaticLayout(sura: Int, ayah: Int): StaticLayout? {
    return synchronized(staticLayoutCache) {
      staticLayoutCache.get(staticLayoutKey(sura, ayah))
    }
  }

  fun getCachedRefStaticLayout(sura: Int, ayah: Int): StaticLayout? {
    return synchronized(refLayoutCache) {
      refLayoutCache.get(staticLayoutKey(sura, ayah))
    }
  }

  fun getCachedPagePillStaticLayout(page: Int, @ColorInt textColorArgb: Int): StaticLayout? {
    return synchronized(pagePillLayoutCache) {
      pagePillLayoutCache.get(pagePillLayoutKey(page, textColorArgb))
    }
  }

  fun clearStaticLayoutCache() {
    synchronized(staticLayoutCache) {
      staticLayoutCache.evictAll()
      staticLayoutCacheGen = -1L
    }
    synchronized(refLayoutCache) {
      refLayoutCache.evictAll()
    }
    synchronized(pagePillLayoutCache) {
      pagePillLayoutCache.evictAll()
    }
  }

  private fun resetVerseStaticLayoutCacheForGeneration(gen: Long) {
    synchronized(staticLayoutCache) {
      if (staticLayoutCacheGen != gen) {
        staticLayoutCache.evictAll()
        staticLayoutCacheGen = gen
      }
    }
  }

  fun updateExpectedVerseAvailWidthPx(availWidthPx: Int) {
    if (availWidthPx <= 0) return
    val prevExpected = expectedAvailWidthPx()
    measuredAvailWidthPx = availWidthPx
    if (prevExpected != availWidthPx) {
      synchronized(staticLayoutCache) {
        staticLayoutCache.evictAll()
      }
      synchronized(refLayoutCache) {
        refLayoutCache.evictAll()
      }
    }
  }

  internal fun staticLayoutCacheCountForTest(): Int {
    return synchronized(staticLayoutCache) {
      staticLayoutCache.size()
    }
  }

  internal fun refLayoutCacheCountForTest(): Int {
    return synchronized(refLayoutCache) {
      refLayoutCache.size()
    }
  }

  internal fun pagePillLayoutCacheCountForTest(): Int {
    return synchronized(pagePillLayoutCache) {
      pagePillLayoutCache.size()
    }
  }

  internal fun expectedAvailWidthPxForTest(): Int = expectedAvailWidthPx()

  private fun expectedAvailWidthPx(): Int {
    val measured = measuredAvailWidthPx
    if (measured > 0) return measured
    val dm = context.resources.displayMetrics
    val outerPad = HybridViewTokens.dp(context, 14f)
    val cardPad = HybridViewTokens.dp(context, 14f)
    return (dm.widthPixels - outerPad * 2 - cardPad * 2).coerceAtLeast(0)
  }

  private fun prebuildStaticLayoutsFromSource(
    gen: Long,
    source: PendingAyahSource,
    startIndex: Int,
    endIndex: Int,
    highlightColorArgb: Int,
    highlightBgArgb: Int
  ) {
    val totalCount = source.totalCount
    if (totalCount <= 0) return
    val clampedStart = startIndex.coerceIn(0, totalCount)
    val clampedEnd = endIndex.coerceIn(clampedStart, totalCount)
    if (clampedEnd <= clampedStart) return
    val availW = expectedAvailWidthPx()
    if (availW <= 0) return

    val dm = context.resources.displayMetrics
    val fontScale = context.resources.configuration.fontScale
    val uthmaniTf = NativeUthmaniTypefaceCache.get(context)
    val paletteColors = resolvePaletteColors()
    val configSig = "$availW|${dm.density}|$fontScale|$highlightColorArgb|$highlightBgArgb|" +
      "${paletteColors.versePrimaryArgb}|${paletteColors.refAccentArgb}|${paletteColors.pillSecondaryArgb}|${uthmaniTf != null}"
    synchronized(staticLayoutCache) {
      if (queryGeneration.get() != gen) return
      if (staticLayoutConfigSig != configSig) {
        staticLayoutCache.evictAll()
        synchronized(refLayoutCache) { refLayoutCache.evictAll() }
        synchronized(pagePillLayoutCache) { pagePillLayoutCache.evictAll() }
        staticLayoutConfigSig = configSig
      }
      if (staticLayoutCacheGen != gen) {
        staticLayoutCache.evictAll()
        staticLayoutCacheGen = gen
      }
    }
    val batchPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      density = dm.density
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18.5f, dm)
      typeface = uthmaniTf ?: Typeface.DEFAULT
      color = paletteColors.versePrimaryArgb
    }
    val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      density = dm.density
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, dm)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      color = paletteColors.refAccentArgb
    }
    val pagePillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      density = dm.density
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10.5f, dm)
      typeface = Typeface.DEFAULT
      color = paletteColors.pillSecondaryArgb
    }
    val pillPadH = HybridViewTokens.dp(context, 8f)
    val headerGap = HybridViewTokens.dp(context, 8f)
    val maxPillTextW = (availW - pillPadH * 2).coerceAtLeast(0)

    val effectiveStart = maxOf(clampedStart, clampedEnd - STATIC_LAYOUT_CACHE_SIZE)
    val rustSource = source as? PendingAyahSource.Rust
    val cursorSource = source as? PendingAyahSource.CursorRows
    val packed = rustSource?.packedMatches
    val stride = RustArabicSearchEngine.MATCH_STRIDE
    val rustGen = rustSource?.rustGeneration ?: -1
    val compiledArabic = rustSource?.compiledArabic ?: cursorSource!!.compiledArabic
    val useRawArabic = rustSource?.useRawArabic ?: cursorSource!!.useRawArabic
    val containsArabic = rustSource?.containsArabic ?: cursorSource!!.containsArabic

    for (idx in effectiveStart until clampedEnd) {
      if (queryGeneration.get() != gen) return

      val sura: Int
      val ayah: Int
      val rawText: String
      val rustVerseIndex: Int
      if (packed != null) {
        val base = idx * stride
        rustVerseIndex = packed[base]
        sura = packed[base + 2]
        ayah = packed[base + 3]
        rawText = RustArabicSearchEngine.getVerseText(rustVerseIndex) ?: ""
      } else {
        val cs = cursorSource!!
        rustVerseIndex = -1
        sura = cs.suras[idx]
        ayah = cs.ayahs[idx]
        rawText = cs.rawTexts[idx]
      }

      val page = quranInfo.getPageFromSuraAyah(sura, ayah)
      val key = staticLayoutKey(sura, ayah)

      // 1. Page pill StaticLayout cached by (page, palette.textSecondary)
      var pillLayout: StaticLayout? = null
      if (maxPillTextW > 0) {
        val pillKey = pagePillLayoutKey(page, paletteColors.pillSecondaryArgb)
        pillLayout = synchronized(pagePillLayoutCache) { pagePillLayoutCache.get(pillKey) }
        if (pillLayout == null || pillLayout.width > maxPillTextW) {
          val pageLabel = context.getString(
            R.string.hybrid_search_page_pill,
            QuranUtils.getLocalizedNumber(page)
          )
          val desiredPillW = ceil(Layout.getDesiredWidth(pageLabel, pagePillPaint).toDouble())
            .toInt()
            .coerceIn(0, maxPillTextW)
          val builtPill = buildSingleLineStaticLayout(
            source = pageLabel,
            textPaint = pagePillPaint,
            widthPx = desiredPillW,
            textDir = TextDirectionHeuristics.FIRSTSTRONG_RTL
          )
          if (builtPill != null) {
            pillLayout = builtPill
            synchronized(pagePillLayoutCache) {
              if (staticLayoutConfigSig == configSig && queryGeneration.get() == gen) {
                pagePillLayoutCache.put(pillKey, builtPill)
              }
            }
          }
        }
      }

      // 2. Surah/ayah ref StaticLayout
      val pillMeasuredW = if (pillLayout != null) pillLayout.width + pillPadH * 2 else 0
      val refMaxW = (availW - pillMeasuredW - headerGap).coerceAtLeast(0)
      val existingRef = synchronized(refLayoutCache) { refLayoutCache.get(key) }
      if ((existingRef == null || existingRef.width > refMaxW) && refMaxW > 0) {
        val suraName = quranDisplayData.getSuraName(context, sura, wantPrefix = false)
        val ayahRefLabel = context.getString(
          R.string.hybrid_search_sura_ayah_header,
          suraName,
          QuranUtils.getLocalizedNumber(ayah)
        )
        val desiredRefW = ceil(Layout.getDesiredWidth(ayahRefLabel, refPaint).toDouble())
          .toInt()
          .coerceIn(0, refMaxW)
        val builtRef = buildSingleLineStaticLayout(
          source = ayahRefLabel,
          textPaint = refPaint,
          widthPx = desiredRefW,
          textDir = TextDirectionHeuristics.FIRSTSTRONG_RTL
        )
        if (builtRef != null) {
          synchronized(refLayoutCache) {
            if (staticLayoutConfigSig == configSig && queryGeneration.get() == gen) {
              refLayoutCache.put(key, builtRef)
            }
          }
        }
      }

      // 3. Verse StaticLayout directly from PendingAyahSource without AyahSearchHit allocation
      val existingVerse = synchronized(staticLayoutCache) { staticLayoutCache.get(key) }
      if (existingVerse != null && existingVerse.width == availW) continue

      val plainText: String
      val ranges: List<HighlightSpanRange>
      val isArabicText: Boolean
      if (useRawArabic && rawText.indexOf('<') < 0) {
        plainText = rawText
        ranges = HybridSearchHighlighter.computeArabicRanges(
          text = rawText,
          compiledQuery = compiledArabic,
          rustVerseIndex = rustVerseIndex,
          rustGeneration = rustGen
        )
        isArabicText = true
      } else {
        val (extracted, htmlRanges) = HybridSearchHighlighter.extractPlainTextAndRanges(
          rawText = rawText,
          arabicQueryFallback = compiledArabic
        )
        plainText = extracted
        ranges = htmlRanges
        isArabicText = containsArabic || QuranUtils.doesStringContainArabic(plainText)
      }
      if (!isArabicText) continue

      val highlightedSource = HybridSearchHighlighter.buildForegroundHighlightedSpan(
        text = plainText,
        ranges = ranges,
        highlightColor = highlightColorArgb
      )
      val built = buildStaticLayout(highlightedSource, batchPaint, availW, isArabic = true) ?: continue
      synchronized(staticLayoutCache) {
        if (staticLayoutConfigSig == configSig &&
          queryGeneration.get() == gen &&
          staticLayoutCacheGen == gen
        ) {
          staticLayoutCache.put(key, built)
        }
      }
    }
  }

  private fun prebuildStaticLayouts(
    hits: List<AyahSearchHit>,
    highlightColorArgb: Int,
    highlightBgArgb: Int,
    gen: Long,
    startIndex: Int = 0,
    endIndex: Int = hits.size
  ) {
    if (hits.isEmpty()) return
    val clampedStart = startIndex.coerceIn(0, hits.size)
    val clampedEnd = endIndex.coerceIn(clampedStart, hits.size)
    if (clampedEnd <= clampedStart) return
    val availW = expectedAvailWidthPx()
    if (availW <= 0) return
    val dm = context.resources.displayMetrics
    val fontScale = context.resources.configuration.fontScale
    val uthmaniTf = NativeUthmaniTypefaceCache.get(context)
    val paletteColors = resolvePaletteColors()
    val configSig = "$availW|${dm.density}|$fontScale|$highlightColorArgb|$highlightBgArgb|" +
      "${paletteColors.versePrimaryArgb}|${paletteColors.refAccentArgb}|${paletteColors.pillSecondaryArgb}|${uthmaniTf != null}"
    synchronized(staticLayoutCache) {
      if (queryGeneration.get() != gen) return
      if (staticLayoutConfigSig != configSig) {
        staticLayoutCache.evictAll()
        synchronized(refLayoutCache) { refLayoutCache.evictAll() }
        synchronized(pagePillLayoutCache) { pagePillLayoutCache.evictAll() }
        staticLayoutConfigSig = configSig
      }
      if (staticLayoutCacheGen != gen) {
        staticLayoutCache.evictAll()
        staticLayoutCacheGen = gen
      }
    }
    val batchPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      density = dm.density
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18.5f, dm)
      typeface = uthmaniTf ?: Typeface.DEFAULT
      color = paletteColors.versePrimaryArgb
    }
    val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      density = dm.density
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, dm)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      color = paletteColors.refAccentArgb
    }
    val pagePillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      density = dm.density
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10.5f, dm)
      typeface = Typeface.DEFAULT
      color = paletteColors.pillSecondaryArgb
    }
    val pillPadH = HybridViewTokens.dp(context, 8f)
    val headerGap = HybridViewTokens.dp(context, 8f)
    val maxPillTextW = (availW - pillPadH * 2).coerceAtLeast(0)

    // Prebuild the entire newly loaded slice [clampedStart, clampedEnd), capping only if a single
    // slice exceeds STATIC_LAYOUT_CACHE_SIZE so incremental batches never skip boundary items.
    val effectiveStart = maxOf(clampedStart, clampedEnd - STATIC_LAYOUT_CACHE_SIZE)
    for (i in effectiveStart until clampedEnd) {
      if (queryGeneration.get() != gen) return
      val hit = hits[i]
      val key = staticLayoutKey(hit.sura, hit.ayah)

      // 1. Page pill StaticLayout cached by (page, palette.textSecondary)
      var pillLayout: StaticLayout? = null
      if (hit.pageLabel.isNotEmpty() && maxPillTextW > 0) {
        val pillKey = pagePillLayoutKey(hit.page, paletteColors.pillSecondaryArgb)
        pillLayout = synchronized(pagePillLayoutCache) { pagePillLayoutCache.get(pillKey) }
        if (pillLayout == null || pillLayout.width > maxPillTextW) {
          val desiredPillW = ceil(Layout.getDesiredWidth(hit.pageLabel, pagePillPaint).toDouble())
            .toInt()
            .coerceIn(0, maxPillTextW)
          val builtPill = buildSingleLineStaticLayout(
            source = hit.pageLabel,
            textPaint = pagePillPaint,
            widthPx = desiredPillW,
            textDir = TextDirectionHeuristics.FIRSTSTRONG_RTL
          )
          if (builtPill != null) {
            pillLayout = builtPill
            synchronized(pagePillLayoutCache) {
              if (staticLayoutConfigSig == configSig && queryGeneration.get() == gen) {
                pagePillLayoutCache.put(pillKey, builtPill)
              }
            }
          }
        }
      }

      // 2. Surah/ayah ref StaticLayout
      if (hit.ayahRefLabel.isNotEmpty()) {
        val pillMeasuredW = if (pillLayout != null) pillLayout.width + pillPadH * 2 else 0
        val refMaxW = (availW - pillMeasuredW - headerGap).coerceAtLeast(0)
        val existingRef = synchronized(refLayoutCache) { refLayoutCache.get(key) }
        if ((existingRef == null || existingRef.width > refMaxW) && refMaxW > 0) {
          val desiredRefW = ceil(Layout.getDesiredWidth(hit.ayahRefLabel, refPaint).toDouble())
            .toInt()
            .coerceIn(0, refMaxW)
          val builtRef = buildSingleLineStaticLayout(
            source = hit.ayahRefLabel,
            textPaint = refPaint,
            widthPx = desiredRefW,
            textDir = TextDirectionHeuristics.FIRSTSTRONG_RTL
          )
          if (builtRef != null) {
            synchronized(refLayoutCache) {
              if (staticLayoutConfigSig == configSig && queryGeneration.get() == gen) {
                refLayoutCache.put(key, builtRef)
              }
            }
          }
        }
      }

      // 3. Verse StaticLayout
      if (!hit.isArabic) continue
      val existingVerse = synchronized(staticLayoutCache) { staticLayoutCache.get(key) }
      if (existingVerse == null || existingVerse.width != availW) {
        val source = hit.precomputedNativeText ?: hit.text
        val built = buildStaticLayout(source, batchPaint, availW, hit.isArabic) ?: continue
        synchronized(staticLayoutCache) {
          if (staticLayoutConfigSig == configSig &&
            queryGeneration.get() == gen &&
            staticLayoutCacheGen == gen
          ) {
            staticLayoutCache.put(key, built)
          }
        }
      }
    }
  }

  private fun buildSingleLineStaticLayout(
    source: CharSequence,
    textPaint: TextPaint,
    widthPx: Int,
    textDir: android.text.TextDirectionHeuristic
  ): StaticLayout? {
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        StaticLayout.Builder.obtain(source, 0, source.length, textPaint, widthPx)
          .setAlignment(Layout.Alignment.ALIGN_NORMAL)
          .setIncludePad(true)
          .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
          .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
          .setTextDirection(textDir)
          .setEllipsize(TextUtils.TruncateAt.END)
          .setEllipsizedWidth(widthPx)
          .setMaxLines(1)
          .build()
      } else {
        @Suppress("DEPRECATION")
        StaticLayout(
          source,
          textPaint,
          widthPx,
          Layout.Alignment.ALIGN_NORMAL,
          1.0f,
          0f,
          true
        )
      }
    }.getOrNull()
  }

  private fun buildStaticLayout(
    source: CharSequence,
    textPaint: TextPaint,
    widthPx: Int,
    isArabic: Boolean
  ): StaticLayout? {
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        StaticLayout.Builder.obtain(source, 0, source.length, textPaint, widthPx)
          .setAlignment(Layout.Alignment.ALIGN_NORMAL)
          .setLineSpacing(0f, 1.42f)
          .setIncludePad(true)
          .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
          .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
          .setTextDirection(
            if (isArabic) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_RTL
          )
          .setMaxLines(Int.MAX_VALUE)
          .build()
      } else {
        @Suppress("DEPRECATION")
        StaticLayout(
          source,
          textPaint,
          widthPx,
          Layout.Alignment.ALIGN_NORMAL,
          1.42f,
          0f,
          true
        )
      }
    }.getOrNull()
  }

  /**
   * Immediately dispatches a search on [ioDispatcher] without waiting for a Compose frame,
   * preserving `intent_to_rust_done_ms <= 32.5 ms` on cold/warm intent launches.
   */
  fun submitQuery(
    rawQuery: String,
    scopeOverride: HybridSearchScope? = null,
    recordRecent: Boolean = false
  ) {
    val gen = queryGeneration.incrementAndGet()
    activeSearchJob?.cancel()
    loadMoreJob?.cancel()
    progressivePrebuildJob?.cancel()

    val targetScope = scopeOverride ?: _uiState.value.scope
    if (rawQuery.isBlank()) {
      clearToEmptyState(gen = gen, newQuery = rawQuery, targetScope = targetScope)
      return
    }

    if (targetScope == HybridSearchScope.SURAS) {
      _uiState.update {
        it.copy(
          query = rawQuery,
          scope = HybridSearchScope.SURAS,
          isLoading = false,
          needsArabicDbWarning = false,
          noTranslationsWarning = false,
          noTafsirInstalled = false
        )
      }
      activeSearchJob = scope.launch(computeDispatcher) {
        val hits = suraSearchIndex.search(context, rawQuery)
        if (queryGeneration.get() == gen) {
          _uiState.update { it.copy(suraHits = hits, isLoading = false) }
          if (recordRecent) {
            withContext(ioDispatcher) {
              recentSearchRepository.recordQuery(rawQuery)
            }
          }
        }
      }
      return
    }

    _uiState.update {
      it.copy(
        query = rawQuery,
        scope = targetScope,
        isLoading = true,
        needsArabicDbWarning = false,
        noTranslationsWarning = false,
        noTafsirInstalled = false
      )
    }

    if (targetScope == HybridSearchScope.AYAHS) {
      resetVerseStaticLayoutCacheForGeneration(gen)
    }

    val searchDispatcher = if (targetScope == HybridSearchScope.AYAHS) rustSearchDispatcher else ioDispatcher
    activeSearchJob = scope.launch(searchDispatcher) {
      executeScopeSearch(gen = gen, query = rawQuery, searchScope = targetScope)
      if (recordRecent && queryGeneration.get() == gen) {
        withContext(ioDispatcher) {
          recentSearchRepository.recordQuery(rawQuery)
        }
      }
    }
  }

  fun onQueryTextChanged(newQuery: String) {
    val gen = queryGeneration.incrementAndGet()
    activeSearchJob?.cancel()
    loadMoreJob?.cancel()
    progressivePrebuildJob?.cancel()

    val currentScope = _uiState.value.scope
    if (newQuery.isBlank()) {
      clearToEmptyState(gen = gen, newQuery = newQuery, targetScope = currentScope)
      return
    }

    if (currentScope == HybridSearchScope.SURAS) {
      _uiState.update {
        it.copy(
          query = newQuery,
          isLoading = false,
          needsArabicDbWarning = false,
          noTranslationsWarning = false,
          noTafsirInstalled = false
        )
      }
      activeSearchJob = scope.launch(computeDispatcher) {
        val hits = suraSearchIndex.search(context, newQuery)
        if (queryGeneration.get() == gen) {
          _uiState.update { it.copy(suraHits = hits, isLoading = false) }
        }
      }
      return
    }

    _uiState.update {
      it.copy(
        query = newQuery,
        isLoading = true,
        needsArabicDbWarning = false,
        noTranslationsWarning = false,
        noTafsirInstalled = false
      )
    }

    val searchDispatcher = if (currentScope == HybridSearchScope.AYAHS) rustSearchDispatcher else ioDispatcher
    activeSearchJob = scope.launch(searchDispatcher) {
      delay(TYPING_DEBOUNCE_MS)
      if (queryGeneration.get() != gen) return@launch
      executeScopeSearch(gen = gen, query = newQuery, searchScope = currentScope)
    }
  }

  fun clearQuery() {
    val gen = queryGeneration.incrementAndGet()
    activeSearchJob?.cancel()
    loadMoreJob?.cancel()
    progressivePrebuildJob?.cancel()
    val currentScope = _uiState.value.scope
    clearToEmptyState(gen = gen, newQuery = "", targetScope = currentScope)
  }

  fun onScopeSelected(newScope: HybridSearchScope) {
    val current = _uiState.value
    if (current.scope == newScope) return

    val gen = queryGeneration.incrementAndGet()
    activeSearchJob?.cancel()
    loadMoreJob?.cancel()
    progressivePrebuildJob?.cancel()

    if (newScope == HybridSearchScope.SURAS) {
      _uiState.update {
        it.copy(
          scope = HybridSearchScope.SURAS,
          isLoading = false,
          needsArabicDbWarning = false,
          noTranslationsWarning = false,
          noTafsirInstalled = false
        )
      }
      activeSearchJob = scope.launch(computeDispatcher) {
        val hits = suraSearchIndex.search(context, current.query)
        if (queryGeneration.get() == gen) {
          _uiState.update { it.copy(suraHits = hits) }
        }
      }
      return
    }

    if (current.query.isBlank()) {
      clearToEmptyState(gen = gen, newQuery = current.query, targetScope = newScope)
      return
    }

    _uiState.update {
      it.copy(
        scope = newScope,
        isLoading = true,
        needsArabicDbWarning = false,
        noTranslationsWarning = false,
        noTafsirInstalled = false
      )
    }
    val searchDispatcher = if (newScope == HybridSearchScope.AYAHS) rustSearchDispatcher else ioDispatcher
    activeSearchJob = scope.launch(searchDispatcher) {
      executeScopeSearch(gen = gen, query = current.query, searchScope = newScope)
    }
  }

  fun loadMoreAyahHits() {
    val source = pendingAyahSource ?: return
    val currentState = _uiState.value
    if (currentState.scope != HybridSearchScope.AYAHS || !currentState.hasMoreAyahHits) return
    val currentSize = currentState.ayahHits.size
    if (currentSize >= source.totalCount) return
    if (loadMoreJob?.isActive == true) return

    val gen = queryGeneration.get()
    val endIndex = min(currentSize + LOAD_MORE_BATCH_SIZE, source.totalCount)
    loadMoreJob = scope.launch(computeDispatcher) {
      val (highlightColor, highlightBg) = resolveHighlightPalette()
      val hColorArgb = highlightColor.toArgb()
      val hBgArgb = highlightBg.toArgb()

      val nextBatch = materializeAyahWindow(
        source = source,
        startIndex = currentSize,
        endIndex = endIndex,
        highlightColor = highlightColor,
        highlightBg = highlightBg
      )
      if (nextBatch.isNotEmpty()) {
        prebuildStaticLayouts(nextBatch, hColorArgb, hBgArgb, gen = gen)
      }
      if (queryGeneration.get() != gen) return@launch
      _uiState.update { state ->
        if (state.ayahHits.size != currentSize) {
          state
        } else {
          val combined = ArrayList<AyahSearchHit>(currentSize + nextBatch.size)
          combined.addAll(state.ayahHits)
          combined.addAll(nextBatch)
          state.copy(
            ayahHits = combined,
            hasMoreAyahHits = endIndex < source.totalCount
          )
        }
      }
    }
  }

  fun loadMoreTafsirHits() {
    val source = pendingTafsirSource ?: return
    val currentState = _uiState.value
    if (currentState.scope != HybridSearchScope.TAFSIR || !currentState.hasMoreTafsirHits) return
    val currentSize = currentState.tafsirHits.size
    if (currentSize >= source.totalCount) return
    if (loadMoreJob?.isActive == true) return

    val gen = queryGeneration.get()
    val endIndex = min(currentSize + LOAD_MORE_BATCH_SIZE, source.totalCount)
    loadMoreJob = scope.launch(computeDispatcher) {
      val nextBatch = materializeTafsirWindow(
        source = source,
        startIndex = currentSize,
        endIndex = endIndex
      )
      if (queryGeneration.get() != gen) return@launch
      _uiState.update { state ->
        if (state.tafsirHits.size != currentSize) {
          state
        } else {
          val combined = ArrayList<TafsirSearchHit>(currentSize + nextBatch.size)
          combined.addAll(state.tafsirHits)
          combined.addAll(nextBatch)
          state.copy(
            tafsirHits = combined,
            hasMoreTafsirHits = endIndex < source.totalCount
          )
        }
      }
    }
  }

  /**
   * Trims excess materialized hits beyond the initial window when leaving SearchActivity
   * temporarily (e.g., jumping to PagerActivity), preserving fast Back navigation without
   * holding large Java heap allocations in AppScope.
   */
  fun trimTransientWindow() {
    loadMoreJob?.cancel()
    progressivePrebuildJob?.cancel()
    clearStaticLayoutCache()
    val ayahSource = pendingAyahSource
    val tafsirSource = pendingTafsirSource
    val current = _uiState.value
    val trimAyahs = ayahSource != null && current.ayahHits.size > TRANSIENT_TRIM_WINDOW_SIZE
    val trimTafsir = tafsirSource != null && current.tafsirHits.size > TRANSIENT_TRIM_WINDOW_SIZE
    if (trimAyahs || trimTafsir) {
      _uiState.update { state ->
        state.copy(
          ayahHits = if (trimAyahs) state.ayahHits.take(TRANSIENT_TRIM_WINDOW_SIZE) else state.ayahHits,
          hasMoreAyahHits = if (trimAyahs) {
            ayahSource!!.totalCount > TRANSIENT_TRIM_WINDOW_SIZE
          } else {
            state.hasMoreAyahHits
          },
          tafsirHits = if (trimTafsir) state.tafsirHits.take(TRANSIENT_TRIM_WINDOW_SIZE) else state.tafsirHits,
          hasMoreTafsirHits = if (trimTafsir) {
            tafsirSource!!.totalCount > TRANSIENT_TRIM_WINDOW_SIZE
          } else {
            state.hasMoreTafsirHits
          }
        )
      }
    }
  }

  /**
   * Releases transient search hit lists and packed cursors when standalone SearchActivity finishes.
   */
  fun clearTransientResults() {
    queryGeneration.incrementAndGet()
    activeSearchJob?.cancel()
    loadMoreJob?.cancel()
    progressivePrebuildJob?.cancel()
    clearStaticLayoutCache()
    pendingAyahSource = null
    pendingTafsirSource = null
    _uiState.update {
      it.copy(
        query = "",
        ayahHits = emptyList(),
        totalAyahHitCount = 0,
        hasMoreAyahHits = false,
        suraHits = emptyList(),
        tafsirHits = emptyList(),
        totalTafsirHitCount = 0,
        hasMoreTafsirHits = false,
        hasSearchedAyahs = false,
        hasSearchedTafsir = false,
        rustElapsedMs = null,
        isLoading = false,
        needsArabicDbWarning = false,
        noTranslationsWarning = false,
        noTafsirInstalled = false
      )
    }
  }

  fun recordRecentQuery(query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    scope.launch(ioDispatcher) {
      recentSearchRepository.recordQuery(trimmed)
    }
  }

  fun removeRecentQuery(query: String) {
    scope.launch(ioDispatcher) {
      recentSearchRepository.removeQuery(query)
    }
  }

  fun clearAllRecentQueries() {
    scope.launch(ioDispatcher) {
      recentSearchRepository.clearAll()
    }
  }

  fun toggleBookmark(sura: Int, ayah: Int) {
    scope.launch(ioDispatcher) {
      hybridBookmarkToggler.toggleAyahBookmark(sura, ayah)
    }
  }

  fun refreshCurrentSearch() {
    val current = _uiState.value
    if (current.query.isNotBlank()) {
      submitQuery(current.query, scopeOverride = current.scope, recordRecent = false)
    }
  }

  private fun clearToEmptyState(gen: Long, newQuery: String, targetScope: HybridSearchScope) {
    clearStaticLayoutCache()
    pendingAyahSource = null
    pendingTafsirSource = null
    _uiState.update {
      it.copy(
        query = newQuery,
        scope = targetScope,
        ayahHits = emptyList(),
        totalAyahHitCount = 0,
        hasMoreAyahHits = false,
        suraHits = emptyList(),
        tafsirHits = emptyList(),
        totalTafsirHitCount = 0,
        hasMoreTafsirHits = false,
        hasSearchedAyahs = false,
        hasSearchedTafsir = false,
        rustElapsedMs = null,
        isLoading = false,
        needsArabicDbWarning = false,
        noTranslationsWarning = false,
        noTafsirInstalled = if (targetScope == HybridSearchScope.TAFSIR) it.noTafsirInstalled else false
      )
    }
    when (targetScope) {
      HybridSearchScope.SURAS -> {
        activeSearchJob = scope.launch(computeDispatcher) {
          val allSuras = suraSearchIndex.search(context, "")
          if (queryGeneration.get() == gen) {
            _uiState.update { it.copy(suraHits = allSuras) }
          }
        }
      }
      HybridSearchScope.TAFSIR -> {
        activeSearchJob = scope.launch(ioDispatcher) {
          val installed = translationsDBAdapter.getTranslations().first()
          if (queryGeneration.get() == gen) {
            _uiState.update { it.copy(noTafsirInstalled = installed.isEmpty()) }
          }
        }
      }
      HybridSearchScope.AYAHS -> Unit
    }
  }

  private suspend fun executeScopeSearch(
    gen: Long,
    query: String,
    searchScope: HybridSearchScope
  ) {
    when (searchScope) {
      HybridSearchScope.AYAHS -> executeAyahSearch(gen, query)
      HybridSearchScope.TAFSIR -> executeTafsirSearch(gen, query)
      HybridSearchScope.SURAS -> {
        val hits = suraSearchIndex.search(context, query)
        if (queryGeneration.get() == gen) {
          _uiState.update { it.copy(suraHits = hits, isLoading = false) }
        }
      }
    }
  }

  private suspend fun executeAyahSearch(gen: Long, query: String) {
    if (queryGeneration.get() != gen) return
    resetVerseStaticLayoutCacheForGeneration(gen)
    val containsArabic = QuranUtils.doesStringContainArabic(query)
    val compiledArabic = if (containsArabic) ArabicCompiledQuery.compile(query) else null
    val useRawArabic = containsArabic && compiledArabic != null
    val searchUri = if (useRawArabic) {
      QuranDataProvider.SEARCH_RAW_ARABIC_URI
    } else {
      QuranDataProvider.SEARCH_URI
    }

    var wasRustCursor = false
    val startNs = System.nanoTime()
    val cursor = context.contentResolver.query(
      searchUri,
      null,
      null,
      arrayOf(query),
      null
    )
    val queryElapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
    val source: PendingAyahSource? = cursor?.use { c ->
      val rustCursor = unwrapRustCursor(c)
      wasRustCursor = rustCursor != null
      if (rustCursor != null) {
        PendingAyahSource.Rust(
          packedMatches = rustCursor.packedMatchesBuffer(),
          rustGeneration = rustCursor.indexGeneration,
          compiledArabic = compiledArabic,
          useRawArabic = useRawArabic,
          containsArabic = containsArabic
        )
      } else {
        val count = c.count.coerceAtLeast(0)
        val ids = LongArray(count)
        val suras = IntArray(count)
        val ayahs = IntArray(count)
        val rawTexts = ArrayList<String>(count)
        var i = 0
        while (c.moveToNext()) {
          if (i < ids.size) {
            ids[i] = c.getLong(0)
            suras[i] = c.getInt(1)
            ayahs[i] = c.getInt(2)
          }
          rawTexts.add(c.getString(3) ?: "")
          i++
        }
        val finalIds = if (i == ids.size) ids else ids.copyOf(i)
        val finalSuras = if (i == suras.size) suras else suras.copyOf(i)
        val finalAyahs = if (i == ayahs.size) ayahs else ayahs.copyOf(i)
        PendingAyahSource.CursorRows(
          ids = finalIds,
          suras = finalSuras,
          ayahs = finalAyahs,
          rawTexts = rawTexts.toTypedArray(),
          compiledArabic = compiledArabic,
          useRawArabic = useRawArabic,
          containsArabic = containsArabic
        )
      }
    }

    if (queryGeneration.get() != gen) return

    val totalCount = source?.totalCount ?: 0
    val initialEnd = min(INITIAL_WINDOW_SIZE, totalCount)
    var resolvedHighlightColorArgb = 0
    var resolvedHighlightBgArgb = 0
    val initialHits: List<AyahSearchHit> = if (source != null && initialEnd > 0) {
      val (highlightColor, highlightBg) = resolveHighlightPalette()
      resolvedHighlightColorArgb = highlightColor.toArgb()
      resolvedHighlightBgArgb = highlightBg.toArgb()
      withContext(computeDispatcher) {
        val built = materializeAyahWindow(
          source = source,
          startIndex = 0,
          endIndex = initialEnd,
          highlightColor = highlightColor,
          highlightBg = highlightBg
        )
        prebuildStaticLayouts(built, resolvedHighlightColorArgb, resolvedHighlightBgArgb, gen = gen)
        built
      }
    } else {
      emptyList()
    }

    if (queryGeneration.get() != gen) return

    pendingAyahSource = if (source != null && totalCount > TRANSIENT_TRIM_WINDOW_SIZE) source else null

    val needsArabicDbWarning = containsArabic &&
      totalCount == 0 &&
      !quranFileUtils.hasTranslation(QuranDataProvider.QURAN_ARABIC_DATABASE)
    val noTranslationsWarning = !containsArabic && source == null && query.length > 2
    val showRustSpeed = totalCount > 0 &&
      (wasRustCursor || (useRawArabic && RustArabicSearchEngine.isNativeAvailable))

    _uiState.update {
      it.copy(
        ayahHits = initialHits,
        totalAyahHitCount = totalCount,
        hasMoreAyahHits = initialEnd < totalCount,
        hasSearchedAyahs = true,
        rustElapsedMs = if (showRustSpeed) queryElapsedMs else null,
        isLoading = false,
        needsArabicDbWarning = needsArabicDbWarning,
        noTranslationsWarning = noTranslationsWarning
      )
    }

    val preloadEnd = min(PROGRESSIVE_PREBUILD_LIMIT, totalCount)
    if (source != null && preloadEnd > initialEnd && expectedAvailWidthPx() > 0) {
      enqueueProgressiveStaticLayoutPrebuild(
        gen = gen,
        source = source,
        startIndex = initialEnd,
        endIndex = preloadEnd,
        highlightColorArgb = resolvedHighlightColorArgb,
        highlightBgArgb = resolvedHighlightBgArgb
      )
    }
  }

  private fun enqueueProgressiveStaticLayoutPrebuild(
    gen: Long,
    source: PendingAyahSource,
    startIndex: Int,
    endIndex: Int,
    highlightColorArgb: Int,
    highlightBgArgb: Int
  ) {
    progressivePrebuildJob?.cancel()
    progressivePrebuildJob = scope.launch(computeDispatcher) {
      var cursor = startIndex
      while (cursor < endIndex && queryGeneration.get() == gen) {
        val chunkEnd = min(cursor + PROGRESSIVE_PREBUILD_CHUNK_SIZE, endIndex)
        prebuildStaticLayoutsFromSource(
          gen = gen,
          source = source,
          startIndex = cursor,
          endIndex = chunkEnd,
          highlightColorArgb = highlightColorArgb,
          highlightBgArgb = highlightBgArgb
        )
        cursor = chunkEnd
        if (cursor < endIndex && queryGeneration.get() == gen) {
          yield()
        }
      }
    }
  }

  private fun materializeAyahWindow(
    source: PendingAyahSource,
    startIndex: Int,
    endIndex: Int,
    highlightColor: Color,
    highlightBg: Color
  ): List<AyahSearchHit> {
    if (endIndex <= startIndex) return emptyList()
    val count = endIndex - startIndex
    val list = ArrayList<AyahSearchHit>(count)
    val highlightColorArgb = highlightColor.toArgb()
    val highlightBgArgb = highlightBg.toArgb()

    when (source) {
      is PendingAyahSource.Rust -> {
        val packed = source.packedMatches
        val stride = RustArabicSearchEngine.MATCH_STRIDE
        val rustGen = source.rustGeneration
        val compiledArabic = source.compiledArabic
        val useRawArabic = source.useRawArabic
        val containsArabic = source.containsArabic
        for (idx in startIndex until endIndex) {
          val base = idx * stride
          val verseIndex = packed[base]
          val id = packed[base + 1].toLong()
          val sura = packed[base + 2]
          val ayah = packed[base + 3]
          val rawText = RustArabicSearchEngine.getVerseText(verseIndex) ?: ""
          list.add(
            buildAyahHit(
              id = id,
              sura = sura,
              ayah = ayah,
              rawText = rawText,
              rustVerseIndex = verseIndex,
              rustGeneration = rustGen,
              compiledArabic = compiledArabic,
              useRawArabic = useRawArabic,
              containsArabic = containsArabic,
              highlightColor = highlightColor,
              highlightBg = highlightBg,
              highlightColorArgb = highlightColorArgb,
              highlightBgArgb = highlightBgArgb
            )
          )
        }
      }

      is PendingAyahSource.CursorRows -> {
        val compiledArabic = source.compiledArabic
        val useRawArabic = source.useRawArabic
        val containsArabic = source.containsArabic
        for (idx in startIndex until endIndex) {
          list.add(
            buildAyahHit(
              id = source.ids[idx],
              sura = source.suras[idx],
              ayah = source.ayahs[idx],
              rawText = source.rawTexts[idx],
              rustVerseIndex = -1,
              rustGeneration = -1,
              compiledArabic = compiledArabic,
              useRawArabic = useRawArabic,
              containsArabic = containsArabic,
              highlightColor = highlightColor,
              highlightBg = highlightBg,
              highlightColorArgb = highlightColorArgb,
              highlightBgArgb = highlightBgArgb
            )
          )
        }
      }
    }
    return list
  }

  private fun buildAyahHit(
    id: Long,
    sura: Int,
    ayah: Int,
    rawText: String,
    rustVerseIndex: Int,
    rustGeneration: Int,
    compiledArabic: ArabicCompiledQuery?,
    useRawArabic: Boolean,
    containsArabic: Boolean,
    highlightColor: Color,
    highlightBg: Color,
    highlightColorArgb: Int,
    highlightBgArgb: Int
  ): AyahSearchHit {
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    val suraName = quranDisplayData.getSuraName(context, sura, wantPrefix = false)

    val plainText: String
    val ranges: List<HighlightSpanRange>
    val isArabicText: Boolean
    if (useRawArabic && rawText.indexOf('<') < 0) {
      plainText = rawText
      ranges = HybridSearchHighlighter.computeArabicRanges(
        text = rawText,
        compiledQuery = compiledArabic,
        rustVerseIndex = rustVerseIndex,
        rustGeneration = rustGeneration
      )
      isArabicText = true
    } else {
      val (extracted, htmlRanges) = HybridSearchHighlighter.extractPlainTextAndRanges(
        rawText = rawText,
        arabicQueryFallback = compiledArabic
      )
      plainText = extracted
      ranges = htmlRanges
      isArabicText = containsArabic || QuranUtils.doesStringContainArabic(plainText)
    }

    val ayahRefLabel = context.getString(
      R.string.hybrid_search_sura_ayah_header,
      suraName,
      QuranUtils.getLocalizedNumber(ayah)
    )
    val pageLabel = context.getString(
      R.string.hybrid_search_page_pill,
      QuranUtils.getLocalizedNumber(page)
    )
    val nativeSpanned = HybridSearchHighlighter.buildForegroundHighlightedSpan(
      text = plainText,
      ranges = ranges,
      highlightColor = highlightColorArgb
    )

    return AyahSearchHit(
      id = id,
      sura = sura,
      ayah = ayah,
      page = page,
      suraName = suraName,
      text = plainText,
      highlightRanges = ranges,
      isArabic = isArabicText,
      ayahRefLabel = ayahRefLabel,
      pageLabel = pageLabel,
      precomputedAnnotatedText = null,
      precomputedNativeText = nativeSpanned,
      precomputedHighlightColorArgb = highlightColorArgb,
      precomputedHighlightBgArgb = highlightBgArgb
    )
  }

  private suspend fun executeTafsirSearch(gen: Long, query: String) {
    val installed = translationsDBAdapter.getTranslations().first()
    if (queryGeneration.get() != gen) return

    if (installed.isEmpty()) {
      pendingTafsirSource = null
      _uiState.update {
        it.copy(
          tafsirHits = emptyList(),
          totalTafsirHitCount = 0,
          hasMoreTafsirHits = false,
          hasSearchedTafsir = true,
          isLoading = false,
          noTafsirInstalled = true
        )
      }
      return
    }

    val arabicFallback = if (QuranUtils.doesStringContainArabic(query)) {
      ArabicCompiledQuery.compile(query)
    } else {
      null
    }

    val source: PendingTafsirSource? = context.contentResolver.query(
      QuranDataProvider.SEARCH_TAFSIR_URI,
      null,
      null,
      arrayOf(query),
      null
    )?.use { cursor ->
      val initialCap = cursor.count.coerceAtLeast(0)
      var ids = LongArray(initialCap)
      var suras = IntArray(initialCap)
      var ayahs = IntArray(initialCap)
      var translationIds = LongArray(initialCap)
      var occurrenceIndices = IntArray(initialCap)
      val rawTexts = ArrayList<String>(initialCap)
      val translatorNames = ArrayList<String>(initialCap)
      val languageCodes = ArrayList<String>(initialCap)

      val translatorIdx = cursor.getColumnIndex("translator_name")
      val translationIdIdx = cursor.getColumnIndex("translation_id")
      val langIdx = cursor.getColumnIndex("language_code")
      val seenKeyCounts = HashMap<String, Int>()
      var i = 0
      while (cursor.moveToNext()) {
        if (i >= ids.size) {
          val nextSize = maxOf(16, ids.size * 2)
          ids = ids.copyOf(nextSize)
          suras = suras.copyOf(nextSize)
          ayahs = ayahs.copyOf(nextSize)
          translationIds = translationIds.copyOf(nextSize)
          occurrenceIndices = occurrenceIndices.copyOf(nextSize)
        }
        val id = cursor.getLong(0)
        val sura = cursor.getInt(1)
        val ayah = cursor.getInt(2)
        val rawText = cursor.getString(3) ?: ""
        val translatorName = if (translatorIdx >= 0) cursor.getString(translatorIdx).orEmpty() else ""
        val translationId = if (translationIdIdx >= 0) cursor.getLong(translationIdIdx) else 0L
        val langCode = if (langIdx >= 0) cursor.getString(langIdx).orEmpty() else ""
        val baseKey = "${translationId}_${id}_${sura}_${ayah}"
        val occurrence = seenKeyCounts[baseKey] ?: 0
        seenKeyCounts[baseKey] = occurrence + 1

        ids[i] = id
        suras[i] = sura
        ayahs[i] = ayah
        translationIds[i] = translationId
        occurrenceIndices[i] = occurrence
        rawTexts.add(rawText)
        translatorNames.add(translatorName)
        languageCodes.add(langCode)
        i++
      }
      if (i == 0) {
        null
      } else {
        PendingTafsirSource(
          ids = if (i == ids.size) ids else ids.copyOf(i),
          suras = if (i == suras.size) suras else suras.copyOf(i),
          ayahs = if (i == ayahs.size) ayahs else ayahs.copyOf(i),
          rawTexts = rawTexts.toTypedArray(),
          translatorNames = translatorNames.toTypedArray(),
          translationIds = if (i == translationIds.size) translationIds else translationIds.copyOf(i),
          languageCodes = languageCodes.toTypedArray(),
          occurrenceIndices = if (i == occurrenceIndices.size) occurrenceIndices else occurrenceIndices.copyOf(i),
          arabicFallback = arabicFallback
        )
      }
    }

    if (queryGeneration.get() != gen) return

    val totalCount = source?.totalCount ?: 0
    val initialEnd = min(INITIAL_WINDOW_SIZE, totalCount)
    val initialHits: List<TafsirSearchHit> = if (source != null && initialEnd > 0) {
      withContext(computeDispatcher) {
        materializeTafsirWindow(
          source = source,
          startIndex = 0,
          endIndex = initialEnd
        )
      }
    } else {
      emptyList()
    }

    if (queryGeneration.get() != gen) return

    pendingTafsirSource = if (source != null && totalCount > TRANSIENT_TRIM_WINDOW_SIZE) source else null

    _uiState.update {
      it.copy(
        tafsirHits = initialHits,
        totalTafsirHitCount = totalCount,
        hasMoreTafsirHits = initialEnd < totalCount,
        hasSearchedTafsir = true,
        isLoading = false,
        noTafsirInstalled = false
      )
    }
  }

  private fun materializeTafsirWindow(
    source: PendingTafsirSource,
    startIndex: Int,
    endIndex: Int
  ): List<TafsirSearchHit> {
    if (endIndex <= startIndex) return emptyList()
    val list = ArrayList<TafsirSearchHit>(endIndex - startIndex)
    for (idx in startIndex until endIndex) {
      val sura = source.suras[idx]
      val ayah = source.ayahs[idx]
      val page = quranInfo.getPageFromSuraAyah(sura, ayah)
      val suraName = quranDisplayData.getSuraName(context, sura, wantPrefix = false)
      val (plainText, ranges) = HybridSearchHighlighter.extractPlainTextAndRanges(
        rawText = source.rawTexts[idx],
        arabicQueryFallback = source.arabicFallback
      )
      val langCode = source.languageCodes[idx]
      val isArabic = langCode == "ar" || QuranUtils.doesStringContainArabic(plainText)
      list.add(
        TafsirSearchHit(
          id = source.ids[idx],
          sura = sura,
          ayah = ayah,
          page = page,
          suraName = suraName,
          translatorName = source.translatorNames[idx],
          text = plainText,
          highlightRanges = ranges,
          isArabic = isArabic,
          translationId = source.translationIds[idx],
          occurrenceIndex = source.occurrenceIndices[idx]
        )
      )
    }
    return list
  }

  private fun unwrapRustCursor(cursor: Cursor): RustSearchResultsCursor? {
    var current: Cursor? = cursor
    while (current is CursorWrapper) {
      current = current.wrappedCursor
    }
    return current as? RustSearchResultsCursor
  }

  companion object {
    private const val TYPING_DEBOUNCE_MS = 160L
    internal const val INITIAL_WINDOW_SIZE = 40
    internal const val TRANSIENT_TRIM_WINDOW_SIZE = 16
    internal const val LOAD_MORE_BATCH_SIZE = 60
    internal const val LOAD_MORE_PREFETCH_THRESHOLD = 24
    internal const val STATIC_LAYOUT_CACHE_SIZE = 120
    internal const val REF_LAYOUT_CACHE_SIZE = 120
    internal const val PAGE_PILL_LAYOUT_CACHE_SIZE = 80
    internal const val PROGRESSIVE_PREBUILD_LIMIT = 120
    private const val PROGRESSIVE_PREBUILD_CHUNK_SIZE = 25

    private fun staticLayoutKey(sura: Int, ayah: Int): Int = (sura shl 16) or (ayah and 0xFFFF)
    private fun pagePillLayoutKey(page: Int, @ColorInt textColorArgb: Int): Long =
      (page.toLong() shl 32) or (textColorArgb.toLong() and 0xFFFFFFFFL)

    private val rustSearchExecutor = Executors.newSingleThreadExecutor { runnable ->
      Thread({
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY) }
        runnable.run()
      }, "quran-rust-search").apply { isDaemon = true }
    }
    private val defaultRustSearchDispatcher: CoroutineDispatcher = rustSearchExecutor.asCoroutineDispatcher()
  }
}
