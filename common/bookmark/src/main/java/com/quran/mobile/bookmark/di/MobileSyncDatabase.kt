package com.quran.mobile.bookmark.di

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.shared.persistence.DriverFactory
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.quran.shared.persistence.MobileSyncDatabase as SharedMobileSyncDatabase

@SingleIn(AppScope::class)
class MobileSyncDatabase @Inject constructor(@ApplicationContext context: Context) {
  internal val database: SharedMobileSyncDatabase =
    SharedMobileSyncDatabase(DriverFactory(context).makeDriver())
}
