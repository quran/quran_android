package com.quran.labs.androidquran.module.fragment;

import dagger.Module;
import dagger.Provides;

@Module
public class QuranPageModule {
  private final Integer[] pages;
  private final boolean isTablet;

  public QuranPageModule(boolean isTablet, Integer... pages) {
    this.pages = pages;
    this.isTablet = isTablet;
  }

  @Provides
  Integer[] providePages() {
    return this.pages;
  }

  @Provides
  boolean provideIsTablet() {
    return this.isTablet;
  }
}
