package com.quran.mobile.feature.appicon.presenter

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.analytics.AnalyticsProvider
import com.quran.mobile.feature.appicon.AppIconManager
import com.quran.mobile.feature.appicon.fakes.FakeLauncherAliasAccess
import com.quran.mobile.feature.appicon.fakes.TestAppIcons
import com.quran.mobile.feature.appicon.model.AppIconOption
import com.quran.mobile.feature.appicon.state.AppIconEvent
import com.quran.mobile.feature.appicon.state.AppIconState
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppIconPresenterTest {
  private val loggedEvents = mutableListOf<Pair<String, Map<String, Any>>>()
  private val analyticsProvider = AnalyticsProvider { name, params -> loggedEvents += name to params }
  private val manager = AppIconManager(TestAppIcons.catalog, FakeLauncherAliasAccess())

  private fun presenter(taskLauncherAlias: String?) =
    AppIconPresenter(taskLauncherAlias, manager, analyticsProvider)

  private fun present(taskLauncherAlias: String?) =
    moleculeFlow(RecompositionMode.Immediate) { presenter(taskLauncherAlias).present() }

  private suspend fun ReceiveTurbine<AppIconState>.awaitSelected(option: AppIconOption): AppIconState {
    var state = awaitItem()
    while (state.selectedOption != option) {
      state = awaitItem()
    }
    return state
  }

  @Test
  fun `shows the current icon`() = runTest {
    present(taskLauncherAlias = null).test {
      val state = awaitItem()
      assertThat(state.selectedOption).isEqualTo(TestAppIcons.default)
      assertThat(state.sections).isEqualTo(TestAppIcons.catalog.sections)
      assertThat(state.pendingOption).isNull()
    }
  }

  @Test
  fun `changes right away when the app stays open`() = runTest {
    present(taskLauncherAlias = TestAppIcons.blue.launcherAlias).test {
      awaitItem().eventSink(AppIconEvent.Select(TestAppIcons.green))

      val state = awaitSelected(TestAppIcons.green)
      assertThat(state.pendingOption).isNull()
      assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.green)
      assertThat(loggedEvents).containsExactly(
        "app_icon_change" to mapOf("from" to "default", "to" to "green")
      )
    }
  }

  @Test
  fun `asks before a change that closes the app`() = runTest {
    present(taskLauncherAlias = TestAppIcons.default.launcherAlias).test {
      awaitItem().eventSink(AppIconEvent.Select(TestAppIcons.blue))

      val pendingState = awaitItem()
      assertThat(pendingState.pendingOption).isEqualTo(TestAppIcons.blue)
      assertThat(pendingState.selectedOption).isEqualTo(TestAppIcons.default)

      pendingState.eventSink(AppIconEvent.ConfirmChange)
      val state = awaitSelected(TestAppIcons.blue)
      assertThat(state.pendingOption).isNull()
      assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.blue)
    }
  }

  @Test
  fun `cancelling keeps the current icon`() = runTest {
    present(taskLauncherAlias = TestAppIcons.default.launcherAlias).test {
      awaitItem().eventSink(AppIconEvent.Select(TestAppIcons.blue))
      awaitItem().eventSink(AppIconEvent.CancelChange)

      val state = awaitItem()
      assertThat(state.pendingOption).isNull()
      assertThat(state.selectedOption).isEqualTo(TestAppIcons.default)
      assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.default)
      assertThat(loggedEvents).isEmpty()
    }
  }

  @Test
  fun `selecting the current icon does nothing`() = runTest {
    present(taskLauncherAlias = TestAppIcons.default.launcherAlias).test {
      awaitItem().eventSink(AppIconEvent.Select(TestAppIcons.default))

      expectNoEvents()
      assertThat(loggedEvents).isEmpty()
    }
  }
}
