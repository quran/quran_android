package com.quran.labs.androidquran.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class QuranUtils {

  private static boolean isArabicFormatter;
  private static NumberFormat numberFormat;
  private static Locale lastLocale;

  public static boolean doesStringContainArabic(String s) {
    if (s == null) {
      return false;
    }

    int length = s.length();
    for (int i = 0; i < length; i++) {
      int current = (int) s.charAt(i);
      // Skip space
      if (current == 32) {
        continue;
      }
      // non-reshaped arabic
      if ((current >= 1570) && (current <= 1610)) {
        return true;
      }
      // re-shaped arabic
      else if ((current >= 65133) && (current <= 65276)) {
        return true;
      }
      // if the value is 42, it deserves another chance :p
      // (in reality, 42 is a * which is useful in searching sqlite)
      else if (current != 42) {
        return false;
      }
    }
    return false;
  }

  public static boolean isRtl() {
    return TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
        == ViewCompat.LAYOUT_DIRECTION_RTL;
  }

  public static boolean isOnWifiNetwork(Context context) {
    ConnectivityManager cm =
        (ConnectivityManager) context.getSystemService(
            Context.CONNECTIVITY_SERVICE);

    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    return activeNetwork != null &&
        activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
  }

  public static boolean haveInternet(Context context) {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
        Context.CONNECTIVITY_SERVICE);
    final NetworkInfo networkInfo = cm == null ? null : cm.getActiveNetworkInfo();
    return networkInfo != null && networkInfo.isConnectedOrConnecting();
  }

  public static String getLocalizedNumber(Context context, int number) {
    Locale locale = Locale.getDefault();
    boolean isArabicNames = QuranSettings.getInstance(context).isArabicNames();
    boolean change = numberFormat == null ||
        !locale.equals(lastLocale) ||
        isArabicNames != isArabicFormatter;

    if (change) {
      numberFormat = isArabicNames ?
          DecimalFormat.getIntegerInstance(new Locale("ar")) :
          DecimalFormat.getIntegerInstance(locale);
      lastLocale = locale;
      isArabicFormatter = isArabicNames;
    }
    return numberFormat.format(number);
  }

  public static boolean isDualPages(Context context, QuranScreenInfo qsi) {
    if (context != null && qsi != null) {
      final Resources resources = context.getResources();
      if (qsi.isTablet(context) &&
          resources.getConfiguration().orientation ==
              Configuration.ORIENTATION_LANDSCAPE) {
        final SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.PREF_TABLET_ENABLED,
            resources.getBoolean(R.bool.use_tablet_interface_by_default));
      }
    }
    return false;
  }

  /**
   * Is this a tablet that has the "dual pages" option set?
   * @param context the context
   * @param qsi the QuranScreenInfo instance
   * @return whether or not this is a tablet with the "dual pages" option set, irrespective of
   * the current orientation of the device.
   */
  public static boolean isDualPagesInLandscape(
      @NonNull Context context, @NonNull QuranScreenInfo qsi) {
    if (qsi.isTablet(context)) {
      final SharedPreferences prefs =
          PreferenceManager.getDefaultSharedPreferences(context);
      final Resources resources = context.getResources();
      return prefs.getBoolean(Constants.PREF_TABLET_ENABLED,
          resources.getBoolean(R.bool.use_tablet_interface_by_default));
    }
    return false;
  }

  @WorkerThread
  public static String getDebugInfo(Context context){
    StringBuilder builder = new StringBuilder();
    builder.append("Android SDK Version: ").append(Build.VERSION.SDK_INT);
    String location = QuranSettings.getInstance(context).getAppCustomLocation();
    builder.append("\nApp Location:").append(location);
    try {
      File file = new File(location);
      builder.append("\n App Location Directory ")
          .append(file.exists() ? "exists" : "doesn't exist")
          .append("\n   Image zip files:");
      String[] list = file.list();
      for (String fileName : list) {
        if (fileName.contains("images_")) {
          File f = new File(fileName);
          builder.append("\n   file: ").append(fileName).append("\tlength: ").append(f.length());
        }
      }
    } catch (Exception e) {
      builder.append("Exception trying to list files")
          .append(e);
    }

    QuranScreenInfo info = QuranScreenInfo.getInstance();
    if (info != null){
      builder.append("\nDisplay: ").append(info.getWidthParam());
      if (info.isTablet(context)){
        builder.append(", tablet width: ").append(info.getWidthParam());
      }
      builder.append("\n");
    }

    int memClass = ((ActivityManager)context
        .getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
    builder.append("memory class: ").append(memClass).append("\n\n");
    return builder.toString();
  }
}
