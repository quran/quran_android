package com.quran.mobile.bookmark.sync

class FakeLocalDataChangeNotifier : LocalDataChangeNotifier {
  var updateCount = 0
    private set
  var throwOnUpdate = false

  override fun localDataUpdated() {
    updateCount++
    if (throwOnUpdate) {
      error("Local data update notification failed.")
    }
  }

  fun reset() {
    updateCount = 0
  }
}
