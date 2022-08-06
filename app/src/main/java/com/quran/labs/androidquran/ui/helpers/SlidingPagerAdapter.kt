package com.quran.labs.androidquran.ui.helpers

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.quran.labs.androidquran.ui.fragment.AyahPlaybackFragment
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment
import com.quran.labs.androidquran.ui.fragment.TagBookmarkFragment
import com.quran.labs.androidquran.view.IconPageIndicator
import com.quran.mobile.di.AyahActionFragmentProvider

class SlidingPagerAdapter(
  fm: FragmentManager,
  private val isRtl: Boolean,
  additionalPanels: Set<AyahActionFragmentProvider>,
) : FragmentStatePagerAdapter(fm, "sliding"), IconPageIndicator.IconPagerAdapter {

  private val pages: ArrayList<AyahActionFragmentProvider> = arrayListOf()

  init {
    // Add the core ayah action panels
    pages.add(TagBookmarkFragment.Provider)
    pages.add(AyahTranslationFragment.Provider)
    pages.add(AyahPlaybackFragment.Provider)

    // Since additionalPanel Set may be unsorted, put them in a list and sort them by page number..
    val additionalPages: ArrayList<AyahActionFragmentProvider> = ArrayList(additionalPanels)
    additionalPages.sortWith { o1, o2 -> o1.order.compareTo(o2.order) }
    // ..then add them to the pages list
    pages.addAll(additionalPages)
  }

  override fun getIconResId(index: Int): Int {
    val pos = getPagePosition(index)
    return pages[pos].iconResId
  }

  override fun getCount(): Int = pages.size

  override fun getItem(position: Int): Fragment {
    val pos = getPagePosition(position)
    return pages[pos].newAyahActionFragment()
  }

  fun getPagePosition(page: Int): Int {
    return if (isRtl) pages.size - 1 - page else page
  }

  companion object {
    const val TAG_PAGE = 0
    const val TRANSLATION_PAGE = 1
    const val AUDIO_PAGE = 2
    const val TRANSCRIPT_PAGE = 3
  }
}
