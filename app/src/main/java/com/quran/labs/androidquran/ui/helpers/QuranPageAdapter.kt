package com.quran.labs.androidquran.ui.helpers

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment
import com.quran.labs.androidquran.ui.fragment.TabletFragment
import com.quran.labs.androidquran.ui.fragment.TranslationFragment
import com.quran.page.common.data.PageMode
import com.quran.page.common.factory.PageViewFactory
import timber.log.Timber

class QuranPageAdapter(
  fm: FragmentManager?,
  private val isDualPages: Boolean,
  var isShowingTranslation: Boolean,
  private val quranInfo: QuranInfo,
  private val isSplitScreen: Boolean,
  private val pageViewFactory: PageViewFactory? = null
) : FragmentStatePagerAdapter(fm, if (isDualPages) "dualPages" else "singlePage") {
  private var pageMode: PageMode = makePageMode()
  private val totalPages: Int = quranInfo.numberOfPagesConsideringSkipped
  private val totalPagesDual: Int = totalPages / 2 + (totalPages % 2)

  fun setTranslationMode() {
    if (!isShowingTranslation) {
      isShowingTranslation = true
      pageMode = makePageMode()
      notifyDataSetChanged()
    }
  }

  fun setQuranMode() {
    if (isShowingTranslation) {
      isShowingTranslation = false
      pageMode = makePageMode()
      notifyDataSetChanged()
    }
  }

  private fun makePageMode(): PageMode {
    return if (isDualPages) {
      if (isShowingTranslation && isSplitScreen) {
        PageMode.DualScreenMode.Mix
      } else if (isShowingTranslation) {
        PageMode.DualScreenMode.Translation
      } else {
        PageMode.DualScreenMode.Arabic
      }
    } else {
      if (isShowingTranslation) {
        PageMode.SingleTranslationPage
      } else {
        PageMode.SingleArabicPage
      }
    }
  }

  override fun getItemPosition(currentItem: Any): Int {
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
    val page = quranInfo.getPageFromPosition(position, isDualPagesVisible)
    Timber.d("getting page: %d, from position %d", page, position)
    return pageViewFactory?.providePage(page, pageMode) ?: when {
      isDualPages -> {
        TabletFragment.newInstance(
          page,
          if (isShowingTranslation) TabletFragment.Mode.TRANSLATION else TabletFragment.Mode.ARABIC,
          isSplitScreen
        )
      }
      isShowingTranslation -> {
        TranslationFragment.newInstance(page)
      }
      else -> {
        QuranPageFragment.newInstance(page)
      }
    }
  }

  override fun destroyItem(container: ViewGroup, position: Int, currentItem: Any) {
    val f = currentItem as Fragment
    Timber.d("destroying item: %d, %s", position, f)
    cleanupFragment(f)
    super.destroyItem(container, position, currentItem)
  }

  override fun cleanupFragment(f: Fragment) {
    if (f is QuranPageFragment) {
      f.cleanup()
    } else if (f is TabletFragment) {
      f.cleanup()
    }
  }

  private val isDualPagesVisible: Boolean
    get() = isDualPages && !(isSplitScreen && isShowingTranslation)
}
