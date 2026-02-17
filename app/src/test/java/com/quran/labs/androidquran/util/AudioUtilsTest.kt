package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito

class AudioUtilsTest {

  // QuranFileUtils requires Android Context to construct, QariUtil requires PageProvider (15-method
  // interface). Neither is invoked by getLastAyahToPlay or doesRequireBasmallah — both are
  // constructor fillers. Mockito is retained until Robolectric is added (see Group A plan).
  private fun audioUtils() = AudioUtils(
    QuranInfo(MadaniDataSource()),
    Mockito.mock(QuranFileUtils::class.java),
    Mockito.mock(QariUtil::class.java)
  )

  @Test
  fun testGetLastAyahWithNewSurahOnNextPageForMadani() {
    // mode 1 is PAGE
    val lastAyah = audioUtils().getLastAyahToPlay(
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
    // mode 2 is SURA
    val lastAyah = audioUtils().getLastAyahToPlay(SuraAyah(2, 6), 3, 2, false)
    Assert.assertNotNull(lastAyah)
    Assert.assertEquals(286, lastAyah!!.ayah.toLong())
    Assert.assertEquals(2, lastAyah.sura.toLong())
  }

  @Test
  fun testSuraTawbaDoesNotNeedBasmallah() {
    val start = SuraAyah(8, 2)
    val ending = SuraAyah(9, 100)
    // start after ayah 1 of sura anfal, finish in sura tawbah — no basmallah needed
    Assert.assertFalse(audioUtils().doesRequireBasmallah(start, ending))
  }

  @Test
  fun testNeedBasmallahAcrossRange() {
    val start = SuraAyah(8, 1)
    val ending = SuraAyah(10, 2)
    // should need a basmallah due to 10:1
    Assert.assertTrue(audioUtils().doesRequireBasmallah(start, ending))
  }

  @Test
  fun testLastAyahForFirstAyahWithPageDownload() {
    val start = SuraAyah(56, 51)
    val end = audioUtils().getLastAyahToPlay(
      start,
      currentPage = 536,
      mode = 1,
      isDualPageVisible = false
    )
    assertThat(end).isEqualTo(SuraAyah(56, 76))
  }
}
