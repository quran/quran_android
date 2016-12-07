package com.quran.labs.androidquran.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;

public class ZipUtilsTest {
  private static final int TEST_MAX_UNZIPPED_SIZE = 5 * 1024 * 1024; // 5 mb
  private static final String CLI_ROOT_DIRECTORY = "src/test/resources";

  private String destinationDirectory;

  private ZipUtils.ZipListener<Object> listener = new ZipUtils.ZipListener<Object>() {
    @Override
    public void onProcessingProgress(Object obj, int processed, int total) {
    }
  };

  @Before
  public void setup() {
    destinationDirectory = CLI_ROOT_DIRECTORY + "/tmp";
    new File(destinationDirectory).mkdir();
  }

  @After
  public void cleanup() {
    removeDirectory(new File(destinationDirectory));
  }

  private void removeDirectory(File file) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File directoryFile : files) {
        removeDirectory(directoryFile);
      }
    }

    if (!file.delete()) {
      throw new IllegalArgumentException("failed to delete: " + file);
    }
  }

  @Test
  public void testFileWithSmallMaxUnzipSize() {
    // max size is 500mb - make it 5mb for the test
    ZipUtils.MAX_UNZIPPED_SIZE = TEST_MAX_UNZIPPED_SIZE;

    RuntimeException e = null;
    try {
      // thanks to https://github.com/commonsguy/cwac-security for this zip file
      ZipUtils.unzipFile(CLI_ROOT_DIRECTORY + "/zip_file_100mb.zip",
          destinationDirectory, null, listener);
    } catch (IllegalStateException ise) {
      e = ise;
    }

    assertThat(e).isNotNull();
  }

  @Test
  public void testNormalFile() {
    RuntimeException e = null;
    try {
      // thanks to https://github.com/commonsguy/cwac-security for this zip file
      ZipUtils.unzipFile(CLI_ROOT_DIRECTORY + "/zip_file_100mb.zip",
          destinationDirectory, null, listener);
    } catch (IllegalStateException ise) {
      e = ise;
    }

    assertThat(e).isNull();
  }

  @Test
  public void testZipFileWritesOutside() {
    RuntimeException e = null;
    try {
      // thanks to https://github.com/commonsguy/cwac-security for this zip file
      ZipUtils.unzipFile(CLI_ROOT_DIRECTORY + "/zip_file_writes_outside.zip",
          destinationDirectory, null, listener);
    } catch (IllegalStateException ise) {
      e = ise;
    }

    assertThat(e).isNotNull();
  }
}
