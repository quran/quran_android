package com.quran.data.source

interface QuranDataSource {
  fun getNumberOfPages() : Int
  fun getPageForSuraArray() : IntArray
  fun getAyahForPageArray() : IntArray
  fun getPageForJuzArray() : IntArray
  fun getNumberOfAyahsForSuraArray() : IntArray
  fun getIsMakkiBySuraArray() : BooleanArray
  fun getQuartersArray() : Array<IntArray>
}
