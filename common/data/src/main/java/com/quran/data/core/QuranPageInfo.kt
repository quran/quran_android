package com.quran.data.core

interface QuranPageInfo {
  fun juz(page: Int): String
  fun suraName(page: Int): String
  fun displayRub3(page: Int): String
  fun localizedPage(page: Int): String
  fun pageForSuraAyah(sura: Int, ayah: Int): Int
  fun manzilForPage(page: Int): String
  fun skippedPagesCount(): Int
}
