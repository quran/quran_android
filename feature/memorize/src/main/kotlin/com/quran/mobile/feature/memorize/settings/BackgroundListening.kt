package com.quran.mobile.feature.memorize.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.quran.data.di.AppScope
import com.quran.mobile.feature.memorize.R
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Helps the user let memorization keep listening with the screen off (salah). Phones stop
 * background work to save battery; these are the switches that prevent that, opened directly
 * where possible.
 */
@SingleIn(AppScope::class)
class BackgroundListening @Inject constructor() {

  fun isUnrestricted(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return true
    return power.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun showSetup(context: Context) {
    val steps = buildList {
      add(Step(R.string.memorize_background_battery) { requestUnrestrictedBattery(it) })
      if (isXiaomi()) {
        add(Step(R.string.memorize_background_autostart) { openXiaomiAutostart(it) })
        add(Step(R.string.memorize_background_no_restrictions) { openXiaomiBatterySaver(it) })
      }
      add(Step(R.string.memorize_background_app_settings) { openAppDetails(it) })
    }
    val status = context.getString(
      if (isUnrestricted(context)) R.string.memorize_background_status_ok else R.string.memorize_background_status_restricted
    )
    AlertDialog.Builder(context)
      .setTitle(context.getString(R.string.memorize_background_title) + "\n" + status)
      .setItems(steps.map { context.getString(it.title) }.toTypedArray()) { _, which -> steps[which].action(context) }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private class Step(val title: Int, val action: (Context) -> Unit)

  @SuppressLint("BatteryLife") // the user asked for this from settings, for listening during salah
  private fun requestUnrestrictedBattery(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    if (!launch(context, direct)) {
      launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) || openAppDetails(context)
    }
  }

  private fun openXiaomiAutostart(context: Context) {
    val intent = Intent().setComponent(
      ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    )
    launch(context, intent) || openAppDetails(context)
  }

  private fun openXiaomiBatterySaver(context: Context) {
    val intent = Intent()
      .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
      .putExtra("package_name", context.packageName)
      .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
    launch(context, intent) || openAppDetails(context)
  }

  private fun openAppDetails(context: Context): Boolean =
    launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))

  private fun launch(context: Context, intent: Intent): Boolean = try {
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }

  private fun isXiaomi(): Boolean {
    val maker = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
    return "xiaomi" in maker || "redmi" in maker || "poco" in maker
  }
}
