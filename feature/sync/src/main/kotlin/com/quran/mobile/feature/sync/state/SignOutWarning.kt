package com.quran.mobile.feature.sync.state

import com.quran.shared.pipeline.UnsyncedItemCounts

internal sealed interface SignOutWarning {
  data object Unknown : SignOutWarning

  data object Synced : SignOutWarning

  data class Unsynced(val pending: Int, val toFix: Int, val waiting: Int) : SignOutWarning
}

internal fun signOutWarning(counts: UnsyncedItemCounts?, blockingItems: Int): SignOutWarning {
  return if (counts == null) {
     SignOutWarning.Unknown
  } else if (counts.pending == 0 && counts.held == 0) {
     SignOutWarning.Synced
  } else {
     val toFix = if (blockingItems > 0) minOf(blockingItems, counts.held) else counts.held
     SignOutWarning.Unsynced(counts.pending, toFix, counts.held - toFix)
  }
}
