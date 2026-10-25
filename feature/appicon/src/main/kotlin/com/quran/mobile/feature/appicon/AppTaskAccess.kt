package com.quran.mobile.feature.appicon

import android.app.ActivityManager
import android.content.Context
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

interface AppTaskAccess {
  fun hasOtherAppInTask(): Boolean
  fun removeTasksStartedFrom(launcherAliases: Set<String>)
}

@ContributesBinding(AppScope::class)
@Inject
class ActivityManagerAppTaskAccess(
  @param:ApplicationContext private val context: Context
) : AppTaskAccess {

  override fun hasOtherAppInTask(): Boolean {
    return appTasks().any { (_, info) ->
      val topActivity = info.topActivity
      topActivity != null && topActivity.packageName != context.packageName
    }
  }

  override fun removeTasksStartedFrom(launcherAliases: Set<String>) {
    appTasks()
      .filter { (_, info) -> info.baseIntent.component?.className in launcherAliases }
      .forEach { (task, _) ->
        try {
          task.finishAndRemoveTask()
        } catch (_: IllegalArgumentException) {
          // already removed
        }
      }
  }

  private fun appTasks(): List<Pair<ActivityManager.AppTask, ActivityManager.RecentTaskInfo>> {
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
    return activityManager.appTasks.mapNotNull { task ->
      try {
        task.taskInfo?.let { task to it }
      } catch (_: IllegalArgumentException) {
        // removed while listing
        null
      }
    }
  }
}
