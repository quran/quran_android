package com.quran.data.page.provider.tajweed

import com.quran.data.page.provider.edition.HafsEditionPageProvider
import com.quran.labs.androidquran.pages.madani.R

/**
 * The Madani mushaf with its tajweed rules in color: the same pages, ayat and reciters as the
 * Madani page type; only its images and glyph boxes differ. Shown under Page Type (experimental).
 */
class TajweedPageProvider : HafsEditionPageProvider(
  folder = "tajweed",
  width = "1280",
  imageVersion = 7,
  directory = "tajweed",
  title = R.string.tajweed_title,
  description = R.string.tajweed_description,
) {
  companion object {
    const val KEY = "tajweed"
  }
}
