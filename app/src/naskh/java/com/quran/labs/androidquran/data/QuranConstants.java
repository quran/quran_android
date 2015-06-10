package com.quran.labs.androidquran.data;

import com.quran.labs.androidquran.util.NaskhPageProvider;
import com.quran.labs.androidquran.util.QuranScreenInfo;

import android.support.annotation.NonNull;
import android.view.Display;

public class QuranConstants {
  public static final int NUMBER_OF_PAGES = 612;

  public static QuranScreenInfo.PageProvider getPageProvider(@NonNull Display display) {
    return new NaskhPageProvider(display);
  }
}
