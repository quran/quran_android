package com.quran.mobile.feature.appicon

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.appicon.LauncherAliasAccess.AliasState
import com.quran.mobile.feature.appicon.fakes.FakeLauncherAliasAccess
import com.quran.mobile.feature.appicon.fakes.TestAppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import java.util.concurrent.CountDownLatch

class AppIconManagerTest {

  @Test
  fun `unavailable without alternate icon support`() {
    val manager = AppIconManager(TestAppIcons.catalog, FakeLauncherAliasAccess(supportsAlternateIcons = false))

    assertThat(manager.isAvailable).isFalse()
    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.default)
  }

  @Test
  fun `unavailable when an alias is missing from the manifest`() {
    val aliasAccess = FakeLauncherAliasAccess(declaredAliases = setOf(TestAppIcons.default.launcherAlias))
    aliasAccess.states[TestAppIcons.blue.launcherAlias] = AliasState.ENABLED
    val manager = AppIconManager(TestAppIcons.catalog, aliasAccess)

    assertThat(manager.isAvailable).isFalse()
    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.default)
  }

  @Test
  fun `current option is the default until an alternate is enabled`() {
    val aliasAccess = FakeLauncherAliasAccess()
    assertThat(AppIconManager(TestAppIcons.catalog, aliasAccess).currentOption.value)
      .isEqualTo(TestAppIcons.default)

    aliasAccess.states[TestAppIcons.default.launcherAlias] = AliasState.DISABLED
    aliasAccess.states[TestAppIcons.green.launcherAlias] = AliasState.ENABLED
    assertThat(AppIconManager(TestAppIcons.catalog, aliasAccess).currentOption.value)
      .isEqualTo(TestAppIcons.green)
  }

  @Test
  fun `selecting an alternate enables it before disabling the default`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val manager = AppIconManager(TestAppIcons.catalog, aliasAccess)

    manager.select(TestAppIcons.blue)

    val applied = aliasAccess.appliedStates.single()
    assertThat(applied.keys.first()).isEqualTo(TestAppIcons.blue.launcherAlias)
    assertThat(applied).containsExactly(
      TestAppIcons.blue.launcherAlias, AliasState.ENABLED,
      TestAppIcons.default.launcherAlias, AliasState.DISABLED,
      TestAppIcons.green.launcherAlias, AliasState.DEFAULT
    )
    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.blue)
  }

  @Test
  fun `publishes the selection when the caller is cancelled while switching`() = runTest {
    val switching = CountDownLatch(1)
    val release = CountDownLatch(1)
    val aliasAccess = FakeLauncherAliasAccess(onSetStates = {
      switching.countDown()
      release.await()
    })
    val manager = AppIconManager(TestAppIcons.catalog, aliasAccess)

    val job = launch(Dispatchers.Default) { manager.select(TestAppIcons.blue) }
    withContext(Dispatchers.IO) { switching.await() }
    job.cancel()
    release.countDown()
    job.join()

    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.blue)
  }

  @Test
  fun `selecting the default resets every alias to its manifest state`() = runTest {
    val aliasAccess = FakeLauncherAliasAccess()
    val manager = AppIconManager(TestAppIcons.catalog, aliasAccess)
    manager.select(TestAppIcons.green)

    manager.select(TestAppIcons.default)

    assertThat(aliasAccess.appliedStates.last().values).containsExactly(
      AliasState.DEFAULT, AliasState.DEFAULT, AliasState.DEFAULT
    )
    assertThat(manager.currentOption.value).isEqualTo(TestAppIcons.default)
  }

  @Test
  fun `only tasks started from the default alias close when choosing an alternate`() {
    val manager = AppIconManager(TestAppIcons.catalog, FakeLauncherAliasAccess())
    val defaultAlias = TestAppIcons.default.launcherAlias
    val blueAlias = TestAppIcons.blue.launcherAlias

    assertThat(manager.closesTask(defaultAlias, TestAppIcons.green)).isTrue()
    assertThat(manager.closesTask(blueAlias, TestAppIcons.green)).isFalse()
    assertThat(manager.closesTask(blueAlias, TestAppIcons.default)).isFalse()
    assertThat(manager.closesTask("app.QuranDataCheckActivity", TestAppIcons.green)).isFalse()
    assertThat(manager.closesTask(null, TestAppIcons.green)).isFalse()
  }
}
