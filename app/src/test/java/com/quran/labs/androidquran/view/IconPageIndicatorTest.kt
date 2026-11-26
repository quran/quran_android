package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class IconPageIndicatorTest {

  private class FakeIconPagerAdapter(
    private var items: List<Pair<Int, String?>>
  ) : PagerAdapter(), IconPageIndicator.IconPagerAdapter {
    fun updateItems(newItems: List<Pair<Int, String?>>) {
      items = newItems
      notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj

    override fun getIconResId(index: Int): Int = items[index].first

    override fun getPageTitle(position: Int): CharSequence? = items[position].second
  }

  private class TrackingViewPager(context: Context) : ViewPager(context) {
    val activeListeners = mutableListOf<OnPageChangeListener>()

    override fun addOnPageChangeListener(listener: OnPageChangeListener) {
      activeListeners.add(listener)
      super.addOnPageChangeListener(listener)
    }

    override fun removeOnPageChangeListener(listener: OnPageChangeListener) {
      activeListeners.remove(listener)
      super.removeOnPageChangeListener(listener)
    }
  }

  @Test
  fun `drawing empty indicator does not throw`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val indicator = IconPageIndicator(context)

    indicator.draw(Canvas())
  }

  @Test
  fun `notifyDataSetChanged clamps selected index when adapter shrinks and sets labels`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val adapter = FakeIconPagerAdapter(
      listOf(
        R.drawable.ic_ab_search to "Search",
        R.drawable.ic_goto_quran to "Quran",
        R.drawable.ic_done to "Done"
      )
    )
    val viewPager = TrackingViewPager(context).apply {
      this.adapter = adapter
    }
    val indicator = IconPageIndicator(context)
    indicator.setViewPager(viewPager, 2)

    val iconsLayout = indicator.getChildAt(0) as ViewGroup
    assertThat(iconsLayout.childCount).isEqualTo(3)
    assertThat(iconsLayout.getChildAt(0).contentDescription).isEqualTo("Search")
    assertThat(iconsLayout.getChildAt(1).contentDescription).isEqualTo("Quran")
    assertThat(iconsLayout.getChildAt(2).contentDescription).isEqualTo("Done")

    adapter.updateItems(
      listOf(
        R.drawable.ic_ab_search to "Search",
        R.drawable.ic_goto_quran to "Quran"
      )
    )
    indicator.notifyDataSetChanged()

    assertThat(iconsLayout.childCount).isEqualTo(2)
    assertThat(viewPager.currentItem).isEqualTo(1)
    indicator.draw(Canvas())

    adapter.updateItems(emptyList())
    indicator.notifyDataSetChanged()

    assertThat(iconsLayout.childCount).isEqualTo(0)
    indicator.draw(Canvas())
  }

  @Test
  fun `setViewPager removes listener from previous ViewPager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val firstPager = TrackingViewPager(context).apply {
      adapter = FakeIconPagerAdapter(listOf(R.drawable.ic_done to "One"))
    }
    val secondPager = TrackingViewPager(context).apply {
      adapter = FakeIconPagerAdapter(listOf(R.drawable.ic_done to "Two"))
    }
    val indicator = IconPageIndicator(context)

    indicator.setViewPager(firstPager)
    assertThat(firstPager.activeListeners).contains(indicator)

    indicator.setViewPager(secondPager)
    assertThat(firstPager.activeListeners).doesNotContain(indicator)
    assertThat(secondPager.activeListeners).contains(indicator)
  }
}
