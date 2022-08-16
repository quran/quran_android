package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.pageinfo.common.MadaniDataSource
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.audio.util.QariUtil
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when` as whenever

class AudioUtilsTest {

  @Test
  fun testGetLastAyahWithNewSurahOnNextPageForMadani() {
    val pageProviderMock = Mockito.mock(PageProvider::class.java)
    whenever(pageProviderMock.getDataSource())
      .thenReturn(MadaniDataSource())
    val quranInfo = QuranInfo(MadaniDataSource())
    val audioUtils =
      AudioUtils(
        quranInfo,
        Mockito.mock(QuranFileUtils::class.java),
        Mockito.mock(QariUtil::class.java)
      )
    // mode 1 is PAGE
    val lastAyah = audioUtils.getLastAyahToPlay(
      SuraAyah(sura = 109, ayah = 1),
      currentPage = 603,
      mode = 1,
      isDualPageVisible = false
    )
    Assert.assertNotNull(lastAyah)
    Assert.assertEquals(5, lastAyah!!.ayah.toLong())
    Assert.assertEquals(111, lastAyah.sura.toLong())
  }

  @Test
  fun testGetLastAyahWhenPlayingWithSuraBounds() {
    val pageProviderMock = Mockito.mock(PageProvider::class.java)
    whenever(pageProviderMock.getDataSource())
      .thenReturn(MadaniDataSource())
    val quranInfo = QuranInfo(MadaniDataSource())
    val audioUtils =
      AudioUtils(
        quranInfo,
        Mockito.mock(QuranFileUtils::class.java),
        Mockito.mock(QariUtil::class.java)
      )
    // mode 2 is SURA
    val lastAyah = audioUtils.getLastAyahToPlay(SuraAyah(2, 6), 3, 2, false)
    Assert.assertNotNull(lastAyah)
    Assert.assertEquals(286, lastAyah!!.ayah.toLong())
    Assert.assertEquals(2, lastAyah.sura.toLong())
  }

  @Test
  fun testSuraTawbaDoesNotNeedBasmallah() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val audioUtils =
      AudioUtils(
        quranInfo,
        Mockito.mock(QuranFileUtils::class.java),
        Mockito.mock(QariUtil::class.java)
      )

    // start after ayah 1 of sura anfal
    val start = SuraAyah(8, 2)
    // finish in sura tawbah, so no basmallah needed here
    val ending = SuraAyah(9, 100)

    // overall don't need a basmallah
    Assert.assertFalse(audioUtils.doesRequireBasmallah(start, ending))
  }

  @Test
  fun testNeedBasmallahAcrossRange() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val audioUtils =
      AudioUtils(
        quranInfo,
        Mockito.mock(QuranFileUtils::class.java),
        Mockito.mock(QariUtil::class.java)
      )
    val start = SuraAyah(8, 1)
    val ending = SuraAyah(10, 2)
    // should need a basmallah due to 10:1
    Assert.assertTrue(audioUtils.doesRequireBasmallah(start, ending))
  }

  @Test
  fun testLastAyahForFirstAyahWithPageDownload() {
    val audioUtils = AudioUtils(
      QuranInfo(MadaniDataSource()),
      Mockito.mock(QuranFileUtils::class.java),
      Mockito.mock(QariUtil::class.java)
    )
    val start = SuraAyah(56, 51)
    val end = audioUtils.getLastAyahToPlay(
      start,
      currentPage = 536,
      mode = 1,
      isDualPageVisible = false
    )
    assertThat(end).isEqualTo(SuraAyah(56, 76))
  }
}
