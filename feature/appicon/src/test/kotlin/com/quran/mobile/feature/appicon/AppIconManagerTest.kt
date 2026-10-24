package com.quran.mobile.feature.appicon

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.appicon.LauncherAliasAccess.AliasState
import com.quran.mobile.feature.appicon.fakes.FakeAppIconPreference
import com.quran.mobile.feature.appicon.fakes.FakeAppTaskAccess
import com.quran.mobile.feature.appicon.fakes.FakeLauncherAliasAccess
import com.quran.mobile.feature.appicon.fakes.TestAppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import java.util.concurrent.CountDownLatch

class AppIconManagerTest {

  @Test
  fun `creating the manager doesn't read the launcher`() {
    val aliasAccess = FakeLauncherAliasAccess()
    val savedAppIcon = TestAppIcons.savedAppIcon(FakeAppIconPreference(savedIconId = "blue"))

    val manager = TestAppIcons.manager(aliasAccess, savedAppIcon)

    assertThat(aliasAccess.reads).isEqualTo(0)
    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.blue)
  }

  @Test
  fun `unavailable without alternate icon support`() = runTest {
    val manager = TestAppIcons.manager(FakeLauncherAliasAccess(supportsAlternateIcons = false))

    assertThat(manager.isAvailable()).isFalse()
    manager.refresh()
    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.default)
  }

  @Test
  fun `unavailable when an alias is missing from the manifest`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess(declaredAliases = setOf(TestAppIcons.default.launcherAlias))
    aliasAccess.states[TestAppIcons.blue.launcherAlias] = AliasState.ENABLED
    val manager = TestAppIcons.manager(aliasAccess)

    assertThat(manager.isAvailable()).isFalse()
    manager.refresh()
    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.default)
  }

  @Test
  fun `checking availability saves it for the next time`() = runTest {
    val available = TestAppIcons.manager(savedAppIcon = TestAppIcons.savedAppIcon())
    assertThat(available.lastKnownAvailability).isNull()
    available.isAvailable()
    assertThat(available.lastKnownAvailability).isTrue()

    val unavailable = TestAppIcons.manager(
      FakeLauncherAliasAccess(supportsAlternateIcons = false),
      TestAppIcons.savedAppIcon()
    )
    unavailable.isAvailable()
    assertThat(unavailable.lastKnownAvailability).isFalse()
  }

  @Test
  fun `refreshing finds the enabled alternate`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    aliasAccess.states[TestAppIcons.default.launcherAlias] = AliasState.DISABLED
    aliasAccess.states[TestAppIcons.green.launcherAlias] = AliasState.ENABLED
    val manager = TestAppIcons.manager(aliasAccess)

    manager.refresh()

    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.green)
  }

  @Test
  fun `refreshing corrects a stale choice when nothing is pending`() = runTest {
    val preference = FakeAppIconPreference(savedIconId = "green")
    val savedAppIcon = TestAppIcons.savedAppIcon(preference)

    TestAppIcons.manager(savedAppIcon = savedAppIcon).refresh()

    assertThat(savedAppIcon.option.value).isEqualTo(TestAppIcons.default)
    assertThat(preference.savedIconId).isNull()
  }

  @Test
  fun `selecting saves the choice without touching the launcher`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val preference = FakeAppIconPreference()
    val manager = TestAppIcons.manager(aliasAccess, TestAppIcons.savedAppIcon(preference))

    manager.select(TestAppIcons.blue)

    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.blue)
    assertThat(preference.savedIconId).isEqualTo("blue")
    assertThat(preference.hasPendingIcon).isTrue()
    assertThat(aliasAccess.reads).isEqualTo(0)
    assertThat(aliasAccess.appliedStates).isEmpty()
  }

  @Test
  fun `refreshing keeps a pending choice`() = runTest {
    val manager = TestAppIcons.manager()
    manager.select(TestAppIcons.blue)

    manager.refresh()

    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.blue)
  }

  @Test
  fun `applying a pending choice enables it before disabling the default`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val preference = FakeAppIconPreference()
    val manager = TestAppIcons.manager(aliasAccess, TestAppIcons.savedAppIcon(preference))
    manager.select(TestAppIcons.blue)

    manager.applyPendingChange { true }

    val applied = aliasAccess.appliedStates.single()
    assertThat(applied.keys.first()).isEqualTo(TestAppIcons.blue.launcherAlias)
    assertThat(applied).containsExactly(
      TestAppIcons.blue.launcherAlias, AliasState.ENABLED,
      TestAppIcons.default.launcherAlias, AliasState.DISABLED,
      TestAppIcons.green.launcherAlias, AliasState.DEFAULT
    )
    assertThat(preference.hasPendingIcon).isFalse()

    manager.applyPendingChange { true }
    assertThat(aliasAccess.appliedStates).hasSize(1)
  }

  @Test
  fun `applying the default resets every alias to its manifest state`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val manager = TestAppIcons.manager(aliasAccess)
    manager.select(TestAppIcons.green)
    manager.applyPendingChange { true }

    manager.select(TestAppIcons.default)
    manager.applyPendingChange { true }

    assertThat(aliasAccess.appliedStates.last().values).containsExactly(
      AliasState.DEFAULT, AliasState.DEFAULT, AliasState.DEFAULT
    )
  }

  @Test
  fun `nothing is applied once the app is back in the foreground`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val preference = FakeAppIconPreference()
    val manager = TestAppIcons.manager(aliasAccess, TestAppIcons.savedAppIcon(preference))
    manager.select(TestAppIcons.blue)

    manager.applyPendingChange { false }

    assertThat(aliasAccess.appliedStates).isEmpty()
    assertThat(preference.hasPendingIcon).isTrue()
  }

  @Test
  fun `a failed launcher update stays pending`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess(onSetStates = { throw IllegalStateException("no") })
    val preference = FakeAppIconPreference()
    val manager = TestAppIcons.manager(aliasAccess, TestAppIcons.savedAppIcon(preference))
    manager.select(TestAppIcons.blue)

    val result = runCatching { manager.applyPendingChange { true } }

    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)

    assertThat(preference.hasPendingIcon).isTrue()
  }

  @Test
  fun `switching away from the default removes the tasks it started before disabling it`() = runTest {
    val events = mutableListOf<String>()
    val aliasAccess = FakeLauncherAliasAccess(onSetStates = { events += "set alias states" })
    val taskAccess = FakeAppTaskAccess(onRemove = { events += "remove tasks" })
    val manager = TestAppIcons.manager(aliasAccess, taskAccess = taskAccess)
    manager.select(TestAppIcons.blue)

    manager.applyPendingChange { true }

    assertThat(taskAccess.removedAliases).containsExactly(setOf(TestAppIcons.default.launcherAlias))
    assertThat(events).containsExactly("remove tasks", "set alias states").inOrder()
  }

  @Test
  fun `only tasks started from the default alias are ever removed`() = runTest {
    val taskAccess = FakeAppTaskAccess()
    val manager = TestAppIcons.manager(taskAccess = taskAccess)

    manager.select(TestAppIcons.blue)
    manager.applyPendingChange { true }
    manager.select(TestAppIcons.green)
    manager.applyPendingChange { true }

    assertThat(taskAccess.removedAliases.toSet()).containsExactly(setOf(TestAppIcons.default.launcherAlias))
  }

  @Test
  fun `switching back to the default removes no tasks`() = runTest {
    val taskAccess = FakeAppTaskAccess()
    val manager = TestAppIcons.manager(taskAccess = taskAccess)
    manager.select(TestAppIcons.blue)
    manager.applyPendingChange { true }
    taskAccess.removedAliases.clear()

    manager.select(TestAppIcons.default)
    manager.applyPendingChange { true }

    assertThat(taskAccess.removedAliases).isEmpty()
  }

  @Test
  fun `nothing is applied while another app's screen is in one of the app's tasks`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val taskAccess = FakeAppTaskAccess(hasOtherApp = true)
    val preference = FakeAppIconPreference()
    val manager = TestAppIcons.manager(aliasAccess, TestAppIcons.savedAppIcon(preference), taskAccess)
    manager.select(TestAppIcons.blue)

    manager.applyPendingChange { true }

    assertThat(taskAccess.removedAliases).isEmpty()
    assertThat(aliasAccess.appliedStates).isEmpty()
    assertThat(preference.hasPendingIcon).isTrue()
  }

  @Test
  fun `a failed task removal keeps the aliases and the change pending`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val taskAccess = FakeAppTaskAccess(onRemove = { throw SecurityException("no") })
    val preference = FakeAppIconPreference()
    val manager = TestAppIcons.manager(aliasAccess, TestAppIcons.savedAppIcon(preference), taskAccess)
    manager.select(TestAppIcons.blue)

    val result = runCatching { manager.applyPendingChange { true } }

    assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    assertThat(aliasAccess.appliedStates).isEmpty()
    assertThat(preference.hasPendingIcon).isTrue()
  }

  @Test
  fun `a launcher read in flight doesn't undo a new choice`() = runTest {
    val reading = CountDownLatch(1)
    val release = CountDownLatch(1)
    val aliasAccess = FakeLauncherAliasAccess(onState = {
      reading.countDown()
      release.await()
    })
    val manager = TestAppIcons.manager(aliasAccess)

    val refresh = launch(Dispatchers.Default) { manager.refresh() }
    withContext(Dispatchers.IO) { reading.await() }
    val select = launch(Dispatchers.Default) { manager.select(TestAppIcons.blue) }
    // gives the choice time to land before the read finishes, unless the lock holds it back
    withContext(Dispatchers.Default) { delay(100) }
    release.countDown()
    refresh.join()
    select.join()

    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.blue)
  }

  @Test
  fun `only the selected alias is enabled after applying`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val manager = TestAppIcons.manager(aliasAccess)
    manager.select(TestAppIcons.green)
    manager.applyPendingChange { true }

    manager.refresh()

    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.green)
  }
}
