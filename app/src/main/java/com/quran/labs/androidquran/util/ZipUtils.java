package com.quran.labs.androidquran.util;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.service.QuranDownloadService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import timber.log.Timber;

public class ZipUtils {
  public static final int BUFFER_SIZE = QuranDownloadService.BUFFER_SIZE;

  public static <T> boolean unzipFile(String zipFile,
      String destDirectory, T item, ZipListener<T> listener){
    try {
      File file = new File(zipFile);
      Crashlytics.log("unzipping " + zipFile + ", size: " + file.length());

      ZipFile zip = new ZipFile(file, ZipFile.OPEN_READ);
      int numberOfFiles = zip.size();
      Enumeration<? extends ZipEntry> entries = zip.entries();

      int processedFiles = 0;
      while (entries.hasMoreElements()) {
        processedFiles++;
        ZipEntry entry = entries.nextElement();
        if (entry.isDirectory()) {
          File f = new File(destDirectory, entry.getName());
          if (!f.exists()){
            f.mkdirs();
          }
          continue;
        }

        // delete files that already exist
        File f = new File(destDirectory, entry.getName());
        if (f.exists()){
          f.delete();
        }

        InputStream is = zip.getInputStream(entry);
        FileOutputStream ostream = new FileOutputStream(f);

        int size;
        byte[] buf = new byte[BUFFER_SIZE];
        while ((size = is.read(buf)) > 0)
          ostream.write(buf, 0, size);
        is.close();
        ostream.close();

        if (listener != null) {
          listener.onProcessingProgress(item, processedFiles, numberOfFiles);
        }
      }

      zip.close();
      file.delete();
      return true;
    }
    catch (IOException ioe) {
      Timber.e("Error unzipping file: ",ioe);
      return false;
    }
  }

  public interface ZipListener<T> {
    void onProcessingProgress(T obj, int processed, int total);
  }
}
