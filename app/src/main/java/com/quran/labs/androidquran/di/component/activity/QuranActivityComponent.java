package com.quran.labs.androidquran.di.component.activity;

import com.quran.labs.androidquran.di.ActivityScope;
import com.quran.labs.androidquran.di.module.activity.QuranActivityModule;
import com.quran.labs.androidquran.ui.QuranActivity;
import dagger.Subcomponent;

@ActivityScope
@Subcomponent(modules = QuranActivityModule.class)
public interface QuranActivityComponent {
  void inject(QuranActivity quranActivity);

  @Subcomponent.Builder
  interface Builder {
    QuranActivityComponent build();
  }
}
