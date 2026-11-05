package com.quran.labs.androidquran.feature.audio

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.feature.audio.api.AudioFileUpdate
import com.quran.labs.androidquran.feature.audio.api.AudioSetUpdate
import com.quran.labs.androidquran.feature.audio.util.AudioFileChecker
import com.quran.labs.androidquran.feature.audio.util.AudioFileCheckerImpl
import com.quran.labs.androidquran.feature.audio.util.fakes.FakeHashCalculator
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioUpdaterTest {
  @get:Rule
  val tempFolder = TemporaryFolder()

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
      QariItem(1, "Gapped Sheikh", "https://url1/", path = "sheikh1", hasGaplessAlternative = false, db = null),
      QariItem(2, "Gapless Sheikh", "https://url2/", path = "sheikh2", hasGaplessAlternative = false, db = "sheikh2")
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

  @Test
  fun testAudioUpdaterFindsChangedGappedFileOnDisk() {
    val suraDir = File(tempFolder.root, "sheikh1/2").apply { mkdirs() }
    val changedFile = File(suraDir, "255.mp3").apply { createNewFile() }
    val unchangedFile = File(suraDir, "256.mp3").apply { createNewFile() }
    val hashCalculator = FakeHashCalculator().apply {
      setHash(changedFile.absolutePath, "oldSum")
      setHash(unchangedFile.absolutePath, "currentSum")
    }
    val updates = listOf(
        AudioSetUpdate("https://url1/", null,
            listOf(AudioFileUpdate("002255.mp3", "newSum"),
                AudioFileUpdate("002256.mp3", "currentSum"),
                AudioFileUpdate("002257.mp3", "newSum")))
    )

    val localUpdates = AudioUpdater.computeUpdates(
        updates, qaris, diskFileChecker(hashCalculator), versionOneDatabaseChecker)
    assertThat(localUpdates).hasSize(1)
    assertThat(localUpdates.first().qari).isEqualTo(qaris[0])
    assertThat(localUpdates.first().files).containsExactly("002255.mp3")
  }

  @Test
  fun testAudioUpdaterFindsChangedGaplessFileOnDisk() {
    val qariDir = File(tempFolder.root, "sheikh2").apply { mkdirs() }
    val changedFile = File(qariDir, "002.mp3").apply { createNewFile() }
    val unchangedFile = File(qariDir, "003.mp3").apply { createNewFile() }
    val hashCalculator = FakeHashCalculator().apply {
      setHash(changedFile.absolutePath, "oldSum")
      setHash(unchangedFile.absolutePath, "currentSum")
    }
    val updates = listOf(
        AudioSetUpdate("https://url2/", null,
            listOf(AudioFileUpdate("002.mp3", "newSum"),
                AudioFileUpdate("003.mp3", "currentSum"),
                AudioFileUpdate("004.mp3", "newSum")))
    )

    val localUpdates = AudioUpdater.computeUpdates(
        updates, qaris, diskFileChecker(hashCalculator), versionOneDatabaseChecker)
    assertThat(localUpdates).hasSize(1)
    assertThat(localUpdates.first().qari).isEqualTo(qaris[1])
    assertThat(localUpdates.first().files).containsExactly("002.mp3")
  }

  private fun diskFileChecker(hashCalculator: FakeHashCalculator) =
    AudioFileCheckerImpl(hashCalculator, tempFolder.root.absolutePath + File.separator)
}
