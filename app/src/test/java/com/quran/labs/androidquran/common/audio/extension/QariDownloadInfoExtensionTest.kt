package com.quran.labs.androidquran.common.audio.extension

import com.quran.data.model.SuraAyah
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.download.PartiallyDownloadedSura
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QariDownloadInfoExtensionTest {

  @Test
  fun checkDownloadedGaplessFiles() {
    val qari = Qari(1, 0, "url", "path", false, "database")
    val qariDownloadInfo = QariDownloadInfo.GaplessQariDownloadInfo(qari, listOf(1, 2), listOf(3))
    assertTrue(qariDownloadInfo.isRangeDownloaded(SuraAyah(1, 1), SuraAyah(2, 286)))
    // ranges are inclusive, and there are no partial files
    assertFalse(qariDownloadInfo.isRangeDownloaded(SuraAyah(3, 1), SuraAyah(3, 200)))
    assertFalse(qariDownloadInfo.isRangeDownloaded(SuraAyah(1, 1), SuraAyah(3, 1)))
  }

  @Test
  fun checkDownloadedGappedFiles() {
    val qari = Qari(1, 0, "url", "path", false, null)
    val qariDownloadInfo = QariDownloadInfo.GappedQariDownloadInfo(
      qari,
      listOf(1, 2),
      listOf(PartiallyDownloadedSura(3, 200, listOf(1)))
    )

    assertTrue(qariDownloadInfo.isRangeDownloaded(SuraAyah(1, 1), SuraAyah(2, 286)))
    // ranges are inclusive, and there are incomplete partial files
    assertFalse(qariDownloadInfo.isRangeDownloaded(SuraAyah(3, 1), SuraAyah(3, 200)))
    // ranges are inclusive, and there are complete partial files
    assertTrue(qariDownloadInfo.isRangeDownloaded(SuraAyah(1, 1), SuraAyah(3, 1)))
  }
}
