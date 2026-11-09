package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtilsTest {

  companion object {
    private const val TEST_MAX_UNZIPPED_SIZE = 5 * 1024 * 1024 // 5 mb
    private const val CLI_ROOT_DIRECTORY = "src/test/resources"
  }

  private lateinit var destinationDirectory: File
  private val listener =
    ZipUtils.ZipListener { _: Any?, _: Int, _: Int -> }

  @Before
  fun setup() {
    destinationDirectory = File(CLI_ROOT_DIRECTORY, "tmp")
    destinationDirectory.mkdir()
  }

  @After
  fun cleanup() {
    removeDirectory(destinationDirectory)
  }

  private fun removeDirectory(file: File) {
    if (file.isDirectory) {
      file.listFiles()?.let { files ->
        for (directoryFile in files) {
          removeDirectory(directoryFile)
        }
      }
    }
    require(file.delete()) { "failed to delete: $file" }
  }

  @Test
  fun testFileWithSmallMaxUnzipSize() {
    // max size is 500mb - make it 5mb for the test
    ZipUtils.MAX_UNZIPPED_SIZE = TEST_MAX_UNZIPPED_SIZE
    var e: RuntimeException? = null
    try {
      // thanks to https://github.com/commonsguy/cwac-security for this zip file
      ZipUtils.unzipFile(File(CLI_ROOT_DIRECTORY, "zip_file_100mb.zip"),
        destinationDirectory, null, listener)
    } catch (ise: IllegalStateException) {
      e = ise
    }

    assertThat(e).isNotNull()
  }

  @Test
  fun testNormalFile() {
    var e: RuntimeException? = null
    try {
      // thanks to https://github.com/commonsguy/cwac-security for this zip file
      ZipUtils.unzipFile(File(CLI_ROOT_DIRECTORY, "zip_file_100mb.zip"),
        destinationDirectory, null, listener)
    } catch (ise: IllegalStateException) {
      e = ise
    }

    assertThat(e).isNull()
  }

  @Test
  fun testZipFileWritesOutside() {
    var e: RuntimeException? = null
    try {
      // thanks to https://github.com/commonsguy/cwac-security for this zip file
      ZipUtils.unzipFile(File(CLI_ROOT_DIRECTORY, "zip_file_writes_outside.zip"),
        destinationDirectory, null, listener)
    } catch (ise: IllegalStateException) {
      e = ise
    }

    assertThat(e).isNotNull()
  }

  @Test
  fun testEntryExceedingLimitIsRejectedNotTruncated() {
    // use a limit that is not a multiple of the internal buffer so an oversized
    // entry would stop mid-buffer rather than landing exactly on the limit
    val original = ZipUtils.MAX_UNZIPPED_SIZE
    ZipUtils.MAX_UNZIPPED_SIZE = 1000
    val zipFile = File.createTempFile("oversized", ".zip", destinationDirectory)
    try {
      ZipOutputStream(zipFile.outputStream()).use { zos ->
        zos.putNextEntry(ZipEntry("big.bin"))
        zos.write(ByteArray(10_000))
        zos.closeEntry()
      }

      var e: RuntimeException? = null
      try {
        ZipUtils.unzipFile(zipFile, destinationDirectory, null, listener)
      } catch (ise: IllegalStateException) {
        e = ise
      }

      // the entry (10kb) is larger than the 1kb limit, so it must be rejected
      // rather than silently extracted in truncated form
      assertThat(e).isNotNull()
    } finally {
      ZipUtils.MAX_UNZIPPED_SIZE = original
    }
  }
}
