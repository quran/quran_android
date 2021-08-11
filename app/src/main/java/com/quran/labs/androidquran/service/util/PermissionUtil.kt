package com.quran.labs.androidquran.service.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.util.QuranSettings

object PermissionUtil {

  @JvmStatic
  fun haveWriteExternalStoragePermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
  }

  @JvmStatic
  fun canRequestWriteExternalStoragePermission(activity: Activity): Boolean {
    return !QuranSettings.getInstance(activity).didPresentSdcardPermissionsDialog() ||
        ActivityCompat.shouldShowRequestPermissionRationale(
          activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
  }
}
