package com.quran.mobile.feature.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

interface LauncherAliasAccess {
  val supportsAlternateIcons: Boolean

  fun isDeclared(alias: String): Boolean

  fun state(alias: String): AliasState

  fun setStates(states: Map<String, AliasState>)

  enum class AliasState { DEFAULT, ENABLED, DISABLED }
}

@ContributesBinding(AppScope::class)
@Inject
class PackageManagerLauncherAliasAccess(
  @param:ApplicationContext private val context: Context
) : LauncherAliasAccess {
  private val packageManager = context.packageManager

  override val supportsAlternateIcons: Boolean by lazy {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
  }

  override fun isDeclared(alias: String): Boolean {
    return try {
      packageManager.getActivityInfo(component(alias), PackageManager.MATCH_DISABLED_COMPONENTS)
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }
  }

  override fun state(alias: String): LauncherAliasAccess.AliasState {
    return when (packageManager.getComponentEnabledSetting(component(alias))) {
      PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> LauncherAliasAccess.AliasState.ENABLED
      PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> LauncherAliasAccess.AliasState.DEFAULT
      else -> LauncherAliasAccess.AliasState.DISABLED
    }
  }

  override fun setStates(states: Map<String, LauncherAliasAccess.AliasState>) {
    val settings = states.map { (alias, state) ->
      val newState = when (state) {
        LauncherAliasAccess.AliasState.DEFAULT -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        LauncherAliasAccess.AliasState.ENABLED -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        LauncherAliasAccess.AliasState.DISABLED -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
      }
      component(alias) to newState
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      packageManager.setComponentEnabledSettings(
        settings.map { (component, newState) ->
          PackageManager.ComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
        }
      )
    } else {
      settings.forEach { (component, newState) ->
        packageManager.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
      }
    }
  }

  private fun component(alias: String) = ComponentName(context.packageName, alias)
}
