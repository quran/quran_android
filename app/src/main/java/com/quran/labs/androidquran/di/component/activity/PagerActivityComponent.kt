package com.quran.labs.androidquran.di.component.activity

import android.content.Context
import com.quran.data.di.ActivityScope
import com.quran.data.di.QuranReadingScope
import com.quran.labs.androidquran.di.component.fragment.QuranPageComponent
import com.quran.labs.androidquran.di.module.activity.PagerActivityModule
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.fragment.AyahPlaybackFragment
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment
import com.quran.labs.androidquran.ui.fragment.TagBookmarkFragment
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.mobile.di.QuranReadingActivityComponent
import com.quran.mobile.di.qualifier.ActivityContext
import com.quran.mobile.feature.qarilist.QariListWrapper
import com.quran.page.common.toolbar.AyahToolBar
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.BindsInstance
import dagger.Subcomponent

@ActivityScope
@MergeSubcomponent(QuranReadingScope::class, modules = [PagerActivityModule::class])
interface PagerActivityComponent : QuranReadingActivityComponent {
  // subcomponents
  fun quranPageComponentFactory(): QuranPageComponent.Factory

  fun inject(pagerActivity: PagerActivity)
  fun inject(ayahToolBar: AyahToolBar)

  fun inject(tagBookmarkFragment: TagBookmarkFragment)
  fun inject(ayahPlaybackFragment: AyahPlaybackFragment)
  fun inject(ayahTranslationFragment: AyahTranslationFragment)

  fun inject(qariListWrapper: QariListWrapper)

  @Subcomponent.Factory
  interface Factory {
    fun generate(
      @BindsInstance @ActivityContext context: Context,
      @BindsInstance ayahSelectedListener: AyahSelectedListener
    ): PagerActivityComponent
  }
}
