package com.quran.labs.androidquran.service.download

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.pageinfo.common.MadaniDataSource
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import org.junit.Test

class GaplessDownloadStrategyTest {

  @Test
  fun testDownloadFileCountOneSura() {
    val startAyah = SuraAyah(114, 1)
    val endAyah = SuraAyah(114, 6)
    val strategy = strategy(startAyah, endAyah, false)
    assertThat(strategy.fileCount()).isEqualTo(1)

    val alternativeStrategy = strategy(startAyah, endAyah, true)
    assertThat(alternativeStrategy.fileCount()).isEqualTo(2)
  }

  @Test
  fun testDownloadFileCountForSuraRange() {
    val startAyah = SuraAyah(78, 1)
    val endAyah = SuraAyah(114, 6)
    val strategy = strategy(startAyah, endAyah, false)
    // +1 because imagine sura 113 => 114 - it's really 114 - 113 + 1 files (2 files)
    assertThat(strategy.fileCount()).isEqualTo((114 - 78) + 1)

    // +1 because of the above
    // another +1 because of the database
    val alternativeStrategy = strategy(startAyah, endAyah, true)
    assertThat(alternativeStrategy.fileCount()).isEqualTo((114 - 78) + 1 + 1)
  }

  @Test
  fun testDownloadFiles() {
    val startSura = SuraAyah(1, 1)
    val endSura = SuraAyah(114, 6)
    val notificationDetails = NotificationDetails("title", "key", 1, null)

    val strategy = strategy(startSura, endSura, false, notificationDetails)
    val result = strategy.downloadFiles()
    assertThat(result).isTrue()
    assertThat(notificationDetails.currentFile).isEqualTo(114)
  }

  @Test
  fun testDownloadFilesWithDatabase() {
    val startSura = SuraAyah(1, 1)
    val endSura = SuraAyah(114, 6)
    val notificationDetails = NotificationDetails("title", "key", 1, null)

    val strategy = strategy(startSura, endSura, true, notificationDetails)
    val result = strategy.downloadFiles()
    assertThat(result).isTrue()
    assertThat(notificationDetails.currentFile).isEqualTo(115)
  }


  private fun strategy(
    startAyah: SuraAyah,
    endAyah: SuraAyah,
    withDatabase: Boolean,
    notificationDetails: NotificationDetails = NotificationDetails("title", "key", 1, null),
    lambda: (String, String, String, NotificationDetails) -> Boolean = { _, _, _, _ -> true }
  ): DownloadStrategy {
    return GaplessDownloadStrategy(
      startAyah,
      endAyah,
      QuranInfo(MadaniDataSource()),
      "https://test.quran.com/audio/sheikh/%d.mp3",
      "destination",
      NoOpQuranDownloadNotifier(),
      notificationDetails,
      if (withDatabase) "https://test.quran.com/databases/sheikh.zip" else null,
      lambda
    )
  }
}
