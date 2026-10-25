package com.quran.mobile.feature.appicon.fakes

import androidx.compose.ui.graphics.Color
import com.quran.labs.androidquran.common.ui.core.QuranAccent
import com.quran.mobile.feature.appicon.AppIconManager
import com.quran.mobile.feature.appicon.AppIconPreference
import com.quran.mobile.feature.appicon.AppTaskAccess
import com.quran.mobile.feature.appicon.LauncherAliasAccess
import com.quran.mobile.feature.appicon.LauncherAliasAccess.AliasState
import com.quran.mobile.feature.appicon.SavedAppIcon
import com.quran.mobile.feature.appicon.model.AppIconCatalog
import com.quran.mobile.feature.appicon.model.AppIconOption
import com.quran.mobile.feature.appicon.model.AppIconSection
import kotlinx.collections.immutable.persistentListOf

class FakeLauncherAliasAccess(
  override val supportsAlternateIcons: Boolean = true,
  private val declaredAliases: Set<String> = TestAppIcons.catalog.options.map { it.launcherAlias }.toSet(),
  private val onState: () -> Unit = {},
  private val onSetStates: () -> Unit = {}
) : LauncherAliasAccess {
  val states = mutableMapOf<String, AliasState>()
  val appliedStates = mutableListOf<Map<String, AliasState>>()
  var reads = 0

  override fun isDeclared(alias: String): Boolean {
    reads++
    return alias in declaredAliases
  }

  override fun state(alias: String): AliasState {
    onState()
    reads++
    return states[alias] ?: AliasState.DEFAULT
  }

  override fun setStates(states: Map<String, AliasState>) {
    onSetStates()
    appliedStates += states
    this.states.putAll(states)
  }
}

class FakeAppIconPreference(
  override var savedIconId: String? = null,
  override var hasPendingIcon: Boolean = false,
  override var savedAvailability: Boolean? = null
) : AppIconPreference

object TestAppIcons {
  private val accent = QuranAccent(light = Color.Blue, dark = Color.Yellow, onDark = Color.Black)

  val default = AppIconOption("default", "app.QuranDataActivity", 0, 0, accent, isDefault = true)
  val blue = AppIconOption("blue", "app.AppIconBlue", 0, 0, accent)
  val green = AppIconOption("green", "app.AppIconGreen", 0, 0, accent)

  val catalog = AppIconCatalog(
    persistentListOf(
      AppIconSection("signature", 0, AppIconSection.PreviewSize.LARGE, persistentListOf(default)),
      AppIconSection("colors", 0, AppIconSection.PreviewSize.REGULAR, persistentListOf(blue, green))
    )
  )

  fun savedAppIcon(preference: AppIconPreference = FakeAppIconPreference()) =
    SavedAppIcon(catalog, preference)

  fun manager(
    aliasAccess: LauncherAliasAccess = FakeLauncherAliasAccess(),
    savedAppIcon: SavedAppIcon = savedAppIcon(),
    taskAccess: AppTaskAccess = FakeAppTaskAccess()
  ) = AppIconManager(catalog, aliasAccess, taskAccess, savedAppIcon)
}
