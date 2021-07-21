package com.quran.labs.androidquran.presenter.quran.ayahtracker

import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.ui.helpers.HighlightType
import com.quran.labs.androidquran.ui.translation.TranslationView
import com.quran.labs.androidquran.view.AyahToolBar.AyahToolBarPosition

class AyahTranslationTrackerItem(
  page: Int,
  private val quranInfo: QuranInfo,
  private val ayahView: TranslationView
) : AyahTrackerItem(page) {

  public override fun onHighlightAyah(
    page: Int, sura: Int, ayah: Int, type: HighlightType, scrollToAyah: Boolean
  ): Boolean {
    if (this.page == page) {
      ayahView.highlightAyah(SuraAyah(sura, ayah), quranInfo.getAyahId(sura, ayah), type)
      return true
    }
    ayahView.unhighlightAyah(type)
    return false
  }

  public override fun onUnHighlightAyah(page: Int, sura: Int, ayah: Int, type: HighlightType) {
    if (this.page == page) {
      ayahView.unhighlightAyah(type)
    }
  }

  public override fun onUnHighlightAyahType(type: HighlightType) {
    ayahView.unhighlightAyat()
  }

  public override fun getToolBarPosition(
    page: Int,
    sura: Int, ayah: Int, toolBarWidth: Int, toolBarHeight: Int
  ): AyahToolBarPosition? {
    val position = ayahView.toolbarPosition
    return position ?: super.getToolBarPosition(
      page, sura, ayah, toolBarWidth,
      toolBarHeight
    )
  }

  public override fun getQuranAyahInfo(sura: Int, ayah: Int): QuranAyahInfo? {
    val quranAyahInfo = ayahView.getQuranAyahInfo(sura, ayah)
    return quranAyahInfo ?: super.getQuranAyahInfo(sura, ayah)
  }

  public override fun getLocalTranslations(): Array<LocalTranslation>? {
    val translations = ayahView.localTranslations
    return translations ?: super.getLocalTranslations()
  }
}
