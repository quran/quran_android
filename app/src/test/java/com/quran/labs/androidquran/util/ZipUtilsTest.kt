package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class ZipUtilsTest {

  companion object {
    private const val TEST_MAX_UNZIPPED_SIZE = 5 * 1024 * 1024 // 5 mb
    private const val CLI_ROOT_DIRECTORY = "src/test/resources"
  }

  private lateinit var destinationDirectory: String
  private val listener =
    ZipUtils.ZipListener { _: Any?, _: Int, _: Int -> }

  @Before
  fun setup() {
    destinationDirectory = "$CLI_ROOT_DIRECTORY/tmp"
    File(destinationDirectory).mkdir()
  }

  @After
  fun cleanup() {
    removeDirectory(File(destinationDirectory))
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
      ZipUtils.unzipFile("$CLI_ROOT_DIRECTORY/zip_file_100mb.zip",
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
      ZipUtils.unzipFile("$CLI_ROOT_DIRECTORY/zip_file_100mb.zip",
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
      ZipUtils.unzipFile("$CLI_ROOT_DIRECTORY/zip_file_writes_outside.zip",
        destinationDirectory, null, listener)
    } catch (ise: IllegalStateException) {
      e = ise
    }

    assertThat(e).isNotNull()
  }
}
