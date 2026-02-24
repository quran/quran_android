package com.quran.labs.androidquran.util

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

class FocusModeManager(private val context: Context) {

  private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private var previousInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL
  private var focusModeActivatedByApp: Boolean = false

  fun isPermissionGranted(): Boolean = notificationManager.isNotificationPolicyAccessGranted

  fun requestPermission(activity: Activity) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    activity.startActivity(intent)
  }

  fun saveCurrentDndState() {
    previousInterruptionFilter = notificationManager.currentInterruptionFilter
  }

  fun enableFocusMode(): Boolean {
    if (!isPermissionGranted()) return false
    if (focusModeActivatedByApp) return true

    previousInterruptionFilter = notificationManager.currentInterruptionFilter
    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
    focusModeActivatedByApp = true
    return true
  }

  fun restorePreviousDndState() {
    if (!isPermissionGranted()) return
    if (!focusModeActivatedByApp) return

    notificationManager.setInterruptionFilter(previousInterruptionFilter)
    focusModeActivatedByApp = false
  }

  fun isFocusModeActive(): Boolean {
    return focusModeActivatedByApp
  }
}
