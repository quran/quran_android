package com.quran.labs.androidquran.di.component.activity

import com.quran.data.di.QuranReadingScope
import com.quran.data.di.QuranScope
import com.quran.labs.androidquran.di.component.fragment.QuranPageComponent
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.fragment.AyahPlaybackFragment
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment
import com.quran.labs.androidquran.ui.fragment.TagBookmarkFragment
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.mobile.di.QuranReadingActivityComponent
import com.quran.mobile.feature.audiobar.AudioBarWrapper
import com.quran.mobile.feature.qarilist.QariListWrapper
import com.quran.page.common.toolbar.AyahToolBar
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.BindsInstance

@QuranScope
@MergeSubcomponent(QuranReadingScope::class)
interface PagerActivityComponent : QuranReadingActivityComponent {
  // subcomponents
  fun quranPageComponentFactory(): QuranPageComponent.Factory

  fun inject(pagerActivity: PagerActivity)
  fun inject(ayahToolBar: AyahToolBar)

  fun inject(tagBookmarkFragment: TagBookmarkFragment)
  fun inject(ayahPlaybackFragment: AyahPlaybackFragment)
  fun inject(ayahTranslationFragment: AyahTranslationFragment)

  fun inject(qariListWrapper: QariListWrapper)
  fun inject(audioBarWrapper: AudioBarWrapper)

  @MergeSubcomponent.Factory
  interface Factory {
    fun generate(
      @BindsInstance ayahSelectedListener: AyahSelectedListener
    ): PagerActivityComponent
  }
}
