package com.quran.labs.androidquran.ui.helpers

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.ui.whatsnew.AnnouncementActivity
import com.quran.labs.androidquran.util.QuranSettings

/**
 * Arms [pendingDirectOpen] once, when the process starts.
 * The reader consumes it from its own creation. Coming back to an activity
 * that is still alive does not arm it again. Any other settled screen clears
 * it, so choosing a surah or juz from the index does not show the prompt.
 */
internal object FatihaPromptSession {
  var pendingDirectOpen: Boolean = false

  private var startedActivities: Int = 0

  fun install(application: Application) {
    pendingDirectOpen = true
    application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
      override fun onActivityStarted(activity: Activity) {
        startedActivities++
      }

      override fun onActivityResumed(activity: Activity) {
        if (!pendingDirectOpen || activity.keepsDirectReadingPrompt()) return
        pendingDirectOpen = false
      }

      override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities == 0 && !activity.isChangingConfigurations) {
          QuranSettings.getInstance(activity).resumeReadingOnLaunch = activity is PagerActivity
        }
      }

      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    })
  }

  private fun Activity.keepsDirectReadingPrompt(): Boolean {
    return when (this) {
      is PagerActivity -> true
      is QuranDataActivity -> true
      is AnnouncementActivity -> true
      is QuranActivity -> isOpeningDirectlyIntoReading
      else -> false
    }
  }
}
