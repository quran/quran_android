package com.quran.labs.androidquran.extra.feature.linebyline.presenter

import com.quran.data.dao.Settings
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.extra.feature.linebyline.model.DisplaySettings
import com.quran.labs.androidquran.extra.feature.linebyline.model.EmptySettings
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@SingleIn(AppScope::class)
class QuranLineByLineSettingsPresenter private constructor(
  private val settings: Settings,
  private val scope: CoroutineScope
) {
  @Inject constructor(settings: Settings) : this(
    settings, CoroutineScope(SupervisorJob() + Dispatchers.IO)
  )

  companion object {
    internal fun forTest(settings: Settings, scope: CoroutineScope) =
      QuranLineByLineSettingsPresenter(settings, scope)
  }

  private val internalDisplaySettingsFlow = settings.preferencesFlow()
    .onStart { emit("") }
    .map {
      DisplaySettings(
        settings.isNightMode(),
        settings.pageThemeTextColor(),
        settings.pageThemeBackgroundColor(),
        settings.shouldShowHeaderFooter(),
        settings.showSidelines(),
        settings.showLineDividers()
      )
    }
    .stateIn(scope, SharingStarted.Eagerly, EmptySettings)

  val displaySettingsFlow: Flow<DisplaySettings> = internalDisplaySettingsFlow

  fun latestDisplaySettings() = internalDisplaySettingsFlow.value
}
