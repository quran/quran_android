package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

public class NoAudioSlidingPagerAdapter extends SlidingPagerAdapter {

  public static final int TAG_PAGE = 0;
  public static final int TRANSLATION_PAGE = 1;
  public static final int[] PAGES = { TAG_PAGE, TRANSLATION_PAGE };
  public static final int[] PAGE_ICONS = { R.drawable.ic_tag, R.drawable.ic_translation };

  private boolean mIsRtl;

  public NoAudioSlidingPagerAdapter(FragmentManager fm, boolean isRtl) {
    super(fm, isRtl);
    mIsRtl = isRtl;
  }

  @Override
  public int getCount() {
    return PAGES.length;
  }

  public int getPagePosition(int page) {
    return mIsRtl ? (PAGES.length - 1) - page : page;
  }

  @Override
  public Fragment getItem(int position) {
    final int pos = getPagePosition(position);
    switch (pos) {
      case TAG_PAGE:
        return new TagBookmarkDialog();
      case TRANSLATION_PAGE:
        return new AyahTranslationFragment();
    }
    return null;
  }

  @Override
  public int getIconResId(int index) {
    return PAGE_ICONS[getPagePosition(index)];
  }
}
