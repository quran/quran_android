package com.quran.labs.androidquran.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R

import java.io.File
import java.util.ArrayList

/**
 * Based on:
 * - http://sapienmobile.com/?p=204
 * - http://stackoverflow.com/a/15612964
 * - http://renzhi.ca/2012/02/03/how-to-list-all-sd-cards-on-android/
 */
object StorageUtils {

  /**
   * @return A List of all storage locations available
   */
  @JvmStatic
  fun getAllStorageLocations(context: Context): List<Storage> {

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
    val result: MutableList<Storage> = ArrayList()
    val limit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 1 else 2
    val mountPoints: Array<File> = ContextCompat.getExternalFilesDirs(context, null)
    if (mountPoints.size >= limit) {

      // internal files dir
      result.add(
        Storage(
          context.getString(R.string.prefs_sdcard_internal),
          context.filesDir.absolutePath,
        )
      )

      // all of these are "external" files dir or related
      var number = 1
      // this first one is not safe to write on starting from Android 11 - /sdcard.
      if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        // don't show the /sdcard option for people on Android 11
        result.add(
          Storage(
            context.getString(R.string.prefs_sdcard_external, number++),
            Environment.getExternalStorageDirectory().absolutePath,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
          )
        )
      }

      // add all the remaining places
      for (mountPoint in mountPoints) {
        result.add(
          Storage(
            context.getString(R.string.prefs_sdcard_external, number++),
            mountPoint.absolutePath
          )
        )
      }
    }
    return result
  }

  class Storage internal constructor(
    val label: String,
    val mountPoint: String,
    private val requiresPermission: Boolean = false
  ) {
    private var freeSpace: Long = 0

    init {
      computeSpace()
    }

    private fun computeSpace() {
      val stat = StatFs(mountPoint)
      val bytesAvailable: Long = stat.availableBlocksLong * stat.blockSizeLong
      // Convert total bytes to megabytes
      freeSpace = bytesAvailable / (1024 * 1024)
    }

    /**
     * @return available free size in Megabytes
     */
    fun getFreeSpace(): Long = freeSpace

    fun doesRequirePermission(): Boolean = requiresPermission
  }
}
