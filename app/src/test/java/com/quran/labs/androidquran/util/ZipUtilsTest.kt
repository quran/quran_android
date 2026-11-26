package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtilsTest {

  companion object {
    private const val TEST_MAX_UNZIPPED_SIZE = 5 * 1024 * 1024 // 5 mb
    private const val DEFAULT_MAX_UNZIPPED_SIZE = 0x1f400000
    private const val CLI_ROOT_DIRECTORY = "src/test/resources"
  }

  private lateinit var destinationDirectory: File
  private var originalMaxUnzippedSize: Int = ZipUtils.MAX_UNZIPPED_SIZE
  private val listener =
    ZipUtils.ZipListener { _: Any?, _: Int, _: Int -> }

  @Before
  fun setup() {
    originalMaxUnzippedSize = ZipUtils.MAX_UNZIPPED_SIZE
    ZipUtils.MAX_UNZIPPED_SIZE = DEFAULT_MAX_UNZIPPED_SIZE
    destinationDirectory = File(CLI_ROOT_DIRECTORY, "tmp")
    destinationDirectory.mkdir()
  }

  @After
  fun cleanup() {
    ZipUtils.MAX_UNZIPPED_SIZE = DEFAULT_MAX_UNZIPPED_SIZE
    removeDirectory(destinationDirectory)
    removeDirectoryIfExists(File(CLI_ROOT_DIRECTORY, "tmp_evil"))
  }

  private fun removeDirectoryIfExists(file: File) {
    if (file.exists()) {
      removeDirectory(file)
    }
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
  fun testSiblingPrefixZipSlipIsRejected() {
    val zipFile = File(destinationDirectory, "sibling_slip.zip")
    val siblingDirName = "${destinationDirectory.name}_evil"
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
      zos.putNextEntry(ZipEntry("../$siblingDirName/pwn.txt"))
      zos.write("pwned".toByteArray())
      zos.closeEntry()
    }

    var e: RuntimeException? = null
    try {
      ZipUtils.unzipFile(zipFile, destinationDirectory, null, listener)
    } catch (ise: IllegalStateException) {
      e = ise
    }

    assertThat(e).isNotNull()
    assertThat(File(destinationDirectory.parentFile, "$siblingDirName/pwn.txt").exists()).isFalse()
  }

  @Test
  fun testNestedEntriesWithoutExplicitDirectoryEntriesExtract() {
    val zipFile = File(destinationDirectory, "nested_no_dirs.zip")
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
      zos.putNextEntry(ZipEntry("nested/dir/file.txt"))
      zos.write("hello".toByteArray())
      zos.closeEntry()
    }

    val result = ZipUtils.unzipFile(zipFile, destinationDirectory, null, listener)

    assertThat(result).isTrue()
    val extracted = File(destinationDirectory, "nested/dir/file.txt")
    assertThat(extracted.exists()).isTrue()
    assertThat(extracted.readText()).isEqualTo("hello")
  }

  @Test
  fun testProgressListenerReceivesItemAndCounts() {
    val zipFile = File(destinationDirectory, "progress.zip")
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
      zos.putNextEntry(ZipEntry("a.txt"))
      zos.write("one".toByteArray())
      zos.closeEntry()
      zos.putNextEntry(ZipEntry("b.txt"))
      zos.write("two".toByteArray())
      zos.closeEntry()
    }

    val reports = mutableListOf<Triple<String, Int, Int>>()
    val ok = ZipUtils.unzipFile(zipFile, destinationDirectory, "tag") { obj, processed, total ->
      reports.add(Triple(obj, processed, total))
    }

    assertThat(ok).isTrue()
    assertThat(reports).containsExactly(Triple("tag", 1, 2), Triple("tag", 2, 2)).inOrder()
  }

  @Test
  fun testExactBufferBoundaryExceedingMaxUnzippedSizeThrows() {
    ZipUtils.MAX_UNZIPPED_SIZE = 600
    val zipFile = File(destinationDirectory, "boundary.zip")
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
      zos.putNextEntry(ZipEntry("data.bin"))
      zos.write(ByteArray(650) { 1 })
      zos.closeEntry()
    }

    var e: RuntimeException? = null
    try {
      ZipUtils.unzipFile(zipFile, destinationDirectory, null, listener)
    } catch (ise: IllegalStateException) {
      e = ise
    }

    assertThat(e).isNotNull()
  }
}
