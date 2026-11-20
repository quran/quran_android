package com.quran.data.page.provider

import com.quran.common.upgrade.LocalDataUpgrade
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.page.provider.linebyline.LineNewMadaniPageProvider
import com.quran.data.page.provider.linebyline.Madani1441LinePageProvider
import com.quran.data.page.provider.newmadani.NewMadaniPageProvider
import com.quran.data.page.provider.newmadani.NewerNewMadaniPageProvider
import com.quran.data.page.provider.shemerly.ShemerlyPageProvider
import com.quran.data.page.provider.tajweed.TajweedPageProvider
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.data.pageinfo.mapper.IdentityAyahMapper
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.drawing.AyahMarkerDrawable
import com.quran.labs.androidquran.common.drawing.helper.AyahMarkerDrawer
import com.quran.labs.androidquran.common.drawing.helper.SuraHeaderDrawer
import com.quran.labs.androidquran.pages.common.madani.MadaniPageProvider
import com.quran.labs.androidquran.pages.common.madani.upgrade.MadaniDataUpgrade
import com.quran.labs.androidquran.pages.common.madani.upgrade.MadaniPreferencesUpgrade
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.page.common.factory.PageViewFactoryProvider
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@BindingContainer
object QuranDataModule {

  @Provides
  fun providePageViewFactoryProvider(): PageViewFactoryProvider {
    return DefaultPageViewFactoryProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("madani")
  fun provideMadaniPageSet(): PageProvider {
    return MadaniPageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("new_madani")
  fun provideNewMadaniPageSet(): PageProvider {
    return NewMadaniPageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("new_madani_1441")
  fun provideNewerNewMadaniPageSet(): PageProvider {
    return NewerNewMadaniPageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("new_madani_1439_lines")
  fun provideNewerNewMadaniLinesPageSet(): PageProvider {
    return LineNewMadaniPageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("madani_1441_lines")
  fun provideMadani1441LinesPageSet(): PageProvider {
    return Madani1441LinePageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("shemerly")
  fun provideShemerlyPageSet(): PageProvider {
    return ShemerlyPageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("tajweed")
  fun provideTajweedPageSet(): PageProvider {
    return TajweedPageProvider()
  }

  @Named(DependencyInjectionConstants.FALLBACK_PAGE_TYPE)
  @JvmStatic
  @Provides
  fun provideFallbackPageType(): String = "madani_1441_lines"

  @JvmStatic
  @Provides
  @ElementsIntoSet
  fun provideImageDrawHelpers(): Set<ImageDrawHelper> {
    return setOf(
        AyahMarkerDrawer(AyahMarkerDrawable(0x79004d40, 0x79e0f2f1)),
        SuraHeaderDrawer(
          com.quran.labs.androidquran.common.drawing.R.drawable.chapter_hdr,
          0xff004d40.toInt()
        )
    )
  }

  @JvmStatic
  @Provides
  fun provideLocalDataUpgrade(madaniDataUpgrade: MadaniDataUpgrade): LocalDataUpgrade = madaniDataUpgrade

  @JvmStatic
  @Provides
  fun providePreferencesUpgrade(madaniPreferencesUpgrade: MadaniPreferencesUpgrade): PreferencesUpgrade =
    madaniPreferencesUpgrade

  @JvmStatic
  @Provides
  fun provideAyahMapper(): AyahMapper = IdentityAyahMapper()
}
