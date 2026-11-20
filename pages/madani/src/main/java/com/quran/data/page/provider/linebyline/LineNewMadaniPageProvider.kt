package com.quran.data.page.provider.linebyline

import com.quran.data.page.provider.newmadani.NewMadaniPageProvider
import com.quran.data.page.provider.newmadani.NewMadaniPageSizeCalculator
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageContentType
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.madani.R

internal class LineNewMadaniPageProvider: NewMadaniPageProvider() {
  override val newMadaniBaseUrl = "$baseUrl/madani_1439"

  override fun getImageVersion() = 1

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
    NewMadaniPageSizeCalculator(1080)

  override fun getPreviewTitle() = R.string.newer_new_madani_lines_title

  override fun getPreviewDescription() = R.string.newer_new_madani_lines_description

  override fun getPageContentType(): PageContentType = PageContentType.Line(174f / 1080, 174, true)

  override fun getFallbackPageType(): String = "new_madani_1441"

  override fun pageType(): String = "madani_1439"
}
