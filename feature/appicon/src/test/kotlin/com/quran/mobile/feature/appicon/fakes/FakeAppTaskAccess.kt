package com.quran.mobile.feature.appicon.fakes

import com.quran.mobile.feature.appicon.AppTaskAccess

class FakeAppTaskAccess(
  var hasOtherApp: Boolean = false,
  private val onRemove: () -> Unit = {}
) : AppTaskAccess {
  val removedAliases = mutableListOf<Set<String>>()

  override fun hasOtherAppInTask(): Boolean = hasOtherApp

  override fun removeTasksStartedFrom(launcherAliases: Set<String>) {
    onRemove()
    removedAliases += launcherAliases
  }
}
