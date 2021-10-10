package com.quran.labs.androidquran.ui.helpers

import com.quran.data.model.selection.AyahToolBarPosition
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo

interface AyahTracker {
  fun getToolBarPosition(
    sura: Int, ayah: Int, toolBarWidth: Int, toolBarHeight: Int
  ): AyahToolBarPosition?
  fun getQuranAyahInfo(sura: Int, ayah: Int): QuranAyahInfo?
  fun getLocalTranslations(): Array<LocalTranslation>?
}
