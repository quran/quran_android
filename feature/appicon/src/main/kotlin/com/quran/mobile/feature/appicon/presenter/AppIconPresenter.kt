package com.quran.mobile.feature.appicon.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.quran.analytics.AnalyticsProvider
import com.quran.mobile.feature.appicon.AppIconManager
import com.quran.mobile.feature.appicon.model.AppIconOption
import com.quran.mobile.feature.appicon.state.AppIconEvent
import com.quran.mobile.feature.appicon.state.AppIconState
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

@AssistedInject
class AppIconPresenter(
  @Assisted private val taskLauncherAlias: String?,
  private val appIconManager: AppIconManager,
  private val analyticsProvider: AnalyticsProvider
) {

  @AssistedFactory
  fun interface Factory {
    fun create(taskLauncherAlias: String?): AppIconPresenter
  }

  @Composable
  fun present(): AppIconState {
    val selectedOption by appIconManager.currentOption.collectAsState()
    var pendingOption by remember { mutableStateOf<AppIconOption?>(null) }
    var isChanging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun change(option: AppIconOption) {
      isChanging = true
      val previousOption = selectedOption
      scope.launch {
        change(from = previousOption, to = option)
        isChanging = false
      }
    }

    return AppIconState(
      sections = appIconManager.catalog.sections,
      selectedOption = selectedOption,
      pendingOption = pendingOption,
      eventSink = { event ->
        when (event) {
          is AppIconEvent.Select -> {
            val option = event.option
            if (option != selectedOption && !isChanging) {
              if (appIconManager.closesTask(taskLauncherAlias, option)) {
                pendingOption = option
              } else {
                change(option)
              }
            }
          }
          AppIconEvent.CancelChange -> pendingOption = null
          AppIconEvent.ConfirmChange -> {
            val option = pendingOption
            if (option != null && !isChanging) {
              pendingOption = null
              change(option)
            }
          }
        }
      }
    )
  }

  private suspend fun change(from: AppIconOption, to: AppIconOption) {
    try {
      appIconManager.select(to)
      analyticsProvider.logEvent("app_icon_change", mapOf("from" to from.id, "to" to to.id))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.e(e, "Unable to change the app icon from ${from.id} to ${to.id}")
    }
  }
}
