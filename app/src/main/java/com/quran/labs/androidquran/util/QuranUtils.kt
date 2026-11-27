package com.quran.labs.androidquran.util

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.View
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.TextUtilsCompat
import androidx.preference.PreferenceManager
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import java.io.File
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicReferenceArray

object QuranUtils {
  private const val MAX_CACHED_LOCALIZED_NUMBER = 604

  private class LocalizedNumberCache(
    val locale: Locale,
    private val format: NumberFormat
  ) {
    val values = AtomicReferenceArray<String>(MAX_CACHED_LOCALIZED_NUMBER + 1)

    @Synchronized
    fun formatUncached(number: Int): String = format.format(number)
  }

  @Volatile
  private var localizedNumberCache: LocalizedNumberCache? = null

  /**
   * Returns a boolean indicating if this string contains Arabic.
   * Note that this returns true for non-Arabic languages that share
   * Arabic characters (ex Urdu).
   */
  @JvmStatic
  fun doesStringContainArabic(s: String?): Boolean {
    if (s == null) {
      return false
    }

    val length = s.length
    for (i in 0 until length) {
      val current = s[i].code
      // Skip space
      if (current == 32) {
        continue
      }
      // non-reshaped arabic (U+0621 HAMZA through U+064A YEH)
      if (current in 1569..1610) {
        return true
      }
      // re-shaped arabic
      if (current in 65133..65276) {
        return true
      }
      // if the value is 42, it deserves another chance :p
      // (in reality, 42 is a * which is useful in searching sqlite)
      // also whitelist () and []
      if (current != 42 && current != 40 && current != 41 && current != 91 && current != 93) {
        return false
      }
    }
    return false
  }

  @JvmStatic
  fun isRtl(): Boolean {
    return TextUtilsCompat.getLayoutDirectionFromLocale(getCurrentLocale()) ==
      View.LAYOUT_DIRECTION_RTL
  }

  @JvmStatic
  fun isOnWifiNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
  }

  @JvmStatic
  fun haveInternet(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  @JvmStatic
  fun getCurrentLocale(): Locale {
    val localeList = AppCompatDelegate.getApplicationLocales()
    return if (localeList.isEmpty) {
      Locale.getDefault()
    } else {
      localeList[0] ?: Locale.getDefault()
    }
  }

  @JvmStatic
  fun getLocalizedNumber(number: Int): String {
    val locale = getCurrentLocale()
    var cache = localizedNumberCache
    if (cache == null || cache.locale != locale) {
      cache = synchronized(this) {
        val current = localizedNumberCache
        if (current != null && current.locale == locale) {
          current
        } else {
          LocalizedNumberCache(locale, DecimalFormat.getIntegerInstance(locale)).also {
            localizedNumberCache = it
          }
        }
      }
    }

    if (number in 0..MAX_CACHED_LOCALIZED_NUMBER) {
      cache.values.get(number)?.let { return it }
      val formatted = cache.formatUncached(number)
      cache.values.compareAndSet(number, null, formatted)
      return cache.values.get(number) ?: formatted
    }
    return cache.formatUncached(number)
  }

  @JvmStatic
  @Synchronized
  fun clearLocalizedNumberCache() {
    localizedNumberCache = null
  }

  @JvmStatic
  fun isDualPages(
    context: Context?,
    qsi: QuranScreenInfo?,
    isValidFoldableDeviceAndOpen: Boolean
  ): Boolean {
    if (context != null && qsi != null) {
      val resources = context.resources
      if ((qsi.isDualPageMode &&
          resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ||
        isValidFoldableDeviceAndOpen
      ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(
          Constants.PREF_DUAL_PAGE_ENABLED,
          resources.getBoolean(R.bool.use_tablet_interface_by_default)
        )
      }
    }
    return false
  }

  /**
   * Is this a tablet that has the "dual pages" option set?
   */
  @JvmStatic
  fun isDualPagesInLandscape(context: Context, qsi: QuranScreenInfo): Boolean {
    if (qsi.isDualPageMode) {
      val prefs = PreferenceManager.getDefaultSharedPreferences(context)
      val resources = context.resources
      return prefs.getBoolean(
        Constants.PREF_DUAL_PAGE_ENABLED,
        resources.getBoolean(R.bool.use_tablet_interface_by_default)
      )
    }
    return false
  }

  @JvmStatic
  @WorkerThread
  fun getDebugInfo(context: Context, quranScreenInfo: QuranScreenInfo?): String {
    val builder = StringBuilder()
    builder.append("Android SDK Version: ").append(Build.VERSION.SDK_INT)
    val location = QuranSettings.getInstance(context).appCustomLocation
    builder.append("\nApp Location:").append(location)
    try {
      val file = File(location)
      builder.append("\n App Location Directory ")
        .append(if (file.exists()) "exists" else "doesn't exist")
        .append("\n   Image zip files:")
      for (fileName in file.list().orEmpty()) {
        if (fileName.contains("images_")) {
          val f = File(file, fileName)
          builder.append("\n   file: ").append(fileName).append("\tlength: ").append(f.length())
        }
      }
    } catch (e: Exception) {
      builder.append("Exception trying to list files").append(e)
    }

    if (quranScreenInfo != null) {
      builder.append("\nDisplay: ").append(quranScreenInfo.widthParam)
      if (quranScreenInfo.isDualPageMode) {
        builder.append(", tablet width: ").append(quranScreenInfo.tabletWidthParam)
      }
      builder.append("\n")
    }

    val memClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
      ?.memoryClass ?: 0
    builder.append("memory class: ").append(memClass).append("\n\n")
    return builder.toString()
  }
}
