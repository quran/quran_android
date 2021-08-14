package com.quran.labs.androidquran.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import androidx.core.content.ContextCompat;

import com.quran.labs.androidquran.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Based on:
 * - http://sapienmobile.com/?p=204
 * - http://stackoverflow.com/a/15612964
 * - http://renzhi.ca/2012/02/03/how-to-list-all-sd-cards-on-android/
 */
public class StorageUtils {

  /**
   * @return A List of all storage locations available
   */
  public static List<Storage> getAllStorageLocations(Context context) {

    /*
      Condition is unwrapped since SDK is always >= 21

      For Kitkat and above, we rely on Environment.getExternalFilesDirs to give us a list
      of application writable directories (none of which require WRITE_EXTERNAL_STORAGE on
      Kitkat and above).

      Previously, we only would show anything if there were at least 2 entries. For M,
      some changes were made, such that on M, we even show this if there is only one
      entry.

      Irrespective of whether we require 1 entry (M) or 2 (Kitkat and L), we add an
      additional entry explicitly for the sdcard itself, (the one requiring
      WRITE_EXTERNAL_STORAGE to write).

      Thus, on Kitkat, the user may either:
      a. not see any item (if there's only one entry returned by getExternalFilesDirs, we won't
      show any options since it's the same sdcard and we have the permission and the user can't
      revoke it pre-Kitkat), or
      b. see 3+ items - /sdcard, and then at least 2 external fiels directories.

      on M, the user will always see at least 2 items (the external files dir and the actual
      external storage directory), and potentially more (depending on how many items are returned
      by getExternalFilesDirs).
     */
    List<Storage> result = new ArrayList<>();
    int limit = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 1 : 2;
    final File[] mountPoints = ContextCompat.getExternalFilesDirs(context, null);
    if (mountPoints.length >= limit) {

      // internal files dir
      result.add(
          new Storage(context.getString(R.string.prefs_sdcard_internal),
              context.getFilesDir().getAbsolutePath()));

      // all of these are "external" files dir or related

      int number = 1;
      // this first one is not safe to write on starting from Android 11 - /sdcard.
      if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        // don't show the /sdcard option for people on Android 11
        result.add(new Storage(context.getString(R.string.prefs_sdcard_external, number++),
            Environment.getExternalStorageDirectory().getAbsolutePath(),
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M));
      }

      // add all the remaining places
      for (File mountPoint : mountPoints) {
        result.add(new Storage(context.getString(R.string.prefs_sdcard_external, number++),
            mountPoint.getAbsolutePath()));
      }
    }
    return result;
  }

  public static class Storage {
    private final String label;
    private final String mountPoint;
    private final boolean requiresPermission;

    private long freeSpace;

    Storage(String label, String mountPoint) {
      this(label, mountPoint, false);
    }

    Storage(String label, String mountPoint, boolean requiresPermission) {
      this.label = label;
      this.mountPoint = mountPoint;
      this.requiresPermission = requiresPermission;
      computeSpace();
    }

    private void computeSpace() {
      StatFs stat = new StatFs(mountPoint);
      long bytesAvailable;
      bytesAvailable = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
      // Convert total bytes to megabytes
      freeSpace = bytesAvailable / (1024 * 1024);
    }

    public String getLabel() {
      return label;
    }

    public String getMountPoint() {
      return mountPoint;
    }

    /**
     * @return available free size in Megabytes
     */
    public long getFreeSpace() {
      return freeSpace;
    }

    public boolean doesRequirePermission() {
      return requiresPermission;
    }
  }
}
