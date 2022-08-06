package com.quran.labs.androidquran.feature.audio

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.feature.audio.api.AudioFileUpdate
import com.quran.labs.androidquran.feature.audio.api.AudioSetUpdate
import com.quran.labs.androidquran.feature.audio.util.AudioFileChecker
import org.junit.Test

class AudioUpdaterTest {
  private val allFilesUpdatedFileChecker = object : AudioFileChecker {
    override fun isQariOnFilesystem(qari: QariItem) = true
    override fun doesFileExistForQari(qari: QariItem, file: String) = true
    override fun doesHashMatchForQariFile(qari: QariItem, file: String, hash: String) = true
    override fun getDatabasePathForQari(qari: QariItem): String? = if (qari.isGapless) "" else null
    override fun doesDatabaseExist(path: String): Boolean = true
  }

  private val allFilesUpdatedNoDatabaseFileChecker = object : AudioFileChecker {
    override fun isQariOnFilesystem(qari: QariItem) = true
    override fun doesFileExistForQari(qari: QariItem, file: String) = true
    override fun doesHashMatchForQariFile(qari: QariItem, file: String, hash: String) = true
    override fun getDatabasePathForQari(qari: QariItem): String? = if (qari.isGapless) "" else null
    override fun doesDatabaseExist(path: String): Boolean = false
  }

  private val allFilesOutdatedFileChecker = object : AudioFileChecker {
    override fun isQariOnFilesystem(qari: QariItem) = true
    override fun doesFileExistForQari(qari: QariItem, file: String) = true
    override fun doesHashMatchForQariFile(qari: QariItem, file: String, hash: String) = false
    override fun getDatabasePathForQari(qari: QariItem): String? = if (qari.isGapless) "" else null
    override fun doesDatabaseExist(path: String): Boolean = true
  }

  private val versionOneDatabaseChecker = object : VersionableDatabaseChecker {
    override fun getVersionForDatabase(path: String) = 1
  }

  private val qaris = listOf(
      QariItem(1, "Gapped Sheikh", "https://url1/", "sheikh1", false, null),
      QariItem(2, "Gapless Sheikh", "https://url2/", "sheikh2", false, "sheikh2")
  )

  @Test
  fun testAudioUpdaterWhenNothingToUpdate() {
    val updates = listOf(
        AudioSetUpdate("https://url1/", null,
            listOf(AudioFileUpdate("001001.mp3", "testSum")))
    )
    assertThat(
        AudioUpdater.computeUpdates(
            updates, qaris, allFilesUpdatedFileChecker, versionOneDatabaseChecker
        )
    ).isEmpty()
  }

  @Test
  fun testAudioUpdaterWhenFileHashMismatches() {
    val updates = listOf(
        AudioSetUpdate("https://url1/", null,
            listOf(AudioFileUpdate("001001.mp3", "testSum")))
    )

    val localUpdates = AudioUpdater.computeUpdates(
        updates, qaris, allFilesOutdatedFileChecker, versionOneDatabaseChecker)
    assertThat(localUpdates).hasSize(1)
    assertThat(localUpdates.first().qari).isEqualTo(qaris[0])
    assertThat(localUpdates.first().files).containsExactly("001001.mp3")
    assertThat(localUpdates.first().needsDatabaseUpgrade).isFalse()
  }

  @Test
  fun testAudioUpdaterWhenHashesMatchButDatabaseOutdated() {
    val updates = listOf(
        AudioSetUpdate("https://url1/", null,
            listOf(AudioFileUpdate("001001.mp3", "testSum"))),
        AudioSetUpdate("https://url2/", 2,
            listOf(AudioFileUpdate("010010.mp3", ""),
                AudioFileUpdate("114006.mp3", "")))
    )

    val localUpdates = AudioUpdater.computeUpdates(
        updates, qaris, allFilesUpdatedFileChecker, versionOneDatabaseChecker)
    assertThat(localUpdates).hasSize(1)
    assertThat(localUpdates.first().qari).isEqualTo(qaris[1])
    assertThat(localUpdates.first().files).isEmpty()
    assertThat(localUpdates.first().needsDatabaseUpgrade).isTrue()
  }

  @Test
  fun testAudioUpdaterWhenHashesMatchAndDatabaseDoesNotExist() {
    val updates = listOf(
        AudioSetUpdate("https://url1/", null,
            listOf(AudioFileUpdate("001001.mp3", "testSum"))),
        AudioSetUpdate("https://url2/", 2,
            listOf(AudioFileUpdate("010010.mp3", ""),
                AudioFileUpdate("114006.mp3", "")))
    )

    val localUpdates = AudioUpdater.computeUpdates(
        updates, qaris, allFilesUpdatedNoDatabaseFileChecker, versionOneDatabaseChecker)
    assertThat(localUpdates).isEmpty()
  }
}
