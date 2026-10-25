package com.quran.mobile.feature.appicon.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.quran.analytics.AnalyticsProvider
import com.quran.mobile.feature.appicon.AppIconManager
import com.quran.mobile.feature.appicon.model.AppIconOption
import com.quran.mobile.feature.appicon.state.AppIconEvent
import com.quran.mobile.feature.appicon.state.AppIconState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
class AppIconPresenter(
  private val appIconManager: AppIconManager,
  private val analyticsProvider: AnalyticsProvider
) {

  @Composable
  fun present(): AppIconState {
    val selectedOption by appIconManager.currentOption.collectAsState()
    LaunchedEffect(Unit) { appIconManager.refresh() }
    val scope = rememberCoroutineScope()

    return AppIconState(
      sections = appIconManager.catalog.sections,
      selectedOption = selectedOption,
      eventSink = { event ->
        when (event) {
          is AppIconEvent.Select -> {
            val previousOption = selectedOption
            if (event.option != previousOption) {
              scope.launch { select(from = previousOption, to = event.option) }
            }
          }
        }
      }
    )
  }

  private suspend fun select(from: AppIconOption, to: AppIconOption) {
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
