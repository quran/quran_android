package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.util.QuranSettings;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionUtil {

  public static boolean haveWriteExternalStoragePermission(Context context) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED;
  }

  public static boolean canRequestWriteExternalStoragePermission(Activity activity) {
    return !QuranSettings.getInstance(activity).didPresentSdcardPermissionsDialog() ||
        ActivityCompat.shouldShowRequestPermissionRationale(activity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
  }
}
