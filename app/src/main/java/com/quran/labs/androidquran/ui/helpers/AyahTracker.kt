package com.quran.labs.androidquran.ui.helpers

import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.view.AyahToolBar.AyahToolBarPosition

interface AyahTracker {
  fun highlightAyah(sura: Int, ayah: Int, type: HighlightType, scrollToAyah: Boolean)
  fun highlightAyat(page: Int, ayahKeys: Set<String>, type: HighlightType)
  fun unHighlightAyah(sura: Int, ayah: Int, type: HighlightType)
  fun unHighlightAyahs(type: HighlightType)
  fun getToolBarPosition(
    sura: Int, ayah: Int, toolBarWidth: Int, toolBarHeight: Int
  ): AyahToolBarPosition?
  fun getQuranAyahInfo(sura: Int, ayah: Int): QuranAyahInfo?
  fun getLocalTranslations(): Array<LocalTranslation?>?
}
