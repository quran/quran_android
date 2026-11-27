package com.quran.labs.androidquran.hybrid.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Looper
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.SearchActivity
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.RubElHizbView
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridSearchDuoToneAndFloatationTest {

  @Test
  fun `NativeHybridSearchView applies Z0 ambient glow, Z2 floating search pill, Z1 recent chips, and Color-1 active scope pill`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.GRAY, isDark = true)
    val searchView = NativeHybridSearchView(activity)
    searchView.applyPalette(palette)
    searchView.render(
      HybridSearchUiState(
        query = "الله",
        scope = HybridSearchScope.AYAHS,
        totalAyahHitCount = 42,
        hasSearchedAyahs = true,
        recentQueries = listOf("الله", "الرحمن")
      )
    )

    // Z0 ambient glow canvas
    assertThat(searchView.background).isInstanceOf(GradientDrawable::class.java)
    assertThat((searchView.background as GradientDrawable).orientation)
      .isEqualTo(GradientDrawable.Orientation.TOP_BOTTOM)

    // Z2 floating search pill (child 0)
    val searchBar = searchView.getChildAt(0) as ViewGroup
    assertThat(searchBar.background).isInstanceOf(LayerDrawable::class.java)
    assertThat(searchBar.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(2, isDark = true))
    )

    // Hit count badge uses Color 2 (editorialAccent)
    val hitBadge = searchBar.findViewWithTag<TextView>(SearchTestTags.HIT_COUNT_BADGE)
    assertThat(hitBadge.visibility).isEqualTo(View.VISIBLE)
    assertThat(hitBadge.currentTextColor).isEqualTo(palette.editorialAccent)
    assertThat(searchView.clearButton.drawable).isInstanceOf(VectorPathDrawable::class.java)

    // Recent chips: Z1 subtle float + CLOCK architectural icon
    val recentChip = searchView.findViewById<LinearLayout>(R.id.recent_chip)
    assertThat(recentChip).isNotNull()
    assertThat(recentChip.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(1, isDark = true))
    )
    assertThat((recentChip.getChildAt(0) as ImageView).drawable)
      .isInstanceOf(VectorPathDrawable::class.java)

    // Scope pills: active in ctaBackground/onCta with Z1 shadow; inactive flat
    val ayahsScope = searchView.findViewWithTag<TextView>(SearchTestTags.SCOPE_AYAHS)
    val surasScope = searchView.findViewWithTag<TextView>(SearchTestTags.SCOPE_SURAS)
    assertThat(ayahsScope.currentTextColor).isEqualTo(palette.onCta)
    assertThat(ayahsScope.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(1, isDark = true))
    )
    assertThat(surasScope.currentTextColor).isEqualTo(palette.textSecondary)
    assertThat(surasScope.elevation).isEqualTo(0f)
  }

  @Test
  fun `HybridSearchAdapter applies Z1 floatation, Color-2 bookmark saved state, Color-1 open page, and Color-2 sura medallion`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.GREEN, isDark = false)
    val rv = RecyclerView(activity)
    val adapter = HybridSearchAdapter(
      onOpenPage = { _, _, _ -> },
      onPlayAyah = { _, _ -> },
      onToggleBookmark = { _, _, _ -> },
      onDownloadArabicDb = {},
      onOpenTranslationManager = {}
    )
    adapter.updatePalette(palette)

    val ayahHit = AyahSearchHit(
      id = 1L,
      sura = 1,
      ayah = 1,
      page = 1,
      suraName = "الفاتحة",
      text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      highlightRanges = listOf(HighlightSpanRange(7, 13)),
      isArabic = true,
      ayahRefLabel = "سورة الفاتحة : ١",
      pageLabel = "ص ١"
    )
    val suraHit = SuraSearchHit(
      sura = 1,
      displayName = "الفاتحة",
      metaString = "مكية، ٧ آيات",
      juzNumber = 1,
      startPage = 1,
      ayahCount = 7,
      isMakki = true
    )

    adapter.submitList(
      listOf(
        HybridSearchRow.AyahCard(ayahHit, isBookmarked = false),
        HybridSearchRow.AyahCard(ayahHit.copy(id = 2L, ayah = 2), isBookmarked = true),
        HybridSearchRow.SuraCard(suraHit)
      )
    )

    // Unsaved Ayah card
    val ayahHolder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(0))
    adapter.onBindViewHolder(ayahHolder, 0)
    val card = ayahHolder.itemView as ViewGroup
    assertThat(card.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(1, isDark = false))
    )
    val listenBtn = card.findViewWithTag<TextView>(ayahHit.listenTag)
    val bookmarkBtn = card.findViewWithTag<TextView>(ayahHit.bookmarkTag)
    val openBtn = card.findViewWithTag<TextView>(ayahHit.openPageTag)
    assertThat(listenBtn.currentTextColor).isEqualTo(palette.textSecondary)
    assertThat(bookmarkBtn.currentTextColor).isEqualTo(palette.textSecondary)
    assertThat(openBtn.currentTextColor).isEqualTo(palette.accent)
    val unsavedIcon = bookmarkBtn.compoundDrawablesRelative[0]
    assertThat(unsavedIcon).isInstanceOf(VectorPathDrawable::class.java)

    // Saved Ayah card swaps bookmark icon & tints label in Color 2 (editorialAccent)
    adapter.onBindViewHolder(ayahHolder, 1)
    assertThat(bookmarkBtn.currentTextColor).isEqualTo(palette.editorialAccent)
    val savedIcon = bookmarkBtn.compoundDrawablesRelative[0]
    assertThat(savedIcon).isInstanceOf(VectorPathDrawable::class.java)
    assertThat(savedIcon).isNotSameInstanceAs(unsavedIcon)

    card.measure(
      View.MeasureSpec.makeMeasureSpec(680, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    card.draw(Canvas())

    // Sura result card: Z1 float + Color 2 RubElHizbView + Color 1 page pill
    val suraHolder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(2))
    adapter.onBindViewHolder(suraHolder, 2)
    val suraCard = suraHolder.itemView as ViewGroup
    assertThat(suraCard.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(1, isDark = false))
    )
    val medallion = (0 until suraCard.childCount)
      .map { suraCard.getChildAt(it) }
      .filterIsInstance<RubElHizbView>()
      .single()
    assertThat(medallion.medallionBorderColorForTest).isEqualTo(palette.editorialAccent)
  }

  @Test
  fun `SearchActivity hosts Z0 ambient glow canvas and non-interactive scroll-under fade veil behind Z3 FloatingBottomNavView`() {
    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "")
    val controller = Robolectric.buildActivity(SearchActivity::class.java, intent)
    val activity = controller.create().start().resume().get()
    try {
      assertThat(activity.rootView.background).isInstanceOf(GradientDrawable::class.java)
      assertThat(activity.bottomFadeView.isClickable).isFalse()
      assertThat(activity.bottomFadeView.isFocusable).isFalse()
      assertThat(activity.bottomFadeView.importantForAccessibility)
        .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
      assertThat(activity.bottomFadeView.background).isInstanceOf(GradientDrawable::class.java)
      assertThat((activity.bottomFadeView.background as GradientDrawable).orientation)
        .isEqualTo(GradientDrawable.Orientation.BOTTOM_TOP)

      // Veil sits strictly behind bottomNavView
      val veilIdx = activity.rootView.indexOfChild(activity.bottomFadeView)
      val navIdx = activity.rootView.indexOfChild(activity.bottomNavView)
      assertThat(veilIdx).isGreaterThan(-1)
      assertThat(navIdx).isGreaterThan(veilIdx)
    } finally {
      controller.pause().stop().destroy()
    }
  }

  @Test
  fun `StaticLayoutVerseView draws glyph-clamped compact highlight rects without BackgroundColorSpan pillars`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false)

    // 1. Foreground-only span on Arabic verse preserves Uthmani ligatures and never attaches BackgroundColorSpan
    val arabicVerse = "ختم الله على قلوبهم\nوعلى سمعهم وعلى أبصارهم غشاوة ولهم عذاب عظيم"
    val arabicStart = arabicVerse.indexOf("الله")
    val arabicEnd = arabicStart + "الله".length
    val arabicRanges = listOf(HighlightSpanRange(arabicStart, arabicEnd))
    val arabicSpanned = HybridSearchHighlighter.buildForegroundHighlightedSpan(
      text = arabicVerse,
      ranges = arabicRanges,
      highlightColor = palette.searchHighlight
    ) as Spanned
    assertThat(arabicSpanned.getSpans(0, arabicSpanned.length, ForegroundColorSpan::class.java)).hasLength(1)
    assertThat(arabicSpanned.getSpans(0, arabicSpanned.length, BackgroundColorSpan::class.java)).isEmpty()
    assertThat(arabicSpanned.getSpans(0, arabicSpanned.length, StyleSpan::class.java)).isEmpty()
    assertThat(arabicSpanned.getSpans(0, arabicSpanned.length, TypefaceSpan::class.java)).isEmpty()

    // 2. Multi-line StaticLayout with 1.42x lineSpacingMultiplier: highlight rect on line 0
    // must be clamped strictly to unmultiplied glyph ascent..descent (never stretching into
    // inter-line whitespace) and match single-line height. (Explicit \n + Latin glyphs keep
    // horizontal advances and line wrapping deterministic under Robolectric's bundled Roboto font.)
    val paint = TextPaint().apply { textSize = 40f }
    val multiText = "Khatama Allah ala qulubihim\nwa ala samihim wa ala absarihim"
    val matchStart = multiText.indexOf("Allah")
    val ranges = listOf(HighlightSpanRange(matchStart, matchStart + "Allah".length))
    val spanned = HybridSearchHighlighter.buildForegroundHighlightedSpan(
      text = multiText,
      ranges = ranges,
      highlightColor = palette.searchHighlight
    )
    val multiLayout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, 800)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setMaxLines(6)
      .build()
    assertThat(multiLayout.lineCount).isGreaterThan(1)

    val singleText = "Bism Allah al-Rahman al-Rahim"
    val singleStart = singleText.indexOf("Allah")
    val singleRanges = listOf(HighlightSpanRange(singleStart, singleStart + "Allah".length))
    val singleSpanned = HybridSearchHighlighter.buildForegroundHighlightedSpan(
      text = singleText,
      ranges = singleRanges,
      highlightColor = palette.searchHighlight
    )
    val singleLayout = StaticLayout.Builder.obtain(singleSpanned, 0, singleSpanned.length, paint, 800)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setMaxLines(6)
      .build()
    assertThat(singleLayout.lineCount).isEqualTo(1)

    val verseView = HybridSearchAdapter.StaticLayoutVerseView(activity)
    verseView.setPrebuiltContent(spanned, multiLayout, ranges, palette.searchHighlightBg)
    val multiRects = verseView.computeHighlightRectsForTest(multiLayout)
    assertThat(multiRects).hasSize(1)
    val line0SpanHeight = (multiLayout.getLineBottom(0) - multiLayout.getLineTop(0)).toFloat()
    assertThat(multiRects.single().height()).isLessThan(line0SpanHeight)

    verseView.setPrebuiltContent(singleSpanned, singleLayout, singleRanges, palette.searchHighlightBg)
    val singleRects = verseView.computeHighlightRectsForTest(singleLayout)
    assertThat(singleRects).hasSize(1)
    assertThat(multiRects.single().height()).isWithin(2.5f).of(singleRects.single().height())

    // Draw both RTL Arabic and multi-line layouts to verify zero runtime exceptions
    val arabicLayout = StaticLayout.Builder.obtain(arabicSpanned, 0, arabicSpanned.length, paint, 600)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setTextDirection(TextDirectionHeuristics.RTL)
      .setMaxLines(6)
      .build()
    verseView.setPrebuiltContent(arabicSpanned, arabicLayout, arabicRanges, palette.searchHighlightBg)
    verseView.measure(
      View.MeasureSpec.makeMeasureSpec(arabicLayout.width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    verseView.layout(0, 0, verseView.measuredWidth, verseView.measuredHeight)
    verseView.draw(Canvas())
  }

  @Test
  fun `NativeHybridSearchView omits latency speed badge even when rustElapsedMs is populated`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val searchView = NativeHybridSearchView(activity)
    searchView.applyPalette(HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false))
    searchView.render(
      HybridSearchUiState(
        query = "الله",
        scope = HybridSearchScope.AYAHS,
        totalAyahHitCount = 19,
        hasSearchedAyahs = true,
        rustElapsedMs = 1.9
      )
    )

    val allTextViews = ArrayList<TextView>()
    fun collect(v: View) {
      if (v is TextView) allTextViews.add(v)
      if (v is ViewGroup) {
        for (i in 0 until v.childCount) collect(v.getChildAt(i))
      }
    }
    collect(searchView)

    assertThat(searchView.findViewWithTag<View>(SearchTestTags.RUST_SPEED_BADGE)).isNull()
    assertThat(allTextViews.none { it.text.toString().contains("مللي") || it.text.toString().contains("ms") }).isTrue()

    val hitBadge = searchView.findViewWithTag<TextView>(SearchTestTags.HIT_COUNT_BADGE)
    assertThat(hitBadge.visibility).isEqualTo(View.VISIBLE)
    assertThat(searchView.findViewWithTag<View>(SearchTestTags.SCOPE_AYAHS).visibility).isEqualTo(View.VISIBLE)
    assertThat(searchView.findViewWithTag<View>(SearchTestTags.SCOPE_SURAS).visibility).isEqualTo(View.VISIBLE)
    assertThat(searchView.findViewWithTag<View>(SearchTestTags.SCOPE_TAFSIR).visibility).isEqualTo(View.VISIBLE)

    // Paginated TAFSIR scope displays totalTafsirHitCount in hit count badge (not just loaded window size)
    val initialWindow = (1..40).map { idx ->
      TafsirSearchHit(
        id = idx.toLong(),
        sura = 2,
        ayah = idx,
        page = 2,
        suraName = "البقرة",
        translatorName = "التفسير الميسر",
        text = "تفسير آية الله",
        highlightRanges = emptyList(),
        isArabic = true,
        translationId = 1L
      )
    }
    searchView.render(
      HybridSearchUiState(
        query = "الله",
        scope = HybridSearchScope.TAFSIR,
        tafsirHits = initialWindow,
        totalTafsirHitCount = 240,
        hasMoreTafsirHits = true,
        hasSearchedTafsir = true,
        rustElapsedMs = 3.4
      )
    )
    assertThat(hitBadge.visibility).isEqualTo(View.VISIBLE)
    assertThat(hitBadge.text.toString()).contains(HybridSearchAdapter.formatCountLocalized(240))
    assertThat(searchView.resultsRecyclerView.adapter?.itemCount).isEqualTo(40)
    assertThat(searchView.findViewWithTag<View>(SearchTestTags.RUST_SPEED_BADGE)).isNull()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `NativeHybridSearchView restoreInputIfNeeded and SearchActivity onResume recover input focus and watcher`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = activity.getSharedPreferences("search_refocus_test", Context.MODE_PRIVATE)
      .apply { edit().clear().commit() }
    try {
      val engine = HybridSearchEngine(
        context = activity,
        quranInfo = quranInfo,
        quranDisplayData = QuranDisplayData(quranInfo),
        quranFileUtils = FakeQuranFileUtils.create(activity),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = RecentSearchRepository(prefs),
        hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
        scope = scope,
        ioDispatcher = UnconfinedTestDispatcher(),
        computeDispatcher = UnconfinedTestDispatcher()
      )
      val view = NativeHybridSearchView(activity)
      activity.setContentView(view)
      view.bind(
        searchEngine = engine,
        handler = object : HybridSearchActionHandler {
          override fun openPage(page: Int, sura: Int, ayah: Int) = Unit
          override fun playAyah(sura: Int, ayah: Int, page: Int) = Unit
          override fun toggleBookmark(sura: Int, ayah: Int, page: Int) = Unit
        },
        scope = scope,
        lifecycleOwner = activity
      )

      view.releaseFocusAndInput()
      assertThat(view.searchInput.isFocusable).isFalse()
      assertThat(view.searchInput.isFocusableInTouchMode).isFalse()

      // Tapping anywhere on the floating search pill restores focusability and TextWatcher
      val searchBar = view.getChildAt(0)
      assertThat(searchBar.performClick()).isTrue()
      assertThat(view.searchInput.isEnabled).isTrue()
      assertThat(view.searchInput.isFocusable).isTrue()
      assertThat(view.searchInput.isFocusableInTouchMode).isTrue()

      view.searchInput.setText("الرحمن")
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertThat(engine.uiState.value.query).isEqualTo("الرحمن")
      view.unbind()
    } finally {
      scope.cancel()
    }

    // Standalone SearchActivity restores search input focusability across stop -> resume
    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "")
    val controller = Robolectric.buildActivity(SearchActivity::class.java, intent)
    val searchActivity = controller.create().start().resume().get()
    try {
      searchActivity.nativeSearchView.releaseFocusAndInput()
      assertThat(searchActivity.nativeSearchView.searchInput.isFocusable).isFalse()

      controller.pause().stop().start().resume()
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertThat(searchActivity.nativeSearchView.searchInput.isEnabled).isTrue()
      assertThat(searchActivity.nativeSearchView.searchInput.isFocusable).isTrue()
      assertThat(searchActivity.nativeSearchView.searchInput.isFocusableInTouchMode).isTrue()
    } finally {
      controller.pause().stop().destroy()
    }
  }
}
