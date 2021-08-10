package com.quran.data.source

import com.quran.data.model.SuraAyah

interface QuranDataSource {
  fun getNumberOfPages() : Int
  fun getPageForSuraArray() : IntArray
  fun getSuraForPageArray() : IntArray
  fun getAyahForPageArray() : IntArray
  fun getPageForJuzArray() : IntArray
  fun getJuzDisplayPageArrayOverride(): Map<Int, Int>
  fun getNumberOfAyahsForSuraArray() : IntArray
  fun getIsMakkiBySuraArray() : BooleanArray
  fun getQuarterStartByPage() : IntArray
  fun getQuartersArray() : Array<SuraAyah>
}
