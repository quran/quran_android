package com.quran.labs.androidquran.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.content.ContextCompat;

import com.quran.labs.androidquran.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import timber.log.Timber;


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
      This first condition is the code moving forward, since the else case is a bunch
      of unsupported hacks.

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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      List<Storage> result = new ArrayList<>();
      int limit = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 1 : 2;
      final File[] mountPoints = ContextCompat.getExternalFilesDirs(context, null);
      if (mountPoints != null && mountPoints.length >= limit) {
        int typeId;
        if (!Environment.isExternalStorageRemovable() || Environment.isExternalStorageEmulated()) {
          typeId = R.string.prefs_sdcard_internal;
        } else {
          typeId = R.string.prefs_sdcard_external;
        }

        int number = 1;
        result.add(new Storage(context.getString(typeId, number),
            Environment.getExternalStorageDirectory().getAbsolutePath(),
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M));
        for (File mountPoint : mountPoints) {
          result.add(new Storage(context.getString(typeId, number++),
              mountPoint.getAbsolutePath()));
          typeId = R.string.prefs_sdcard_external;
        }
      }
      return result;
    } else {
      return getLegacyStorageLocations(context);
    }
  }

  /**
   * Attempt to return a list of storage locations pre-Kitkat.
   * @param context the context
   * @return the list of storage locations
   */
  private static List<Storage> getLegacyStorageLocations(Context context) {
    List<String> mounts = readMountsFile();

    // As per http://source.android.com/devices/tech/storage/config.html
    // device-specific vold.fstab file is removed after Android 4.2.2
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
      Set<String> volds = readVoldsFile();

      List<String> toRemove = new ArrayList<>();
      for (String mount : mounts) {
        if (!volds.contains(mount)) {
          toRemove.add(mount);
        }
      }

      for (String s : toRemove) {
        mounts.remove(s);
      }
    } else {
      Timber.d("Android version: %d, skip reading vold.fstab file", Build.VERSION.SDK_INT);
    }

    Timber.d("mounts list is: %s", mounts);
    return buildMountsList(context, mounts);
  }

  /**
   * Converts a list of mount strings to a list of Storage items
   * @param context the context
   * @param mounts a list of mount points as strings
   * @return a list of Storage items that can be rendered by the ui
   */
  private static List<Storage> buildMountsList(Context context, List<String> mounts) {
    List<Storage> list = new ArrayList<>(mounts.size());

    int externalSdcardsCount = 0;
    if (mounts.size() > 0) {
      // Follow Android SD Cards naming conventions
      if (!Environment.isExternalStorageRemovable() || Environment.isExternalStorageEmulated()) {
        list.add(new Storage(context.getString(R.string.prefs_sdcard_internal),
            Environment.getExternalStorageDirectory().getAbsolutePath()));
      } else {
        externalSdcardsCount = 1;
        list.add(new Storage(context.getString(R.string.prefs_sdcard_external,
            externalSdcardsCount), mounts.get(0)));
      }

      // All other mounts rather than the first mount point are considered as External SD Card
      if (mounts.size() > 1) {
        externalSdcardsCount++;
        for (int i = 1/*skip the first item*/; i < mounts.size(); i++) {
          list.add(new Storage(context.getString(R.string.prefs_sdcard_external,
              externalSdcardsCount++), mounts.get(i)));
        }
      }
    }

    Timber.d("final storage list is: %s", list);
    return list;
  }

  /**
   * Read /proc/mounts. This is a set of hacks for versions below Kitkat.
   * @return list of mounts based on the mounts file.
   */
  private static List<String> readMountsFile() {
    String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();
    List<String> mounts = new ArrayList<>();
    mounts.add(sdcardPath);

    Timber.d("reading mounts file begin");
    try {
      File mountFile = new File("/proc/mounts");
      if (mountFile.exists()) {
        Timber.d("mounts file exists");
        Scanner scanner = new Scanner(mountFile);
        while (scanner.hasNext()) {
          String line = scanner.nextLine();
          Timber.d("line: %s", line);
          if (line.startsWith("/dev/block/vold/")) {
            String[] lineElements = line.split(" ");
            String element = lineElements[1];
            Timber.d("mount element is: %s", element);
            if (!sdcardPath.equals(element)) {
              mounts.add(element);
            }
          } else {
            Timber.d("skipping mount line: %s", line);
          }
        }
      } else {
        Timber.d("mounts file doesn't exist");
      }

      Timber.d("reading mounts file end.. list is: %s", mounts);
    } catch (Exception e) {
      Timber.e(e, "Error reading mounts file");
    }
    return mounts;
  }

  /**
   * Reads volume manager daemon file for auto-mounted storage.
   * Read more about it <a href="http://vold.sourceforge.net/">here</a>.
   *
   * Set usage, to safely avoid duplicates, is intentional.
   * @return Set of mount points from `vold.fstab` configuration file
   */
  private static Set<String> readVoldsFile() {
    Set<String> volds = new HashSet<>();
    volds.add(Environment.getExternalStorageDirectory().getAbsolutePath());

    Timber.d("reading volds file");
    try {
      File voldFile = new File("/system/etc/vold.fstab");
      if (voldFile.exists()) {
        Timber.d("reading volds file begin");
        Scanner scanner = new Scanner(voldFile);
        while (scanner.hasNext()) {
          String line = scanner.nextLine();
          Timber.d("line: %s", line);
          if (line.startsWith("dev_mount")) {
            String[] lineElements = line.split(" ");
            String element = lineElements[2];
            Timber.d("volds element is: %s", element);

            if (element.contains(":")) {
              element = element.substring(0, element.indexOf(":"));
              Timber.d("volds element is: %s", element);
            }

            Timber.d("adding volds element to list: %s", element);
            volds.add(element);
          } else {
            Timber.d("skipping volds line: %s", line);
          }
        }
      } else {
        Timber.d("volds file doesn't exit");
      }
      Timber.d("reading volds file end.. list is: %s", volds);
    } catch (Exception e) {
      Timber.e(e, "Error reading volds file");
    }

    return volds;
  }

  public static class Storage {
    private final String label;
    private final String mountPoint;
    private final boolean requiresPermission;

    private int freeSpace;

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
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
        bytesAvailable = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
      } else {
        //noinspection deprecation
        bytesAvailable = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
      }
      // Convert total bytes to megabytes
      freeSpace = Math.round(bytesAvailable / (1024 * 1024));
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
    public int getFreeSpace() {
      return freeSpace;
    }

    public boolean doesRequirePermission() {
      return requiresPermission;
    }
  }
}
