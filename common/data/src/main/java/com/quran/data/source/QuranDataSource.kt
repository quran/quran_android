package com.quran.data.source

import com.quran.data.model.SuraAyah

interface QuranDataSource {
  val numberOfPages: Int
  val pageForSuraArray: IntArray
  val suraForPageArray: IntArray
  val ayahForPageArray: IntArray
  val pageForJuzArray: IntArray
  val juzDisplayPageArrayOverride: Map<Int, Int>
  val numberOfAyahsForSuraArray: IntArray
  val isMakkiBySuraArray: BooleanArray
  val quarterStartByPage: IntArray
  val quartersArray: Array<SuraAyah>
  val manzilPageArray: Array<Int>
  val haveSidelines: Boolean
  val pagesToSkip: Int
}
