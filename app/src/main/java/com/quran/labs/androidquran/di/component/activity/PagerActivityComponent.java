package com.quran.labs.androidquran.di.component.activity;

import com.quran.labs.androidquran.di.component.fragment.QuranPageComponent;
import com.quran.labs.androidquran.di.ActivityScope;
import com.quran.labs.androidquran.di.module.activity.PagerActivityModule;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment;

import dagger.Subcomponent;

@ActivityScope
@Subcomponent(modules = PagerActivityModule.class)
public interface PagerActivityComponent {
  // subcomponents
  QuranPageComponent.Builder quranPageComponentBuilder();

  void inject(PagerActivity pagerActivity);
  void inject(AyahTranslationFragment ayahTranslationFragment);

  @Subcomponent.Builder interface Builder {
    Builder withPagerActivityModule(PagerActivityModule pagerModule);
    PagerActivityComponent build();
  }
}
