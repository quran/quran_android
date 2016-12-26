package com.quran.labs.androidquran.ui.helpers;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.ViewGroup;

import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment;
import com.quran.labs.androidquran.ui.fragment.TabletFragment;
import com.quran.labs.androidquran.ui.fragment.TranslationFragment;

import timber.log.Timber;

import static com.quran.labs.androidquran.data.Constants.PAGES_LAST;
import static com.quran.labs.androidquran.data.Constants.PAGES_LAST_DUAL;

public class QuranPageAdapter extends FragmentStatePagerAdapter {

  private boolean mIsShowingTranslation = false;
  private boolean mIsDualPages = false;

  public QuranPageAdapter(FragmentManager fm, boolean dualPages,
                          boolean isShowingTranslation) {
    super(fm, dualPages ? "dualPages" : "singlePage");
    mIsDualPages = dualPages;
    mIsShowingTranslation = isShowingTranslation;
  }

  public void setTranslationMode() {
    if (!mIsShowingTranslation) {
      mIsShowingTranslation = true;
      notifyDataSetChanged();
    }
  }

  public void setQuranMode() {
    if (mIsShowingTranslation) {
      mIsShowingTranslation = false;
      notifyDataSetChanged();
    }
  }

  public boolean getIsShowingTranslation() {
    return mIsShowingTranslation;
  }

  @Override
  public int getItemPosition(Object object) {
    /* when the ViewPager gets a notifyDataSetChanged (or invalidated),
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
    return POSITION_NONE;
  }

  @Override
  public int getCount() {
    return mIsDualPages ? PAGES_LAST_DUAL : PAGES_LAST;
  }

  @Override
  public Fragment getItem(int position) {
    int page = QuranInfo.getPageFromPos(position, mIsDualPages);
    Timber.d("getting page: %d", page);
    if (mIsDualPages) {
      return TabletFragment.newInstance(page,
          mIsShowingTranslation ? TabletFragment.Mode.TRANSLATION :
              TabletFragment.Mode.ARABIC);
    } else if (mIsShowingTranslation) {
      return TranslationFragment.newInstance(page);
    } else {
      return QuranPageFragment.newInstance(page);
    }
  }

  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    Fragment f = (Fragment) object;
    Timber.d("destroying item: %d, %s", position, f);
    cleanupFragment(f);
    super.destroyItem(container, position, object);
  }

  @Override
  public void cleanupFragment(Fragment f) {
    if (f instanceof QuranPageFragment) {
      ((QuranPageFragment) f).cleanup();
    } else if (f instanceof TabletFragment) {
      ((TabletFragment) f).cleanup();
    }
  }

  public QuranPage getFragmentIfExistsForPage(int page) {
    if (page < Constants.PAGES_FIRST || PAGES_LAST < page) {
      return null;
    }
    int position = QuranInfo.getPosFromPage(page, mIsDualPages);
    Fragment fragment = getFragmentIfExists(position);
    return fragment instanceof QuranPage && fragment.isAdded() ? (QuranPage) fragment : null;
  }

}
