package com.quran.labs.androidquran.ui.helpers

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment
import com.quran.labs.androidquran.ui.fragment.TabletFragment
import com.quran.labs.androidquran.ui.fragment.TranslationFragment
import timber.log.Timber

class QuranPageAdapter(
  fm: FragmentManager?, private val isDualPages: Boolean,
  var isShowingTranslation: Boolean,
  private val quranInfo: QuranInfo, private val isSplitScreen: Boolean
) : FragmentStatePagerAdapter(fm, if (isDualPages) "dualPages" else "singlePage") {
  private val totalPages: Int = quranInfo.numberOfPages
  private val totalPagesDual: Int = totalPages / 2
  fun setTranslationMode() {
    if (!isShowingTranslation) {
      isShowingTranslation = true
      notifyDataSetChanged()
    }
  }

  fun setQuranMode() {
    if (isShowingTranslation) {
      isShowingTranslation = false
      notifyDataSetChanged()
    }
  }

  override fun getItemPosition(`object`: Any): Int {
    /** when the ViewPager gets a notifyDataSetChanged (or invalidated),
     * it goes through its set of saved views and runs this method on
     * each one to figure out whether or not it should remove the view
     * or not.  the default implementation returns POSITION_UNCHANGED,
     * which means that "this page is as is."
     *
     * as noted in http://stackoverflow.com/questions/7263291 in one
     * of the answers, if you're just updating your view (changing a
     * field's value, etc), this is highly inefficient (because you
     * recreate the view for nothing).
     *
     * in our case, however, this is the right thing to do since we
     * change the fragment completely when we notifyDataSetChanged.
     */
    return POSITION_NONE
  }

  override fun getCount(): Int {
    return if (isDualPagesVisible) {
      totalPagesDual
    } else {
      totalPages
    }
  }

  override fun getItem(position: Int): Fragment {
    val page = quranInfo
      .getPageFromPosition(position, isDualPagesVisible)
    Timber.d("getting page: %d, from position %d", page, position)
    return if (isDualPages) {
      TabletFragment.newInstance(
        page,
        if (isShowingTranslation) TabletFragment.Mode.TRANSLATION else TabletFragment.Mode.ARABIC,
        isSplitScreen
      )
    } else if (isShowingTranslation) {
      TranslationFragment.newInstance(page)
    } else {
      QuranPageFragment.newInstance(page)
    }
  }

  override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
    val f = `object` as Fragment
    Timber.d("destroying item: %d, %s", position, f)
    cleanupFragment(f)
    super.destroyItem(container, position, `object`)
  }

  override fun cleanupFragment(f: Fragment) {
    if (f is QuranPageFragment) {
      f.cleanup()
    } else if (f is TabletFragment) {
      f.cleanup()
    }
  }

  fun getFragmentIfExistsForPage(page: Int): QuranPage? {
    if (page < Constants.PAGES_FIRST || totalPages < page) {
      return null
    }
    val position = quranInfo.getPositionFromPage(page, isDualPagesVisible)
    val fragment = getFragmentIfExists(position)
    return if (fragment is QuranPage && fragment.isAdded) fragment else null
  }

  private val isDualPagesVisible: Boolean
    get() = isDualPages && !(isSplitScreen && isShowingTranslation)
}
