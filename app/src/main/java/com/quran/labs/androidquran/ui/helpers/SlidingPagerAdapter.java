package com.quran.labs.androidquran.ui.helpers;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.fragment.AyahPlaybackFragment;
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.widgets.IconPageIndicator;

public class SlidingPagerAdapter extends FragmentStatePagerAdapter implements
    IconPageIndicator.IconPagerAdapter {

  public static final int TAG_PAGE = 0;
  public static final int TRANSLATION_PAGE = 1;
  public static final int AUDIO_PAGE = 2;
  public static final int[] PAGES = {
      TAG_PAGE, TRANSLATION_PAGE, AUDIO_PAGE
  };
  public static final int[] PAGE_ICONS = {
      R.drawable.ic_tag, R.drawable.ic_translation, R.drawable.ic_play
  };

  private boolean mIsRtl;

  public SlidingPagerAdapter(FragmentManager fm, boolean isRtl) {
    super(fm, "sliding");
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
      case AUDIO_PAGE:
        return new AyahPlaybackFragment();
    }
    return null;
  }

  @Override
  public int getIconResId(int index) {
    return PAGE_ICONS[getPagePosition(index)];
  }

}
