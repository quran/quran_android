package com.quran.labs.androidquran.hybrid

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.SearchActivity
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import com.quran.labs.androidquran.common.ui.core.PooledPageView
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.QuranLineByLinePresenter
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.search.HybridSearchAdapter
import com.quran.labs.androidquran.hybrid.ui.FloatingBottomNavView
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.page.common.data.PageMode
import com.quran.page.common.factory.PageViewFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class Run7PerformanceAndPoolingTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    QuranSettings.setInstance(null)
    QuranLineByLinePresenter.clearScratchBitmapPool()
  }

  @After
  fun tearDown() {
    QuranLineByLinePresenter.clearScratchBitmapPool()
    QuranSettings.setInstance(null)
  }

  @Test
  fun `QuranLineByLinePresenter reuses 1-slot swBitmap pool and clears on BitmapPoolTrimRegistry`() {
    val first = QuranLineByLinePresenter.acquireScratchBitmap(720, 1280)
    assertThat(first.width).isEqualTo(720)
    assertThat(first.height).isEqualTo(1280)
    assertThat(first.isMutable).isTrue()
    assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isFalse()

    QuranLineByLinePresenter.releaseScratchBitmap(first)
    assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isTrue()

    val reused = QuranLineByLinePresenter.acquireScratchBitmap(720, 1280)
    assertThat(reused).isSameInstanceAs(first)

    QuranLineByLinePresenter.releaseScratchBitmap(reused)
    val resized = QuranLineByLinePresenter.acquireScratchBitmap(800, 1400)
    assertThat(resized).isNotSameInstanceAs(reused)
    assertThat(reused.isRecycled).isTrue()

    QuranLineByLinePresenter.releaseScratchBitmap(resized)
    assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isTrue()

    BitmapPoolTrimRegistry.clearAll()
    assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isFalse()
    assertThat(resized.isRecycled).isTrue()
  }

  private class FakePooledPageView(
    context: Context,
    initialPage: Int
  ) : FrameLayout(context), PooledPageView {
    val reboundPages = mutableListOf(initialPage)
    var cleanupCount = 0

    override fun rebindPage(page: Int) {
      reboundPages.add(page)
    }

    override fun cleanup() {
      cleanupCount++
    }
  }

  @Test
  fun `QuranPageAdapter pools and rebinds single-page Views up to 3 slots without Fragments`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val container = FrameLayout(activity)
    val quranInfo = QuranInfo(MadaniDataSource())
    val createdViews = mutableListOf<FakePooledPageView>()
    val factory = object : PageViewFactory {
      override fun providePage(pageNumber: Int, pageMode: PageMode): Fragment? = null
      override fun providePageView(context: Context, pageNumber: Int, pageMode: PageMode): View? {
        if (pageMode != PageMode.SingleArabicPage) return null
        return FakePooledPageView(context, pageNumber).also { createdViews.add(it) }
      }
    }

    val adapter = QuranPageAdapter(
      fm = activity.supportFragmentManager,
      isDualPages = false,
      isShowingTranslation = false,
      quranInfo = quranInfo,
      isSplitScreen = false,
      pageViewFactory = factory
    )

    // Instantiate 3 adjacent pages (pos 0..2) -> creates 3 views
    val item0 = adapter.instantiateItem(container, 0) as FakePooledPageView
    val item1 = adapter.instantiateItem(container, 1) as FakePooledPageView
    val item2 = adapter.instantiateItem(container, 2) as FakePooledPageView
    assertThat(createdViews).hasSize(3)
    assertThat(adapter.getFragmentIfExists(0)).isNull()
    assertThat(adapter.isViewFromObject(item0, item0)).isTrue()

    // Destroy pos 0 -> enters pool (size 1)
    adapter.destroyItem(container, 0, item0)
    assertThat(item0.cleanupCount).isEqualTo(1)
    assertThat(item0.parent).isNull()
    assertThat(adapter.pooledViewCountForTest()).isEqualTo(1)

    // Instantiate pos 3 -> reuses item0 via rebindPage instead of creating a 4th view
    val item3 = adapter.instantiateItem(container, 3) as FakePooledPageView
    assertThat(item3).isSameInstanceAs(item0)
    assertThat(createdViews).hasSize(3)
    assertThat(item3.reboundPages.last()).isEqualTo(quranInfo.getPageFromPosition(3, false))

    // Destroy 4 views -> pool stays bounded at 3
    val extra = FakePooledPageView(activity, 99)
    adapter.destroyItem(container, 1, item1)
    adapter.destroyItem(container, 2, item2)
    adapter.destroyItem(container, 3, item3)
    adapter.destroyItem(container, 4, extra)
    assertThat(adapter.pooledViewCountForTest()).isEqualTo(3)

    adapter.clearViewPool()
    assertThat(adapter.pooledViewCountForTest()).isEqualTo(0)
  }

  @Test
  fun `StaticLayoutVerseView uses prebuilt layout on exact width and falls back cleanly on mismatch`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val view = HybridSearchAdapter.StaticLayoutVerseView(activity)
    val text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَـٰنِ ٱلرَّحِیمِ"
    val paint = TextPaint().apply { textSize = 32f }
    val widthPx = 420
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setTextDirection(TextDirectionHeuristics.RTL)
      .setMaxLines(6)
      .build()

    view.setPrebuiltContent(text, layout)
    assertThat(view.text.toString()).isEqualTo(text)
    assertThat(view.hasPrebuiltLayout()).isTrue()

    view.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    assertThat(view.measuredWidth).isEqualTo(widthPx)
    assertThat(view.measuredHeight).isEqualTo(layout.height + view.compoundPaddingTop + view.compoundPaddingBottom)

    // Mismatched width falls back cleanly without throwing
    view.measure(
      View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    assertThat(view.measuredWidth).isEqualTo(280)
  }

  @Test
  fun `SearchActivity finishing onStop clears focus, listeners, adapter, recents, and transient engine state`() {
    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الله")
    val controller = Robolectric.buildActivity(SearchActivity::class.java, intent)
    val activity = controller.create().start().resume().get()
    activity.nativeSearchView.searchInput.requestFocus()
    assertThat(activity.nativeSearchView.searchInput.hasFocus()).isTrue()

    activity.finish()
    controller.pause().stop()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    assertThat(activity.nativeSearchView.searchInput.hasFocus()).isFalse()
    assertThat(activity.nativeSearchView.resultsRecyclerView.adapter).isNull()
    assertThat(activity.hybridSearchEngine.uiState.value.query).isEmpty()
    assertThat(activity.hybridSearchEngine.uiState.value.ayahHits).isEmpty()

    controller.destroy()
    assertThat(activity.rootView.childCount).isEqualTo(0)
  }

  @Test
  fun `FloatingBottomNavView uses StateListDrawable instead of RippleDrawable and unbinds cleanly`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val nav = FloatingBottomNavView(activity)
    nav.applyPalette(HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false))
    val pill = nav.getChildAt(0) as ViewGroup
    val tab0 = pill.getChildAt(0)
    assertThat(tab0.background).isInstanceOf(StateListDrawable::class.java)
    assertThat(tab0.background).isNotInstanceOf(RippleDrawable::class.java)

    var calls = 0
    nav.setOnTabSelectedListener { calls++ }
    tab0.performClick()
    assertThat(calls).isEqualTo(1)

    nav.unbind()
    tab0.performClick()
    assertThat(calls).isEqualTo(1)
  }
}
