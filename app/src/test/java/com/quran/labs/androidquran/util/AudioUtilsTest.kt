package com.quran.labs.androidquran.util

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AudioUtilsTest {

  // QuranFileUtils is constructed with a real Robolectric context and FakePageProvider.
  // QariUtil is constructed with FakePageProvider directly — no Android context needed.
  private fun audioUtils(qaris: List<Qari> = emptyList()): AudioUtils {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakePageProvider = FakePageProvider(qaris)
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    // QuranScreenInfo requires a Display object; Robolectric has no replacement shadow for
    // DisplayManager.getDisplay(), so the deprecated WindowManager.defaultDisplay is used.
    // Can be removed once QuranScreenInfo is refactored to not require Display.
    @Suppress("DEPRECATION")
    val display = windowManager.defaultDisplay
    val pageSizeCalculator = fakePageProvider.getPageSizeCalculator(
      com.quran.data.source.DisplaySize(0, 0)
    )
    val quranScreenInfo = QuranScreenInfo(context, display, pageSizeCalculator)
    val quranFileUtils = QuranFileUtils(context, fakePageProvider, quranScreenInfo)
    val qariUtil = QariUtil(fakePageProvider)
    return AudioUtils(
      QuranInfo(MadaniDataSource()),
      quranFileUtils,
      qariUtil
    )
  }

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

  @Test
  fun testGetQariListKeepsGappedQarisUnlessHiddenByAnEmptyGaplessAlternative() {
    val gapless = Qari(1, R.string.app_name, "https://url1/", path = "gapless",
      hasGaplessAlternative = false, db = "gapless")
    val gappedOnly = Qari(2, R.string.app_name, "https://url2/", path = "gapped_only",
      hasGaplessAlternative = false)
    val gappedWithFiles = Qari(3, R.string.app_name, "https://url3/", path = "gapped_with_files",
      hasGaplessAlternative = true)
    val gappedWithoutFiles = Qari(4, R.string.app_name, "https://url4/",
      path = "gapped_without_files", hasGaplessAlternative = true)
    val audioUtils = audioUtils(listOf(gapless, gappedOnly, gappedWithFiles, gappedWithoutFiles))
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gappedWithFilesPath =
      audioUtils.getLocalQariUrl(QariItem.fromQari(context, gappedWithFiles))!!
    File(gappedWithFilesPath, "1").mkdirs()
    File(gappedWithFilesPath, "1/1.mp3").createNewFile()

    val qaris = audioUtils.getQariList(context)

    assertThat(qaris.map { it.id }).containsExactly(1, 2, 3)
  }
}
