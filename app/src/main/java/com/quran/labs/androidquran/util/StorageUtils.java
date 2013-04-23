package com.quran.labs.androidquran.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


/**
 * Based on:
 * - http://sapienmobile.com/?p=204
 * - http://stackoverflow.com/a/15612964
 * - http://renzhi.ca/2012/02/03/how-to-list-all-sd-cards-on-android/
 */
public class StorageUtils {

    private static final String TAG = "StorageUtils";

    /**
     * @return A List of all storage locations available
     */
    public static List<Storage> getAllStorageLocations(Context context){
        List<String> mMounts = readMountsFile();
        List<String> mVold = readVoldsFile();

        // make sure that the each entry in mounts exists in volds list
        for (int i = 0; i < mMounts.size(); i++) {
            String mount = mMounts.get(i);
            if (!mVold.contains(mount))
                mMounts.remove(i--);
        }
        mVold.clear();

        return buildMountsList(context, mMounts);
    }

    private static List<Storage> buildMountsList(Context context,
                                                 List<String> mounts){
        List<Storage> list = new ArrayList<Storage>(mounts.size());

        int externalSdcardsCount = 0;
        if (mounts.size() > 0) {

            // Follow Android SDCards naming conventions
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD){
                list.add(new Storage(
                        context.getString(R.string.prefs_sdcard_auto),
                        mounts.get(0)));
            }
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB){
                if (Environment.isExternalStorageRemovable()){
                    list.add(new Storage(context.getString(
                            R.string.prefs_sdcard_external) +
                            " 1", mounts.get(0)));
                    externalSdcardsCount = 1;
                }
                else {
                    list.add(new Storage(context.getString(
                            R.string.prefs_sdcard_internal), mounts.get(0)));
                }
            } else {
                if (!Environment.isExternalStorageRemovable() ||
                        Environment.isExternalStorageEmulated()) {
                    list.add(new Storage(context.getString(
                            R.string.prefs_sdcard_internal), mounts.get(0)));
                } else {
                    list.add(new Storage(context.getString(
                            R.string.prefs_sdcard_external) +
                            " 1", mounts.get(0)));
                    externalSdcardsCount = 1;
                }
            }

            // All other mounts rather than the first mount point, are considered as External SD Card
            if (mounts.size() > 1) {
                for (int i = 1; i < mounts.size(); i++){
                    list.add(new Storage(context.getString(
                            R.string.prefs_sdcard_external)
                            + " " + (i + externalSdcardsCount),
                            mounts.get(i)));
                }
            }
        }

        return list;
    }

    private static List<String> readMountsFile() {
        List<String> mMounts = new ArrayList<String>();

        try {
            File mountFile = new File("/proc/mounts");
            if(mountFile.exists()){
                Scanner scanner = new Scanner(mountFile);
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("/dev/block/vold/")) {
                        String[] lineElements = line.split(" ");
                        String element = lineElements[1];

                        // don't add the default mount path
                        // it's already in the list.
                        if (!element.equals("/mnt/sdcard"))
                            mMounts.add(element);
                    }
                }
            }


        } catch (Exception e) {
            Log.e(TAG, "Error reading mounts file", e);
        }
        return mMounts;
    }

    /**
     * reads volume manager daemon file for auto-mounted storage
     * read more about it here: http://vold.sourceforge.net/
     * @return
     */
    private static List<String> readVoldsFile() {
        List<String> mVold = new ArrayList<String>();

        try {
            File voldFile = new File("/system/etc/vold.fstab");
            if(voldFile.exists()){
                Scanner scanner = new Scanner(voldFile);
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("dev_mount")) {
                        String[] lineElements = line.split(" ");
                        String element = lineElements[2];

                        if (element.contains(":"))
                            element = element.substring(
                                    0, element.indexOf(":"));
                        if (!element.equals("/mnt/sdcard"))
                            mVold.add(element);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading vold file", e);
        }

        return mVold;
    }

    public static class Storage {
        private String label;
        private String mountPoint;
        private int freeSpace;
        private int totalSpace;

        private Storage(String label, String mountPoint) {
            this.label = label;
            this.mountPoint = mountPoint;
            computeSpace();
        }

        private void computeSpace() {
            StatFs stat = new StatFs(mountPoint);
            long totalBytes = (long) stat.getBlockCount() *
                    (long)stat.getBlockSize();
            long bytesAvailable = (long) stat.getAvailableBlocks() *
                    (long)stat.getBlockSize();
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
