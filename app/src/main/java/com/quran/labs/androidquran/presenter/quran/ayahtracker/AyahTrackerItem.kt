package com.quran.labs.androidquran.presenter.quran.ayahtracker

import com.quran.data.model.AyahGlyph
import com.quran.data.model.AyahWord
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.SelectionIndicator
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.ui.helpers.HighlightType
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates

open class AyahTrackerItem internal constructor(val page: Int) {
  open fun onSetPageBounds(pageCoordinates: PageCoordinates) {}
  open fun onSetAyahCoordinates(ayahCoordinates: AyahCoordinates) {}
  open fun onHighlightAyah(
    page: Int,
    sura: Int,
    ayah: Int,
    type: HighlightType,
    scrollToAyah: Boolean
  ): Boolean {
    return onHighlightAyah(page, sura, ayah, -1, type, scrollToAyah)
  }

  open fun onHighlightAyah(
    page: Int,
    sura: Int,
    ayah: Int,
    word: Int,
    type: HighlightType,
    scrollToAyah: Boolean
  ): Boolean {
    return false
  }

  open fun onHighlightAyat(page: Int, ayahKeys: Set<String>, type: HighlightType) {}
  open fun onUnHighlightAyah(page: Int, sura: Int, ayah: Int, type: HighlightType) {
    return onUnHighlightAyah(page, sura, ayah, -1, type)
  }
  open fun onUnHighlightAyah(page: Int, sura: Int, ayah: Int, word: Int, type: HighlightType) {}
  open fun onUnHighlightAyahType(type: HighlightType) {}

  open fun getToolBarPosition(page: Int, sura: Int, ayah: Int): SelectionIndicator = SelectionIndicator.None

  open fun getAyahForPosition(page: Int, x: Float, y: Float): SuraAyah? = null

  open fun getGlyphForPosition(page: Int, x: Float, y: Float): AyahGlyph? = null

  open fun getQuranAyahInfo(sura: Int, ayah: Int): QuranAyahInfo? = null

  open fun getLocalTranslations(): Array<LocalTranslation>? = null
}
