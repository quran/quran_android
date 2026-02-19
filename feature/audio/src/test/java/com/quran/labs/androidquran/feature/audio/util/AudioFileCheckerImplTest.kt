package com.quran.labs.androidquran.feature.audio.util

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.feature.audio.util.fakes.FakeHashCalculator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioFileCheckerImplTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var fakeHashCalculator: FakeHashCalculator
  private lateinit var checker: AudioFileCheckerImpl

  private val gappedQari = QariItem(
    id = 1,
    name = "Gapped Sheikh",
    url = "https://example.com/gapped/",
    path = "gapped_sheikh",
    hasGaplessAlternative = false,
    db = null
  )

  private val gaplessQari = QariItem(
    id = 2,
    name = "Gapless Sheikh",
    url = "https://example.com/gapless/",
    path = "gapless_sheikh",
    hasGaplessAlternative = false,
    db = "gapless_sheikh"
  )

  @Before
  fun setup() {
    fakeHashCalculator = FakeHashCalculator()
    checker = AudioFileCheckerImpl(fakeHashCalculator, tempFolder.root.absolutePath + File.separator)
  }

  @Test
  fun `isQariOnFilesystem returns true when qari directory exists`() {
    // Arrange
    tempFolder.newFolder("gapped_sheikh")

    // Act
    val result = checker.isQariOnFilesystem(gappedQari)

    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `isQariOnFilesystem returns false when qari directory does not exist`() {
    // Arrange — no directory created

    // Act
    val result = checker.isQariOnFilesystem(gappedQari)

    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `doesFileExistForQari returns true for gapless qari when file is present`() {
    // Arrange
    val qariDir = tempFolder.newFolder("gapless_sheikh")
    File(qariDir, "001.mp3").createNewFile()

    // Act
    val result = checker.doesFileExistForQari(gaplessQari, "001.mp3")

    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `doesFileExistForQari returns false for gapless qari when file is absent`() {
    // Arrange
    tempFolder.newFolder("gapless_sheikh")

    // Act
    val result = checker.doesFileExistForQari(gaplessQari, "001.mp3")

    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `doesFileExistForQari returns true for gapped qari when file is in sura subdirectory`() {
    // Arrange — gapped qari stores files under <path>/<sura>/<file>
    // file name "001001.mp3" -> sura directory "1"
    val qariDir = tempFolder.newFolder("gapped_sheikh")
    val suraDir = File(qariDir, "1")
    suraDir.mkdirs()
    File(suraDir, "001001.mp3").createNewFile()

    // Act
    val result = checker.doesFileExistForQari(gappedQari, "001001.mp3")

    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `doesFileExistForQari returns false for gapped qari when file is missing from sura subdirectory`() {
    // Arrange
    val qariDir = tempFolder.newFolder("gapped_sheikh")
    File(qariDir, "1").mkdirs()

    // Act
    val result = checker.doesFileExistForQari(gappedQari, "001001.mp3")

    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `doesHashMatchForQariFile returns true when hash matches`() {
    // Arrange
    val qariDir = tempFolder.newFolder("gapless_sheikh")
    val audioFile = File(qariDir, "001.mp3")
    audioFile.createNewFile()
    fakeHashCalculator.setHash(audioFile.absolutePath, "abc123")

    // Act
    val result = checker.doesHashMatchForQariFile(gaplessQari, "001.mp3", "abc123")

    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `doesHashMatchForQariFile returns false when hash differs`() {
    // Arrange
    val qariDir = tempFolder.newFolder("gapless_sheikh")
    val audioFile = File(qariDir, "001.mp3")
    audioFile.createNewFile()
    fakeHashCalculator.setHash(audioFile.absolutePath, "abc123")

    // Act
    val result = checker.doesHashMatchForQariFile(gaplessQari, "001.mp3", "wrong_hash")

    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `getDatabasePathForQari returns path for gapless qari`() {
    // Act
    val path = checker.getDatabasePathForQari(gaplessQari)

    // Assert
    assertThat(path).isNotNull()
    assertThat(path).contains("gapless_sheikh")
    assertThat(path).endsWith("gapless_sheikh.db")
  }

  @Test
  fun `getDatabasePathForQari returns null for gapped qari`() {
    // Act
    val path = checker.getDatabasePathForQari(gappedQari)

    // Assert
    assertThat(path).isNull()
  }

  @Test
  fun `doesDatabaseExist returns true when database file exists`() {
    // Arrange
    val dbFile = tempFolder.newFile("sheikh2.db")

    // Act
    val result = checker.doesDatabaseExist(dbFile.absolutePath)

    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `doesDatabaseExist returns false when database file does not exist`() {
    // Arrange
    val missingPath = tempFolder.root.absolutePath + "/nonexistent.db"

    // Act
    val result = checker.doesDatabaseExist(missingPath)

    // Assert
    assertThat(result).isFalse()
  }
}
