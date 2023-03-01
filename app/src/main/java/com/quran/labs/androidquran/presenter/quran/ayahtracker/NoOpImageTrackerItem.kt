package com.quran.labs.androidquran.presenter.quran.ayahtracker

import com.quran.data.model.AyahGlyph
import com.quran.data.model.AyahWord
import com.quran.data.model.selection.SelectionIndicator

class NoOpImageTrackerItem(pageNumber: Int) : AyahTrackerItem(pageNumber) {
  override fun getToolBarPosition(page: Int, sura: Int, ayah: Int): SelectionIndicator = SelectionIndicator.None
  override fun getToolBarPosition(page: Int, word: AyahWord): SelectionIndicator = SelectionIndicator.None
  override fun getToolBarPosition(page: Int, glyph: AyahGlyph): SelectionIndicator = SelectionIndicator.None
}
