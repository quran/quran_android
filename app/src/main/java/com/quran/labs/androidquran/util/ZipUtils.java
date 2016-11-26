package com.quran.labs.androidquran.util;

import android.support.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import timber.log.Timber;

public class ZipUtils {

  private static final int BUFFER_SIZE = 512;
  private static final int MAX_FILES = 2048; // Max number of files

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static int MAX_UNZIPPED_SIZE = 0x1f400000; // Max size of unzipped data, 500MB

  /**
   * Unzip a file given the file, an item, and the listener
   * Does similar checks to those shown in rule 0's IDS04-J rule from:
   * https://www.securecoding.cert.org/confluence/display/java
   * @param zipFile the path to the zip file
   * @param destDirectory the directory to extract the file in
   * @param item any data object passed back to the listener
   * @param listener a progress listener
   * @param <T> the type of the item passed in
   * @return a boolean representing whether we succeeded to unzip the file or not
   */
  public static <T> boolean unzipFile(String zipFile,
      String destDirectory, T item, ZipListener<T> listener){
    try {
      File file = new File(zipFile);
      Timber.d("unzipping %s, size: %d", zipFile, file.length());

      ZipFile zip = new ZipFile(file, ZipFile.OPEN_READ);
      int numberOfFiles = zip.size();
      Enumeration<? extends ZipEntry> entries = zip.entries();

      String canonicalPath = new File(destDirectory).getCanonicalPath();

      long total = 0;
      int processedFiles = 0;
      while (entries.hasMoreElements()) {
        processedFiles++;
        ZipEntry entry = entries.nextElement();

        File currentEntryFile = new File(destDirectory, entry.getName());
        if (currentEntryFile.getCanonicalPath().startsWith(canonicalPath)) {
          if (entry.isDirectory()) {
            if (!currentEntryFile.exists()) {
              currentEntryFile.mkdirs();
            }
            continue;
          } else if (currentEntryFile.exists()) {
            // delete files that already exist
            currentEntryFile.delete();
          }

          InputStream is = zip.getInputStream(entry);
          FileOutputStream ostream = new FileOutputStream(currentEntryFile);

          int size;
          byte[] buf = new byte[BUFFER_SIZE];
          while (total + BUFFER_SIZE <= MAX_UNZIPPED_SIZE && (size = is.read(buf)) > 0) {
            ostream.write(buf, 0, size);
            total += size;
          }
          is.close();
          ostream.close();

          if (processedFiles >= MAX_FILES || total >= MAX_UNZIPPED_SIZE) {
            throw new IllegalStateException("Invalid zip file.");
          }

          if (listener != null) {
            listener.onProcessingProgress(item, processedFiles, numberOfFiles);
          }
        } else {
          throw new IllegalStateException("Invalid zip file.");
        }
      }

      zip.close();
      return true;
    }
    catch (IOException ioe) {
      Timber.e(ioe, "Error unzipping file");
      return false;
    }
  }

  public interface ZipListener<T> {
    void onProcessingProgress(T obj, int processed, int total);
  }
}
