package com.quran.labs.androidquran.util

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.core.QuranPageInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper

class QuranPageInfoImpl(
  private val context: Context,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData
): QuranPageInfo {

  override fun juz(page: Int): String {
    return quranDisplayData.getJuzDisplayStringForPage(context, page)
  }

  override fun suraName(page: Int): String {
    return quranDisplayData.getSuraNameFromPage(context, page, true)
  }

  override fun displayRub3(page: Int): String {
    return QuranDisplayHelper.displayRub3(context, quranInfo, page)
  }

  override fun localizedPage(page: Int): String {
    return QuranUtils.getLocalizedNumber(context, page)
  }

  override fun pageForSuraAyah(sura: Int, ayah: Int): Int {
    return quranInfo.getPageFromSuraAyah(sura, ayah)
  }

  override fun manzilForPage(page: Int): String {
    return quranDisplayData.getManzilForPage(context, page)
  }

  override fun skippedPagesCount(): Int = quranInfo.skip
}
