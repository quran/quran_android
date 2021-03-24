package com.quran.labs.androidquran.presenter.quran.ayahtracker

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.ui.helpers.HighlightType
import com.quran.labs.androidquran.view.AyahToolBar.AyahToolBarPosition
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates

class AyahSplitConsolidationTrackerItem(
  page: Int,
  private val imageTrackerItem: AyahImageTrackerItem,
  private val translationTrackerItem: AyahTranslationTrackerItem
) : AyahTrackerItem(page) {

  override fun onSetPageBounds(pageCoordinates: PageCoordinates?) {
    imageTrackerItem.onSetPageBounds(pageCoordinates)
    translationTrackerItem.onSetPageBounds(pageCoordinates)
  }

  override fun onSetAyahCoordinates(ayahCoordinates: AyahCoordinates?) {
    imageTrackerItem.onSetAyahCoordinates(ayahCoordinates)
    translationTrackerItem.onSetAyahCoordinates(ayahCoordinates)
  }

  override fun onSetAyahBookmarks(bookmarks: MutableList<Bookmark>) {
    imageTrackerItem.onSetAyahBookmarks(bookmarks)
    translationTrackerItem.onSetAyahBookmarks(bookmarks)
  }

  override fun onHighlightAyah(
    page: Int, sura: Int, ayah: Int, type: HighlightType?, scrollToAyah: Boolean
  ): Boolean {
    val firstResult = imageTrackerItem.onHighlightAyah(page, sura, ayah, type, scrollToAyah)
    val secondResult = translationTrackerItem.onHighlightAyah(page, sura, ayah, type, scrollToAyah)
    return firstResult && secondResult
  }

  override fun onHighlightAyat(page: Int, ayahKeys: MutableSet<String>?, type: HighlightType?) {
    imageTrackerItem.onHighlightAyat(page, ayahKeys, type)
    translationTrackerItem.onHighlightAyat(page, ayahKeys, type)
  }

  override fun onUnHighlightAyah(page: Int, sura: Int, ayah: Int, type: HighlightType?) {
    imageTrackerItem.onUnHighlightAyah(page, sura, ayah, type)
    translationTrackerItem.onUnHighlightAyah(page, sura, ayah, type)
  }

  override fun onUnHighlightAyahType(type: HighlightType?) {
    imageTrackerItem.onUnHighlightAyahType(type)
    translationTrackerItem.onUnHighlightAyahType(type)
  }

  override fun getToolBarPosition(
    page: Int, sura: Int, ayah: Int, toolBarWidth: Int, toolBarHeight: Int
  ): AyahToolBarPosition? {
    return imageTrackerItem.getToolBarPosition(page, sura, ayah, toolBarWidth, toolBarHeight)
  }

  override fun getAyahForPosition(
    page: Int,
    x: Float,
    y: Float
  ): SuraAyah? {
    return imageTrackerItem.getAyahForPosition(page, x, y)
  }
}
