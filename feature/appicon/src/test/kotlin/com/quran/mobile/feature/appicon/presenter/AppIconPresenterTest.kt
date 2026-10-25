package com.quran.mobile.feature.appicon.presenter

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.analytics.AnalyticsProvider
import com.quran.mobile.feature.appicon.AppIconManager
import com.quran.mobile.feature.appicon.fakes.FakeAppIconPreference
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
  private val aliasAccess = FakeLauncherAliasAccess()
  private val preference = FakeAppIconPreference()

  private fun present(manager: AppIconManager = TestAppIcons.manager(aliasAccess, TestAppIcons.savedAppIcon(preference))) =
    moleculeFlow(RecompositionMode.Immediate) { AppIconPresenter(manager, analyticsProvider).present() }

  private suspend fun ReceiveTurbine<AppIconState>.awaitSelected(option: AppIconOption): AppIconState {
    var state = awaitItem()
    while (state.selectedOption != option) {
      state = awaitItem()
    }
    return state
  }

  @Test
  fun `shows the current icon`() = runTest {
    present().test {
      val state = awaitItem()
      assertThat(state.selectedOption).isEqualTo(TestAppIcons.default)
      assertThat(state.sections).isEqualTo(TestAppIcons.catalog.sections)
    }
  }

  @Test
  fun `selecting saves the choice for the launcher to show later`() = runTest {
    present().test {
      awaitItem().eventSink(AppIconEvent.Select(TestAppIcons.green))

      awaitSelected(TestAppIcons.green)
      assertThat(preference.hasPendingIcon).isTrue()
      assertThat(aliasAccess.appliedStates).isEmpty()
      assertThat(loggedEvents).containsExactly(
        "app_icon_change" to mapOf("from" to "default", "to" to "green")
      )
    }
  }

  @Test
  fun `selecting the current icon does nothing`() = runTest {
    present().test {
      awaitItem().eventSink(AppIconEvent.Select(TestAppIcons.default))

      expectNoEvents()
      assertThat(preference.hasPendingIcon).isFalse()
      assertThat(loggedEvents).isEmpty()
    }
  }

  @Test
  fun `opening the picker corrects a stale saved icon`() = runTest {
    val savedAppIcon = TestAppIcons.savedAppIcon(FakeAppIconPreference(savedIconId = "blue"))

    present(TestAppIcons.manager(savedAppIcon = savedAppIcon)).test {
      assertThat(awaitItem().selectedOption).isEqualTo(TestAppIcons.blue)
      awaitSelected(TestAppIcons.default)
    }
  }

  @Test
  fun `opening the picker keeps a pending choice`() = runTest {
    val savedAppIcon = TestAppIcons.savedAppIcon(
      FakeAppIconPreference(savedIconId = "blue", hasPendingIcon = true)
    )

    present(TestAppIcons.manager(savedAppIcon = savedAppIcon)).test {
      assertThat(awaitItem().selectedOption).isEqualTo(TestAppIcons.blue)
      expectNoEvents()
    }
  }
}
