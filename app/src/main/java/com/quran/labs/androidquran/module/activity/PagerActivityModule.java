package com.quran.labs.androidquran.module.activity;

import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;

import dagger.Module;
import dagger.Provides;

@Module
public class PagerActivityModule {
  private final PagerActivity pagerActivity;

  public PagerActivityModule(PagerActivity pagerActivity) {
    this.pagerActivity = pagerActivity;
  }

  @Provides
  AyahSelectedListener provideAyahSelectedListener() {
    return this.pagerActivity;
  }
}
