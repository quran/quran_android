package com.quran.labs.androidquran.service.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
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

  @JvmStatic
  fun havePostNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  @JvmStatic
  fun canRequestPostNotificationPermission(activity: Activity): Boolean {
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
  }

  @JvmStatic
  fun buildPostPermissionDialog(
    context: Context,
    onAccept: (() -> Unit),
    onDecline: (() -> Unit)
  ): AlertDialog {
    val builder = AlertDialog.Builder(context)
    builder.setMessage(R.string.post_notification_permission)
      .setPositiveButton(R.string.downloadPrompt_ok) { dialog, _ ->
        dialog.dismiss()
        onAccept()
      }
      .setNegativeButton(R.string.downloadPrompt_no) { dialog, _ ->
        dialog.dismiss()
        onDecline()
      }
    return builder.create()
  }
}
