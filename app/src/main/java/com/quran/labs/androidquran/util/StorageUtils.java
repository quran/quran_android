package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;


/**
 * Based on:
 * - http://sapienmobile.com/?p=204
 * - http://stackoverflow.com/a/15612964
 * - http://renzhi.ca/2012/02/03/how-to-list-all-sd-cards-on-android/
 */
public class StorageUtils {

  private static final String TAG = StorageUtils.class.getSimpleName();

  /**
   * @return A List of all storage locations available
   */
  public static List<Storage> getAllStorageLocations(Context context) {
    Collection<String> mounts = readMountsFile();

    // As per http://source.android.com/devices/tech/storage/config.html
    // device-specific vold.fstab file is removed after Android 4.2.2
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
        Collection<String> vold = readVoldsFile();

        List<String> toRemove = new ArrayList<String>();
        for (Iterator<String> iter = mounts.iterator(); iter.hasNext(); ){
            String mount = iter.next();
            if (!vold.contains(mount)){
                toRemove.add(mount);
            }
        }

        for (String s : toRemove){
            mounts.remove(s);
        }
    } else {
        Log.d(TAG, "Android version: " + Build.VERSION.CODENAME + " skip reading vold.fstab file");
    }

    Log.d(TAG, "mounts list is: " + mounts);
    return buildMountsList(context, mounts);
  }

  private static List<Storage> buildMountsList(Context context,
                                               Collection<String> mounts){
    List<Storage> list = new ArrayList<Storage>(mounts.size());

    int externalSdcardsCount = 0;
    if (mounts.size() > 0) {
      String firstItem = mounts.iterator().next();

      // Follow Android SDCards naming conventions
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
        list.add(new Storage(
            context.getString(R.string.prefs_sdcard_auto),
            firstItem));
      }
      else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
        if (isExternalStorageRemovableGingerbread()) {
          list.add(new Storage(context.getString(
              R.string.prefs_sdcard_external) +
              " 1", firstItem));
          externalSdcardsCount = 1;
        }
        else {
          list.add(new Storage(context.getString(
              R.string.prefs_sdcard_internal), firstItem));
        }
      }
      else {
        if (!isExternalStorageRemovableGingerbread() ||
            isExternalStorageEmulatedHoneycomb()) {
          list.add(new Storage(context.getString(
              R.string.prefs_sdcard_internal), firstItem));
        }
        else {
          list.add(new Storage(context.getString(
              R.string.prefs_sdcard_external) +
              " 1", firstItem));
          externalSdcardsCount = 1;
        }
      }

      // All other mounts rather than the first mount point
      // are considered as External SD Card
      if (mounts.size() > 1) {
        Iterator<String> iter = mounts.iterator();

        // skip the first one
        iter.next();

        int i = 1;
        while (iter.hasNext()){
          String mount = iter.next();
          list.add(new Storage(context.getString(
              R.string.prefs_sdcard_external)
              + " " + (i++ + externalSdcardsCount),
              mount));
        }
      }
    }

    Log.d(TAG, "final storage list is: " + list);
    return list;
  }

  @TargetApi(Build.VERSION_CODES.GINGERBREAD)
  private static boolean isExternalStorageRemovableGingerbread() {
    return Environment.isExternalStorageRemovable();
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private static boolean isExternalStorageEmulatedHoneycomb() {
    return Environment.isExternalStorageEmulated();
  }

  private static Collection<String> readMountsFile() {
    String sdcardPath = Environment
        .getExternalStorageDirectory().getAbsolutePath();
    List<String> mounts = new ArrayList<String>();
    mounts.add(sdcardPath);

    Log.d(TAG, "reading mounts file begin");
    try {
      File mountFile = new File("/proc/mounts");
      if (mountFile.exists()) {
        Log.d(TAG, "mounts file exists");
        Scanner scanner = new Scanner(mountFile);
        while (scanner.hasNext()) {
          String line = scanner.nextLine();
          Log.d(TAG, "line: " + line);
          if (line.startsWith("/dev/block/vold/")) {
            String[] lineElements = line.split(" ");
            String element = lineElements[1];
            Log.d(TAG, "mount element is: " + element);
            if (!sdcardPath.equals(element)){
              mounts.add(element);
            }
          } else {
            Log.d(TAG, "skipping mount line: " + line);
          }
        }
      } else {
        Log.d(TAG, "mounts file doesn't exist");
      }

      Log.d(TAG, "reading mounts file end.. list is: " + mounts);
    } catch (Exception e) {
      Log.e(TAG, "Error reading mounts file", e);
    }
    return mounts;
  }

  /**
   * reads volume manager daemon file for auto-mounted storage
   * read more about it here: http://vold.sourceforge.net/
   *
   * @return
   */
  private static Set<String> readVoldsFile() {
    Set<String> volds = new HashSet<String>();
    volds.add(Environment.getExternalStorageDirectory().getAbsolutePath());

    Log.d(TAG, "reading volds file");
    try {
      File voldFile = new File("/system/etc/vold.fstab");
      if (voldFile.exists()) {
        Log.d(TAG, "reading volds file begin");
        Scanner scanner = new Scanner(voldFile);
        while (scanner.hasNext()) {
          String line = scanner.nextLine();
          Log.d(TAG, "line: " + line);
          if (line.startsWith("dev_mount")) {
            String[] lineElements = line.split(" ");
            String element = lineElements[2];
            Log.d(TAG, "volds element is: " + element);

            if (element.contains(":")) {
              element = element.substring(
                  0, element.indexOf(":"));
              Log.d(TAG, "volds element is: " + element);
            }

            Log.d(TAG, "adding volds element to list: " + element);
            volds.add(element);
          } else {
            Log.d(TAG, "skipping volds line: " + line);
          }
        }
      }
      else {
        Log.d(TAG, "volds file doesn't exit");
      }
      Log.d(TAG, "reading volds file end.. list is: " + volds);
    }
    catch (Exception e) {
      Log.e(TAG, "Error reading vold file", e);
    }

    return volds;
  }

  public static class Storage {
    private String label;
    private String mountPoint;
    private int freeSpace;
    private int totalSpace;

    public Storage(String label, String mountPoint) {
      this.label = label;
      this.mountPoint = mountPoint;
      computeSpace();
    }

    private void computeSpace() {
      StatFs stat = new StatFs(mountPoint);
      long totalBytes = (long) stat.getBlockCount() *
          (long) stat.getBlockSize();
      long bytesAvailable = (long) stat.getAvailableBlocks() *
          (long) stat.getBlockSize();
      // Convert total bytes to megabytes
      totalSpace = Math.round(totalBytes / (1024 * 1024));
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

    /**
     * @return total size in Megabytes
     */
    public int getTotalSpace() {
      return totalSpace;
    }
  }
}
