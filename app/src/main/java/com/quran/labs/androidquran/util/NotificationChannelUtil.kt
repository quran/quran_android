package com.quran.labs.androidquran.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.quran.labs.androidquran.data.Constants

object NotificationChannelUtil {

  fun setupNotificationChannel(notificationManager: NotificationManager, channelId: String, channelName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
      if (channelId == Constants.AUDIO_CHANNEL) {
        notificationChannel.setBypassDnd(true)
      }
      if (notificationManager.getNotificationChannel(channelId) == null) {
        notificationManager.createNotificationChannel(notificationChannel)
      }
    }
  }
}
