package com.quran.labs.androidquran.util

import androidx.annotation.VisibleForTesting
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

object ZipUtils {
  private const val BUFFER_SIZE = 16384
  private const val MAX_FILES = 12000

  @VisibleForTesting
  @JvmField
  var MAX_UNZIPPED_SIZE: Int = 0x1f400000 // Max size of unzipped data, 500MB

  /**
   * Unzip a file given the file, an item, and the listener.
   * Does similar checks to those shown in rule 0's IDS04-J rule from:
   * https://www.securecoding.cert.org/confluence/display/java
   *
   * @param zipFile the path to the zip file
   * @param destDirectory the directory to extract the file in
   * @param item any data object passed back to the listener
   * @param listener a progress listener
   * @param T the type of the item passed in
   * @return true if extraction succeeded, false on I/O failure
   */
  @JvmStatic
  fun <T> unzipFile(
    zipFile: File,
    destDirectory: File,
    item: T,
    listener: ZipListener<T>?
  ): Boolean {
    return try {
      Timber.d("unzipping %s, size: %d", zipFile, zipFile.length())

      ZipFile(zipFile, ZipFile.OPEN_READ).use { zip ->
        val numberOfFiles = zip.size()
        val entries = zip.entries()

        val canonicalPath = destDirectory.canonicalPath
        val canonicalDestWithSep = if (canonicalPath.endsWith(File.separator)) {
          canonicalPath
        } else {
          canonicalPath + File.separator
        }

        var total = 0L
        var processedFiles = 0
        val buf = ByteArray(BUFFER_SIZE)
        while (entries.hasMoreElements()) {
          processedFiles++
          val entry = entries.nextElement()

          val currentEntryFile = File(destDirectory, entry.name)
          val entryCanonicalPath = currentEntryFile.canonicalPath
          if (entry.isDirectory) {
            if (entryCanonicalPath != canonicalPath &&
              !entryCanonicalPath.startsWith(canonicalDestWithSep)
            ) {
              throw IllegalStateException("Invalid zip file.")
            }
            if (!currentEntryFile.isDirectory && !currentEntryFile.mkdirs() &&
              !currentEntryFile.isDirectory
            ) {
              throw IOException("Failed to create directory: $currentEntryFile")
            }
            continue
          }

          if (!entryCanonicalPath.startsWith(canonicalDestWithSep)) {
            throw IllegalStateException("Invalid zip file.")
          }

          val parent = currentEntryFile.parentFile
          if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Failed to create parent directory: $parent")
          }
          if (currentEntryFile.exists()) {
            // delete files that already exist
            currentEntryFile.delete()
          }

          zip.getInputStream(entry).use { input ->
            BufferedOutputStream(FileOutputStream(currentEntryFile), BUFFER_SIZE).use { output ->
              var size: Int
              while (input.read(buf).also { size = it } > 0) {
                if (total + size > MAX_UNZIPPED_SIZE) {
                  throw IllegalStateException("Invalid zip file.")
                }
                total += size
                output.write(buf, 0, size)
              }
            }
          }

          if (processedFiles >= MAX_FILES) {
            throw IllegalStateException("Invalid zip file.")
          }

          listener?.onProcessingProgress(item, processedFiles, numberOfFiles)
        }
      }

      true
    } catch (ioe: IOException) {
      Timber.e(ioe, "Error unzipping file")
      false
    }
  }

  fun interface ZipListener<T> {
    fun onProcessingProgress(obj: T, processed: Int, total: Int)
  }
}
