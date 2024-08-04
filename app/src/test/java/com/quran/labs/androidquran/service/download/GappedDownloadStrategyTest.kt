package com.quran.labs.androidquran.service.download

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import org.junit.Test

class GappedDownloadStrategyTest {

  @Test
  fun testDownloadFileCountOneSura() {
    val startAyah = SuraAyah(114, 1)
    val endAyah = SuraAyah(114, 6)
    val strategy = strategy(startAyah, endAyah)
    // 6 ayat in sura Nas plus 1 for the basmallah
    assertThat(strategy.fileCount()).isEqualTo(7)
  }

  @Test
  fun testDownloadFileCountForSuraRange() {
    val startAyah = SuraAyah(113, 1)
    val endAyah = SuraAyah(114, 6)
    val strategy = strategy(startAyah, endAyah)
    // sura Falaq: 5 ayat, sura Nas: 5 ayat, plus basmallah
    assertThat(strategy.fileCount()).isEqualTo((5 + 6) + 1)
  }

  @Test
  fun testDownloadFiles() {
    val startSura = SuraAyah(1, 1)
    val endSura = SuraAyah(114, 6)
    val notificationDetails = NotificationDetails("title", "key", 1, null)

    val strategy = strategy(startSura, endSura, notificationDetails)
    val result = strategy.downloadFiles()
    assertThat(result).isTrue()
    assertThat(notificationDetails.currentFile).isEqualTo(6236 + 1)
    assertThat(notificationDetails.sura).isEqualTo(114)
    assertThat(notificationDetails.ayah).isEqualTo(6)
  }

  private fun strategy(
    startAyah: SuraAyah,
    endAyah: SuraAyah,
    notificationDetails: NotificationDetails = NotificationDetails("title", "key", 1, null),
    lambda: (String, String, String, NotificationDetails) -> Boolean = { _, _, _, _ -> true }
  ): DownloadStrategy {
    return GappedDownloadStrategy(
      startAyah,
      endAyah,
      "https://test.quran.com/audio/sheikh/%d.mp3",
      QuranInfo(MadaniDataSource()),
      "destination",
      NoOpQuranDownloadNotifier(),
      notificationDetails,
      lambda
    )
  }
}
