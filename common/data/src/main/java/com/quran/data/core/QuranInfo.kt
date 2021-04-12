package com.quran.data.core

import com.quran.data.core.QuranConstants.LAST_SURA
import com.quran.data.core.QuranConstants.MAX_AYAH
import com.quran.data.core.QuranConstants.MIN_AYAH
import com.quran.data.core.QuranConstants.NUMBER_OF_SURAS
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.data.source.QuranDataSource
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class QuranInfo @Inject constructor(quranDataSource: QuranDataSource) {
  private val suraPageStart = quranDataSource.getPageForSuraArray()
  private val pageSuraStart = quranDataSource.getSuraForPageArray()
  private val pageAyahStart = quranDataSource.getAyahForPageArray()
  private val juzPageStart = quranDataSource.getPageForJuzArray()
  private val juzPageOverride: Map<Int, Int> = quranDataSource.getJuzDisplayPageArrayOverride()
  private val pageRub3Start = quranDataSource.getQuarterStartByPage()
  private val suraNumAyahs = quranDataSource.getNumberOfAyahsForSuraArray()
  private val suraIsMakki = quranDataSource.getIsMakkiBySuraArray()
  val quarters = quranDataSource.getQuartersArray()

  val numberOfPages = quranDataSource.getNumberOfPages()
  val numberOfPagesDual = numberOfPages / 2

  fun getStartingPageForJuz(juz: Int): Int {
    return juzPageStart[juz - 1]
  }

  fun getPageNumberForSura(sura: Int): Int {
    return suraPageStart[sura - 1]
  }

  fun getSuraNumberFromPage(page: Int): Int {
    var sura = -1
    for (i in 0 until NUMBER_OF_SURAS) {
      if (suraPageStart[i] == page) {
        sura = i + 1
        break
      } else if (suraPageStart[i] > page) {
        sura = i
        break
      }
    }
    return sura
  }

  fun getListOfSurahWithStartingOnPage(page: Int): List<Int> {
    val startIndex = pageSuraStart[page - 1] - 1
    val result: MutableList<Int> = ArrayList()
    for (i in startIndex until NUMBER_OF_SURAS) {
      if (suraPageStart[i] == page) {
        result.add(i + 1)
      } else if (suraPageStart[i] > page) break
    }
    return result
  }

  fun getVerseRangeForPage(page: Int): VerseRange {
    val result = getPageBounds(page)
    val versesInRange: Int = 1 + abs(
        getAyahId(result[0], result[1]) - getAyahId(result[2], result[3])
    )
    return VerseRange(result[0], result[1], result[2], result[3], versesInRange)
  }

  fun getFirstAyahOnPage(page: Int): Int {
    return pageAyahStart[page - 1]
  }

  fun getPageBounds(inputPage: Int): IntArray {
    val page =
      when {
        inputPage > numberOfPages -> numberOfPages
        inputPage < 1 -> 1
        else -> inputPage
      }

    val bounds = IntArray(4)
    bounds[0] = pageSuraStart[page - 1]
    bounds[1] = pageAyahStart[page - 1]
    if (page == numberOfPages) {
      bounds[2] = LAST_SURA
      bounds[3] = 6
    } else {
      val nextPageSura = pageSuraStart[page]
      val nextPageAyah = pageAyahStart[page]
      if (nextPageSura == bounds[0]) {
        bounds[2] = bounds[0]
        bounds[3] = nextPageAyah - 1
      } else {
        if (nextPageAyah > 1) {
          bounds[2] = nextPageSura
          bounds[3] = nextPageAyah - 1
        } else {
          bounds[2] = nextPageSura - 1
          bounds[3] = suraNumAyahs[bounds[2] - 1]
        }
      }
    }
    return bounds
  }

  fun getSuraOnPage(page: Int) = pageSuraStart[page - 1]

  fun getJuzFromPage(page: Int): Int {
    for (i in juzPageStart.indices) {
      if (juzPageStart[i] > page) {
        return i
      } else if (juzPageStart[i] == page) {
        return i + 1
      }
    }
    return 30
  }

  fun getRub3FromPage(page: Int): Int {
    return if (page > numberOfPages || page < 1) -1 else pageRub3Start[page - 1]
  }

  fun getPageFromSuraAyah(sura: Int, ayah: Int): Int {
    // basic bounds checking
    val currentAyah = if (ayah == 0) 1 else ayah
    if (sura < 1 || sura > NUMBER_OF_SURAS ||currentAyah < MIN_AYAH || currentAyah > MAX_AYAH) {
      return -1
    }

    // what page does the sura start on?
    var index = suraPageStart[sura - 1] - 1
    while (index < numberOfPages) {
      // what's the first sura in that page?
      val ss = pageSuraStart[index]

      // if we've passed the sura, return the previous page
      // or, if we're at the same sura and passed the ayah
      if (ss > sura || ss == sura &&
          pageAyahStart[index] > currentAyah
      ) {
        break
      }

      // otherwise, look at the next page
      index++
    }
    return index
  }

  fun getAyahId(sura: Int, ayah: Int): Int {
    var ayahId = 0
    for (i in 0 until sura - 1) {
      ayahId += suraNumAyahs[i]
    }
    ayahId += ayah
    return ayahId
  }

  fun getNumberOfAyahs(sura: Int): Int {
    return if (sura < 1 || sura > NUMBER_OF_SURAS) -1 else suraNumAyahs[sura - 1]
  }

  fun getPageFromPosition(
    position: Int,
    isDualPagesVisible: Boolean
  ): Int {
    return if (isDualPagesVisible) {
      (numberOfPagesDual - position) * 2
    } else {
      numberOfPages - position
    }
  }

  fun getPositionFromPage(
    page: Int,
    isDualPagesVisible: Boolean
  ): Int {
    return if (isDualPagesVisible) {
      val pageToUse = if (page % 2 != 0) { page + 1 } else { page }
      numberOfPagesDual - pageToUse / 2
    } else {
      numberOfPages - page
    }
  }

  /**
   * Gets the juz' that should be printed at the top of the page
   * This may be different than the actual juz' for the page (for example, juz' 7 starts at page
   * 121, but despite this, the title of the page is juz' 6).
   *
   * @param page the page
   * @return the display juz' display string for the page
   */
  fun getJuzForDisplayFromPage(page: Int): Int {
    val actualJuz = getJuzFromPage(page)
    val overriddenJuz = juzPageOverride[page]
    return overriddenJuz ?: actualJuz
  }

  fun getSuraAyahFromAyahId(ayahId: Int): SuraAyah? {
    var sura = 0
    var ayahIdentifier = ayahId
    while (ayahIdentifier > suraNumAyahs[sura]) {
      ayahIdentifier -= suraNumAyahs[sura++]
    }
    return SuraAyah(sura + 1, ayahIdentifier)
  }

  fun getQuarterByIndex(quarter: Int) = quarters[quarter]

  fun getJuzFromSuraAyah(sura: Int, ayah: Int, juz: Int): Int {
    if (juz == 30) {
      return juz
    }

    // get the starting point of the next juz'
    val lastQuarter = quarters[juz * 8]
    // if we're after that starting point, return juz + 1
    return if (sura > lastQuarter[0] || lastQuarter[0] == sura && ayah >= lastQuarter[1]) {
      juz + 1
    } else {
      // otherwise just return this juz
      juz
    }
  }

  fun isMakki(sura: Int) = suraIsMakki[sura - 1]
}
