package com.quran.data.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

@SingleIn(AppScope::class)
class AppCoroutineScope @Inject constructor() : CoroutineScope {
  override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO
}
