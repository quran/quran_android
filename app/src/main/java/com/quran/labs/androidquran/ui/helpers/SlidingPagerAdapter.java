package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.ui.fragment.AyahPlaybackFragment;
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkFragment;
import com.quran.labs.androidquran.view.IconPageIndicator;
import com.quran.mobile.di.AyahActionFragmentProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class SlidingPagerAdapter extends FragmentStatePagerAdapter implements
    IconPageIndicator.IconPagerAdapter {

  public static final int TAG_PAGE = 0;
  public static final int TRANSLATION_PAGE = 1;
  public static final int AUDIO_PAGE = 2;
  public static final int TRANSCRIPT_PAGE = 3;

  private final boolean isRtl;
  private final List<AyahActionFragmentProvider> pages;

  public SlidingPagerAdapter(FragmentManager fm, boolean isRtl,
                             Set<AyahActionFragmentProvider> additionalPanels) {
    super(fm, "sliding");
    this.isRtl = isRtl;
    this.pages = new ArrayList<>();

    // Add the core ayah action panels
    this.pages.add(TagBookmarkFragment.Provider.INSTANCE);
    this.pages.add(AyahTranslationFragment.Provider.INSTANCE);
    this.pages.add(AyahPlaybackFragment.Provider.INSTANCE);

    // Since additionalPanel Set may be unsorted, put them in a list and sort them by page number..
    List<AyahActionFragmentProvider> additionalPages = new ArrayList<>(additionalPanels);
    Collections.sort(additionalPages, (o1, o2) -> Integer.compare(o1.getOrder(), o2.getOrder()));
    // ..then add them to the pages list
    pages.addAll(additionalPages);
  }

  @Override
  public int getCount() {
    return pages.size();
  }

  public int getPagePosition(int page) {
    return isRtl ? (pages.size() - 1) - page : page;
  }

  @Override
  public Fragment getItem(int position) {
    final int pos = getPagePosition(position);
    return pages.get(pos).newAyahActionFragment();
  }

  @Override
  public int getIconResId(int index) {
    final int pos = getPagePosition(index);
    return pages.get(pos).getIconResId();
  }

}
