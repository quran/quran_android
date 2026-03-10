package com.quran.mobile.bookmark.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.shared.auth.service.AuthService
import com.quran.shared.persistence.DriverFactory
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.recentpage.repository.RecentPagesRepository
import com.quran.shared.pipeline.SyncService
import com.quran.shared.pipeline.di.SharedDependencyGraph
import com.quran.shared.syncengine.SynchronizationEnvironment
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class MobileSyncSession @Inject constructor(
  @ApplicationContext context: Context
) {
  private val appContext = context

  private val graph by lazy {
    SharedDependencyGraph.init(
      driverFactory = DriverFactory(appContext),
      environment = SynchronizationEnvironment(endPointURL = endpoint(appContext))
    )
  }

  val authService: AuthService
    get() = graph.authService

  val bookmarksRepository: BookmarksRepository
    get() = graph.bookmarksRepository

  val recentPagesRepository: RecentPagesRepository
    get() = graph.recentPagesRepository

  val syncService: SyncService
    get() = graph.syncService

  private fun endpoint(context: Context): String {
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return if (isDebuggable) {
      "https://apis-prelive.quran.foundation/auth"
    } else {
      "https://apis.quran.foundation/auth"
    }
  }
}
