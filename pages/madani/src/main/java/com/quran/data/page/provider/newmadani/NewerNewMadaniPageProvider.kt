package com.quran.data.page.provider.newmadani

import com.quran.data.source.DisplaySize
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.madani.R

internal class NewerNewMadaniPageProvider : NewMadaniPageProvider() {
  override val newMadaniBaseUrl = "$baseUrl/madani_1440"

  override fun getImageVersion() = 4
  override fun ayahUnderlineRaiseRatio() = 0f

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
    NewMadaniPageSizeCalculator(1352)

  override fun getPreviewTitle() = R.string.newer_new_madani_title

  override fun getPreviewDescription() = R.string.newer_new_madani_description
}
