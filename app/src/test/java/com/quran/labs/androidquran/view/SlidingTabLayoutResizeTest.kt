package com.quran.labs.androidquran.view

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class SlidingTabLayoutResizeTest {

  @Test
  fun `resizing from fitted width to narrow width updates child widths to mMaxTabWidth`() {
    val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    val container = FrameLayout(activity)
    val tabLayout = SlidingTabLayout(activity)
    val titles = listOf("SURAH ONE", "JUZ TWO", "BOOKMARKS THREE")
    val viewPager = ViewPager(activity).apply {
      adapter = object : PagerAdapter() {
        override fun getCount(): Int = titles.size
        override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj
        override fun getPageTitle(position: Int): CharSequence = titles[position]
      }
    }

    container.addView(
      tabLayout,
      FrameLayout.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT)
    )
    activity.setContentView(
      container,
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    )
    tabLayout.setViewPager(viewPager)
    shadowOf(Looper.getMainLooper()).idle()

    val strip = tabLayout.getChildAt(0) as LinearLayout
    assertThat(strip.childCount).isEqualTo(3)
    assertThat(tabLayout.width).isEqualTo(320)
    val wideLp = strip.getChildAt(0).layoutParams as LinearLayout.LayoutParams
    assertThat(wideLp.width).isEqualTo(0)
    assertThat(wideLp.weight).isEqualTo(1f)
    assertThat(strip.getChildAt(0).width).isGreaterThan(0)

    tabLayout.layoutParams = FrameLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT)
    shadowOf(Looper.getMainLooper()).idle()
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(tabLayout.width).isEqualTo(100)
    val narrowLp = strip.getChildAt(0).layoutParams as LinearLayout.LayoutParams
    assertThat(narrowLp.width).isGreaterThan(0)
    assertThat(narrowLp.weight).isEqualTo(0f)
    assertThat(strip.getChildAt(0).width).isEqualTo(narrowLp.width)
    assertThat(strip.getChildAt(1).width).isEqualTo(narrowLp.width)
    assertThat(strip.getChildAt(2).width).isEqualTo(narrowLp.width)

    tabLayout.layoutParams = FrameLayout.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT)
    shadowOf(Looper.getMainLooper()).idle()
    shadowOf(Looper.getMainLooper()).idle()

    val refittedLp = strip.getChildAt(0).layoutParams as LinearLayout.LayoutParams
    assertThat(refittedLp.width).isEqualTo(0)
    assertThat(refittedLp.weight).isEqualTo(1f)
    assertThat(strip.getChildAt(0).width).isGreaterThan(narrowLp.width)
  }

  @Test
  fun `setViewPager syncs tab strip indicator to non-zero initial page`() {
    val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    val container = FrameLayout(activity)
    val tabLayout = SlidingTabLayout(activity)
    val titles = listOf("BOOKMARKS", "JUZ", "SURAH")
    val viewPager = ViewPager(activity).apply {
      adapter = object : PagerAdapter() {
        override fun getCount(): Int = titles.size
        override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj
        override fun getPageTitle(position: Int): CharSequence = titles[position]
      }
      setCurrentItem(2, false)
    }

    container.addView(
      tabLayout,
      FrameLayout.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT)
    )
    activity.setContentView(
      container,
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    )

    tabLayout.setViewPager(viewPager)
    shadowOf(Looper.getMainLooper()).idle()

    val strip = tabLayout.getChildAt(0) as SlidingTabStrip
    assertThat(strip.childCount).isEqualTo(3)
    assertThat(strip.selectedPosition).isEqualTo(2)
  }
}
