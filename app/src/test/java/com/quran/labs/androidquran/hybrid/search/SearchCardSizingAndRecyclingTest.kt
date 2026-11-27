package com.quran.labs.androidquran.hybrid.search

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Canvas
import android.net.Uri
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class SearchCardSizingAndRecyclingTest {

  private lateinit var context: Context
  private lateinit var quranInfo: QuranInfo
  private lateinit var quranDisplayData: QuranDisplayData

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    quranInfo = QuranInfo(MadaniDataSource())
    quranDisplayData = QuranDisplayData(quranInfo)
    FakeSizingSearchProvider.reset()
  }

  @After
  fun tearDown() {
    FakeSizingSearchProvider.reset()
  }

  @Test
  fun `recycled AyahViewHolder expands 1-line to 10-line and shrinks 10-line to 1-line under identical MeasureSpecs with prebuilt and fallback layouts`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.BEIGE, isDark = false)
    val rv = RecyclerView(activity)

    val cardWidthPx = 680
    val cardPadH = HybridViewTokens.dp(activity, 14f)
    val availWidthPx = cardWidthPx - cardPadH * 2
    val paint = TextPaint().apply { textSize = 36f }

    val shortText = "Short verse line 1"
    val longText = (1..10).joinToString("\n") { line ->
      if (line == 10) "Line 10 contains al-Jannah match" else "Long verse line $line with Quranic text"
    }
    val longMatchStart = longText.indexOf("al-Jannah")
    val longRanges = listOf(HighlightSpanRange(longMatchStart, longMatchStart + "al-Jannah".length))

    val shortSpanned = HybridSearchHighlighter.buildForegroundHighlightedSpan(
      text = shortText,
      ranges = emptyList(),
      highlightColor = palette.searchHighlight
    )
    val longSpanned = HybridSearchHighlighter.buildForegroundHighlightedSpan(
      text = longText,
      ranges = longRanges,
      highlightColor = palette.searchHighlight
    )

    val shortLayout = StaticLayout.Builder.obtain(shortSpanned, 0, shortSpanned.length, paint, availWidthPx)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setMaxLines(Int.MAX_VALUE)
      .build()
    val longLayout = StaticLayout.Builder.obtain(longSpanned, 0, longSpanned.length, paint, availWidthPx)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setMaxLines(Int.MAX_VALUE)
      .build()
    assertThat(shortLayout.lineCount).isEqualTo(1)
    assertThat(longLayout.lineCount).isEqualTo(10)
    assertThat(longLayout.height).isGreaterThan(shortLayout.height * 5)

    val prebuiltMap = HashMap<Pair<Int, Int>, StaticLayout>()
    prebuiltMap[2 to 1] = shortLayout
    prebuiltMap[2 to 25] = longLayout

    val adapter = HybridSearchAdapter(
      onOpenPage = { _, _, _ -> },
      onPlayAyah = { _, _ -> },
      onToggleBookmark = { _, _, _ -> },
      onDownloadArabicDb = {},
      onOpenTranslationManager = {},
      staticLayoutProvider = { sura, ayah -> prebuiltMap[sura to ayah] }
    )
    adapter.updatePalette(palette)

    val shortPrebuiltHit = AyahSearchHit(
      id = 1L,
      sura = 2,
      ayah = 1,
      page = 2,
      suraName = "البقرة",
      text = shortText,
      highlightRanges = emptyList(),
      isArabic = true,
      ayahRefLabel = "سورة البقرة : ١",
      pageLabel = "ص ٢",
      precomputedNativeText = shortSpanned,
      precomputedHighlightColorArgb = palette.searchHighlight,
      precomputedHighlightBgArgb = palette.searchHighlightBg
    )
    val longPrebuiltHit = AyahSearchHit(
      id = 2L,
      sura = 2,
      ayah = 25,
      page = 5,
      suraName = "البقرة",
      text = longText,
      highlightRanges = longRanges,
      isArabic = true,
      ayahRefLabel = "سورة البقرة : ٢٥",
      pageLabel = "ص ٥",
      precomputedNativeText = longSpanned,
      precomputedHighlightColorArgb = palette.searchHighlight,
      precomputedHighlightBgArgb = palette.searchHighlightBg
    )
    // Fallback hits (not in prebuiltMap -> layout == null -> TextView path)
    val shortFallbackHit = shortPrebuiltHit.copy(id = 3L, ayah = 2, ayahRefLabel = "سورة البقرة : ٢")
    val longFallbackHit = longPrebuiltHit.copy(id = 4L, ayah = 266, ayahRefLabel = "سورة البقرة : ٢٦٦")

    adapter.submitList(
      listOf(
        HybridSearchRow.AyahCard(shortPrebuiltHit, isBookmarked = false),
        HybridSearchRow.AyahCard(longPrebuiltHit, isBookmarked = false),
        HybridSearchRow.AyahCard(shortFallbackHit, isBookmarked = false),
        HybridSearchRow.AyahCard(longFallbackHit, isBookmarked = false)
      )
    )

    val holder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(0))
    val card = holder.itemView as ViewGroup
    val verseView = card.findViewById<HybridSearchAdapter.StaticLayoutVerseView>(R.id.verseText)
    assertThat(verseView.maxLines).isEqualTo(Int.MAX_VALUE)
    assertThat(verseView.ellipsize).isNull()

    val widthSpec = View.MeasureSpec.makeMeasureSpec(cardWidthPx, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    fun measureAndLayoutCard() {
      card.measure(widthSpec, heightSpec)
      card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    }

    // 1. Prebuilt 1-line -> Prebuilt 10-line -> Prebuilt 1-line with IDENTICAL MeasureSpecs
    adapter.onBindViewHolder(holder, 0)
    assertThat(verseView.hasPrebuiltLayout()).isTrue()
    measureAndLayoutCard()
    val shortPrebuiltVerseH = verseView.measuredHeight
    val shortPrebuiltCardH = card.measuredHeight
    assertThat(shortPrebuiltVerseH).isEqualTo(shortLayout.height)

    adapter.onBindViewHolder(holder, 1)
    assertThat(verseView.hasPrebuiltLayout()).isTrue()
    measureAndLayoutCard()
    val longPrebuiltVerseH = verseView.measuredHeight
    val longPrebuiltCardH = card.measuredHeight
    assertThat(longPrebuiltVerseH).isEqualTo(longLayout.height)
    assertThat(longPrebuiltCardH - shortPrebuiltCardH).isEqualTo(longLayout.height - shortLayout.height)

    // Verify line-10 highlight rect falls strictly inside 0..verseView.measuredHeight
    val highlightRects = verseView.computeHighlightRectsForTest(longLayout)
    assertThat(highlightRects).hasSize(1)
    assertThat(highlightRects.single().top).isAtLeast(0f)
    assertThat(highlightRects.single().bottom).isAtMost(verseView.measuredHeight.toFloat())

    // Shrink back 10-line -> 1-line: zero leftover whitespace
    adapter.onBindViewHolder(holder, 0)
    measureAndLayoutCard()
    assertThat(verseView.measuredHeight).isEqualTo(shortPrebuiltVerseH)
    assertThat(card.measuredHeight).isEqualTo(shortPrebuiltCardH)

    // 2. Fallback (layout == null) 1-line -> 10-line -> 1-line with IDENTICAL MeasureSpecs
    adapter.onBindViewHolder(holder, 2)
    assertThat(verseView.hasPrebuiltLayout()).isFalse()
    measureAndLayoutCard()
    val shortFallbackVerseH = verseView.measuredHeight
    val shortFallbackCardH = card.measuredHeight
    assertThat(shortFallbackVerseH).isGreaterThan(0)

    adapter.onBindViewHolder(holder, 3)
    assertThat(verseView.hasPrebuiltLayout()).isFalse()
    measureAndLayoutCard()
    val longFallbackVerseH = verseView.measuredHeight
    val longFallbackCardH = card.measuredHeight
    assertThat(longFallbackVerseH).isGreaterThan(shortFallbackVerseH * 5)
    assertThat(longFallbackCardH - shortFallbackCardH).isEqualTo(longFallbackVerseH - shortFallbackVerseH)

    // Shrink back fallback 10-line -> 1-line
    adapter.onBindViewHolder(holder, 2)
    measureAndLayoutCard()
    assertThat(verseView.measuredHeight).isEqualTo(shortFallbackVerseH)
    assertThat(card.measuredHeight).isEqualTo(shortFallbackCardH)

    // 3. Mixed transition: fallback 1-line -> prebuilt 10-line -> fallback 1-line
    // (catches stale TextView.mLayout matching previous fallback height while measuredHeight was tall)
    adapter.onBindViewHolder(holder, 1)
    measureAndLayoutCard()
    assertThat(verseView.measuredHeight).isEqualTo(longPrebuiltVerseH)

    adapter.onBindViewHolder(holder, 2)
    measureAndLayoutCard()
    assertThat(verseView.measuredHeight).isEqualTo(shortFallbackVerseH)
    assertThat(card.measuredHeight).isEqualTo(shortFallbackCardH)
  }

  @Test
  fun `StaticLayoutVerseView and StaticLayoutSingleLineView force-remeasure even when isInLayout is true`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val paint = TextPaint().apply { textSize = 36f }
    val widthPx = 500
    val shortText = "One line"
    val longText = (1..9).joinToString("\n") { "Line $it text content" }
    val shortLayout = StaticLayout.Builder.obtain(shortText, 0, shortText.length, paint, widthPx)
      .setLineSpacing(0f, 1.42f)
      .setMaxLines(Int.MAX_VALUE)
      .build()
    val longLayout = StaticLayout.Builder.obtain(longText, 0, longText.length, paint, widthPx)
      .setLineSpacing(0f, 1.42f)
      .setMaxLines(Int.MAX_VALUE)
      .build()

    var simulateInLayout = false
    var relayoutCalls = 0
    val host = FrameLayout(activity)
    val verseView = object : HybridSearchAdapter.StaticLayoutVerseView(activity) {
      override fun isInLayout(): Boolean = simulateInLayout
      override fun requestLayout() {
        relayoutCalls++
        super.requestLayout()
      }
    }
    host.addView(
      verseView,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    )

    val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
    val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    verseView.setPrebuiltContent(shortText, shortLayout)
    host.measure(wSpec, hSpec)
    host.layout(0, 0, host.measuredWidth, host.measuredHeight)
    assertThat(verseView.measuredHeight).isEqualTo(shortLayout.height)
    assertThat(host.measuredHeight).isEqualTo(shortLayout.height)

    // Simulate RecyclerView.layoutChunk rebinding while isInLayout == true
    simulateInLayout = true
    val relayoutsBeforeInLayoutBind = relayoutCalls
    verseView.setPrebuiltContent(longText, longLayout)
    assertThat(relayoutCalls).isEqualTo(relayoutsBeforeInLayoutBind)

    // Re-measure parent with IDENTICAL MeasureSpecs -> must NOT hit stale measure cache!
    host.measure(wSpec, hSpec)
    host.layout(0, 0, host.measuredWidth, host.measuredHeight)
    assertThat(verseView.measuredHeight).isEqualTo(longLayout.height)
    assertThat(host.measuredHeight).isEqualTo(longLayout.height)

    // Shrink back to 1-line while isInLayout == true
    verseView.setPrebuiltContent(shortText, shortLayout)
    host.measure(wSpec, hSpec)
    host.layout(0, 0, host.measuredWidth, host.measuredHeight)
    assertThat(verseView.measuredHeight).isEqualTo(shortLayout.height)
    assertThat(host.measuredHeight).isEqualTo(shortLayout.height)

    // Transition to fallback (layout == null) while isInLayout == true -> must expand cleanly
    verseView.setPrebuiltContent(longText, null)
    host.measure(wSpec, hSpec)
    host.layout(0, 0, host.measuredWidth, host.measuredHeight)
    assertThat(verseView.measuredHeight).isGreaterThan(shortLayout.height * 4)
    assertThat(host.measuredHeight).isEqualTo(verseView.measuredHeight)

    // Verify StaticLayoutSingleLineView also force-remeasures when width changes during isInLayout == true
    val singleHost = FrameLayout(activity)
    val singleView = object : HybridSearchAdapter.StaticLayoutSingleLineView(activity) {
      override fun isInLayout(): Boolean = simulateInLayout
    }
    singleHost.addView(
      singleView,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    )
    val narrowLayout = StaticLayout.Builder.obtain("ص ١", 0, 3, paint, 80).setMaxLines(1).build()
    val wideLayout = StaticLayout.Builder.obtain("ص ٦٠٤", 0, 5, paint, 160).setMaxLines(1).build()
    val atMostW = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST)

    simulateInLayout = false
    singleView.setPrebuiltContent("ص ١", narrowLayout)
    singleHost.measure(atMostW, hSpec)
    singleHost.layout(0, 0, singleHost.measuredWidth, singleHost.measuredHeight)
    assertThat(singleView.measuredWidth).isEqualTo(narrowLayout.width)

    simulateInLayout = true
    singleView.setPrebuiltContent("ص ٦٠٤", wideLayout)
    singleHost.measure(atMostW, hSpec)
    singleHost.layout(0, 0, singleHost.measuredWidth, singleHost.measuredHeight)
    assertThat(singleView.measuredWidth).isEqualTo(wideLayout.width)
  }

  @Test
  fun `TafsirViewHolder removes 5-line cap and resizes accurately across recycled short and long snippets`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.BEIGE, isDark = false)
    val rv = RecyclerView(activity)
    val adapter = HybridSearchAdapter(
      onOpenPage = { _, _, _ -> },
      onPlayAyah = { _, _ -> },
      onToggleBookmark = { _, _, _ -> },
      onDownloadArabicDb = {},
      onOpenTranslationManager = {}
    )
    adapter.updatePalette(palette)

    val shortSnippet = "Brief tafsir line"
    val longSnippet = (1..8).joinToString("\n") { "Tafsir explanation line $it about al-Jannah" }
    val shortHit = TafsirSearchHit(
      id = 1L,
      sura = 2,
      ayah = 25,
      page = 5,
      suraName = "البقرة",
      translatorName = "التفسير الميسر",
      text = shortSnippet,
      highlightRanges = emptyList(),
      isArabic = false,
      translationId = 1L
    )
    val longHit = shortHit.copy(id = 2L, ayah = 266, text = longSnippet)

    adapter.submitList(
      listOf(
        HybridSearchRow.TafsirCard(shortHit, isBookmarked = false),
        HybridSearchRow.TafsirCard(longHit, isBookmarked = false)
      )
    )

    val holder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(0))
    val card = holder.itemView as ViewGroup
    val snippetView = (0 until card.childCount)
      .map { card.getChildAt(it) }
      .filterIsInstance<HybridSearchAdapter.StaticLayoutVerseView>()
      .single()

    assertThat(snippetView.maxLines).isEqualTo(Int.MAX_VALUE)
    assertThat(snippetView.ellipsize).isNull()

    val widthSpec = View.MeasureSpec.makeMeasureSpec(680, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    adapter.onBindViewHolder(holder, 0)
    card.measure(widthSpec, heightSpec)
    card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    val shortSnippetH = snippetView.measuredHeight
    val shortCardH = card.measuredHeight

    adapter.onBindViewHolder(holder, 1)
    card.measure(widthSpec, heightSpec)
    card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    val longSnippetH = snippetView.measuredHeight
    val longCardH = card.measuredHeight
    assertThat(snippetView.lineCount).isEqualTo(8)
    assertThat(longSnippetH).isGreaterThan(shortSnippetH * 5)
    assertThat(longCardH - shortCardH).isEqualTo(longSnippetH - shortSnippetH)

    // Shrink back to 1-line snippet
    adapter.onBindViewHolder(holder, 0)
    card.measure(widthSpec, heightSpec)
    card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    assertThat(snippetView.measuredHeight).isEqualTo(shortSnippetH)
    assertThat(card.measuredHeight).isEqualTo(shortCardH)
  }

  @Test
  fun `HybridSearchEngine builds uncapped StaticLayout for long verses past line 6 without ellipsis truncation`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    // Multi-line verse (>6 lines) where الجنة appears on the final line
    val longBaqarahVerse =
      "وَبَشِّرِ الَّذِينَ آمَنُوا وَعَمِلُوا الصَّالِحَاتِ أَنَّ لَهُمْ\n" +
        "جَنَّاتٍ تَجْرِي مِن تَحْتِهَا الْأَنْهَارُ كُلَّمَا رُزِقُوا مِنْهَا\n" +
        "مِن ثَمَرَةٍ رِّزْقًا قَالُوا هَٰذَا الَّذِي رُزِقْنَا مِن قَبْلُ\n" +
        "وَأُتُوا بِهِ مُتَشَابِهًا وَلَهُمْ فِيهَا أَزْوَاجٌ مُّطَهَّرَةٌ\n" +
        "وَهُمْ فِيهَا خَالِدُونَ أَيَوَدُّ أَحَدُكُمْ أَن تَكُونَ لَهُ\n" +
        "نَخِيلٌ وَأَعْنَابٌ تَجْرِي مِن تَحْتِهَا الْأَنْهَارُ\n" +
        "لَهُ فِيهَا مِن كُلِّ الثَّمَرَاتِ وَأَصَابَهُ الْكِبَرُ\n" +
        "وَأَصْحَابُ الجنة هُمُ الْفَائِزُونَ"

    registerFakeProvider { _, _ ->
      MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
        addRow(arrayOf<Any>(25L, 2, 25, longBaqarahVerse))
      }
    }

    val prefs = context.getSharedPreferences("sizing_long_verse_test", Context.MODE_PRIVATE).apply {
      edit().clear().commit()
    }

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = RecentSearchRepository(prefs),
        hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )
      val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
      val palette = HybridViewPalette.from(AppThemeStyle.BEIGE, isDark = false)
      engine.updateHighlightPalette(
        androidx.compose.ui.graphics.Color(palette.searchHighlight),
        androidx.compose.ui.graphics.Color(palette.searchHighlightBg)
      )
      engine.updateExpectedVerseAvailWidthPx(540)
      engine.submitQuery("جنة", scopeOverride = HybridSearchScope.AYAHS)

      val hit = engine.uiState.value.ayahHits.single()
      val cachedLayout = engine.getCachedStaticLayout(2, 25)
      assertThat(cachedLayout).isNotNull()
      val layout = cachedLayout!!
      assertThat(layout.lineCount).isAtLeast(8)
      assertThat(layout.getEllipsisCount(layout.lineCount - 1)).isEqualTo(0)
      assertThat(layout.getLineEnd(layout.lineCount - 1)).isEqualTo(longBaqarahVerse.length)

      // Render inside AyahViewHolder and verify full unclipped height + visible highlight rect
      val rv = RecyclerView(activity)
      val adapter = HybridSearchAdapter(
        onOpenPage = { _, _, _ -> },
        onPlayAyah = { _, _ -> },
        onToggleBookmark = { _, _, _ -> },
        onDownloadArabicDb = {},
        onOpenTranslationManager = {},
        staticLayoutProvider = { s, a -> engine.getCachedStaticLayout(s, a) }
      )
      adapter.updatePalette(palette)
      adapter.submitList(listOf(HybridSearchRow.AyahCard(hit, isBookmarked = false)))

      val holder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(0))
      adapter.onBindViewHolder(holder, 0)
      val card = holder.itemView as ViewGroup
      val verseView = card.findViewById<HybridSearchAdapter.StaticLayoutVerseView>(R.id.verseText)
      assertThat(verseView.hasPrebuiltLayout()).isTrue()
      val cardW = 540 + HybridViewTokens.dp(activity, 14f) * 2
      card.measure(
        View.MeasureSpec.makeMeasureSpec(cardW, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
      )
      card.layout(0, 0, card.measuredWidth, card.measuredHeight)
      card.draw(Canvas())

      assertThat(verseView.measuredHeight).isEqualTo(layout.height)
      assertThat(hit.highlightRanges).isNotEmpty()
      val matchIdx = longBaqarahVerse.indexOf("الجنة")
      assertThat(matchIdx).isGreaterThan(0)
      val targetLine = layout.getLineForOffset(matchIdx)
      assertThat(targetLine).isGreaterThan(5) // strictly beyond old maxLines=6 cap
      assertThat(layout.getLineBottom(targetLine)).isAtMost(verseView.measuredHeight)
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `consecutive Ayah queries on the same sura-ayah invalidate staticLayoutCache while preserving ref and page pill caches`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    val verse = "وَقُلْنَا يَا آدَمُ اسْكُنْ أَنتَ وَزَوْجُكَ الْجَنَّةَ وَكُلَا مِنْهَا رَغَدًا"

    registerFakeProvider { _, _ ->
      MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
        addRow(arrayOf<Any>(35L, 2, 35, verse))
      }
    }

    val prefs = context.getSharedPreferences("sizing_stale_cache_test", Context.MODE_PRIVATE).apply {
      edit().clear().commit()
    }

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = RecentSearchRepository(prefs),
        hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      // Query 1: "آدم"
      engine.submitQuery("آدم", scopeOverride = HybridSearchScope.AYAHS)
      val layout1 = engine.getCachedStaticLayout(2, 35)
      val refLayout1 = engine.getCachedRefStaticLayout(2, 35)
      assertThat(layout1).isNotNull()
      assertThat(refLayout1).isNotNull()

      val spanned1 = layout1!!.text as Spanned
      val spans1 = spanned1.getSpans(0, spanned1.length, ForegroundColorSpan::class.java)
      assertThat(spans1).isNotEmpty()
      val highlighted1 = spanned1.subSequence(
        spanned1.getSpanStart(spans1.first()),
        spanned1.getSpanEnd(spans1.first())
      ).toString()
      assertThat(highlighted1).contains("آدَم")

      // Query 2 directly without clearing: "الجنة" on the exact same (2, 35)
      engine.submitQuery("الجنة", scopeOverride = HybridSearchScope.AYAHS)
      val layout2 = engine.getCachedStaticLayout(2, 35)
      val refLayout2 = engine.getCachedRefStaticLayout(2, 35)
      assertThat(layout2).isNotNull()
      assertThat(layout2).isNotSameInstanceAs(layout1)
      // Query-independent ref cache stayed warm
      assertThat(refLayout2).isSameInstanceAs(refLayout1)

      val spanned2 = layout2!!.text as Spanned
      val spans2 = spanned2.getSpans(0, spanned2.length, ForegroundColorSpan::class.java)
      assertThat(spans2).isNotEmpty()
      val highlighted2 = spanned2.subSequence(
        spanned2.getSpanStart(spans2.first()),
        spanned2.getSpanEnd(spans2.first())
      ).toString()
      assertThat(highlighted2).contains("الْجَنَّة")
      assertThat(highlighted2).doesNotContain("آدَم")
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `NativeHybridSearchView syncs exact card availWidthPx to HybridSearchEngine with zero rounding discrepancy`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val prefs = context.getSharedPreferences("sizing_width_sync_test", Context.MODE_PRIVATE).apply {
      edit().clear().commit()
    }

    try {
      val engine = HybridSearchEngine(
        context = activity,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(activity),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = RecentSearchRepository(prefs),
        hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      val searchView = NativeHybridSearchView(activity)
      searchView.bind(
        searchEngine = engine,
        handler = object : com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler {
          override fun openPage(page: Int, sura: Int, ayah: Int) = Unit
          override fun playAyah(sura: Int, ayah: Int, page: Int) = Unit
          override fun toggleBookmark(sura: Int, ayah: Int, page: Int) = Unit
        },
        scope = engineScope,
        lifecycleOwner = activity
      )

      val hostWidthPx = 720
      searchView.measure(
        View.MeasureSpec.makeMeasureSpec(hostWidthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY)
      )
      searchView.layout(0, 0, hostWidthPx, 1280)

      val outerPad = HybridViewTokens.dp(activity, 14f)
      val cardPad = HybridViewTokens.dp(activity, 14f)
      val expectedCardAvailW = hostWidthPx - outerPad * 2 - cardPad * 2
      assertThat(engine.expectedAvailWidthPxForTest()).isEqualTo(expectedCardAvailW)
      assertThat(searchView.resultsRecyclerView.width - cardPad * 2).isEqualTo(expectedCardAvailW)

      searchView.unbind()
    } finally {
      engineScope.cancel()
    }
  }

  private fun registerFakeProvider(handler: (Uri, Array<out String>?) -> Cursor?) {
    FakeSizingSearchProvider.queryHandler = handler
    Robolectric.buildContentProvider(FakeSizingSearchProvider::class.java)
      .create(QuranDataProvider.SEARCH_URI.authority!!)
  }

  class FakeSizingSearchProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?
    ): Cursor? = queryHandler(uri, selectionArgs)

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
      @Volatile
      var queryHandler: (Uri, Array<out String>?) -> Cursor? = { _, _ -> null }
      fun reset() {
        queryHandler = { _, _ -> null }
      }
    }
  }
}
