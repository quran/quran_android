package com.quran.mobile.feature.appicon

import com.quran.data.di.AppScope
import com.quran.mobile.feature.appicon.LauncherAliasAccess.AliasState
import com.quran.mobile.feature.appicon.model.AppIconCatalog
import com.quran.mobile.feature.appicon.model.AppIconOption
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
class AppIconManager(
  val catalog: AppIconCatalog,
  private val aliasAccess: LauncherAliasAccess,
  private val taskAccess: AppTaskAccess,
  private val savedAppIcon: SavedAppIcon
) {
  private val mutex = Mutex()

  private val available: Boolean by lazy {
    aliasAccess.supportsAlternateIcons &&
        catalog.options.all { aliasAccess.isDeclared(it.launcherAlias) }
  }

  val currentOption: StateFlow<AppIconOption> = savedAppIcon.option

  val lastKnownAvailability: Boolean?
    get() = savedAppIcon.isAvailable

  suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
    available.also { savedAppIcon.saveAvailability(it) }
  }

  suspend fun refresh() {
    mutex.withLock {
      withContext(Dispatchers.IO) {
        if (!savedAppIcon.isPending) {
          savedAppIcon.save(readLauncherOption())
        }
      }
    }
  }

  suspend fun select(option: AppIconOption) {
    mutex.withLock {
      if (option != savedAppIcon.option.value) {
        savedAppIcon.select(option)
      }
    }
  }

  suspend fun applyPendingChange(isInBackground: () -> Boolean) {
    mutex.withLock {
      if (savedAppIcon.isPending && isInBackground() && !taskAccess.hasOtherAppInTask()) {
        if (available) {
          val states = aliasStatesFor(savedAppIcon.option.value)
          val disabledAliases = states.filterValues { it == AliasState.DISABLED }.keys
          if (disabledAliases.isNotEmpty()) {
            taskAccess.removeTasksStartedFrom(disabledAliases)
          }
          aliasAccess.setStates(states)
        }
        savedAppIcon.markApplied()
      }
    }
  }

  private fun aliasStatesFor(selected: AppIconOption): Map<String, AliasState> {
    val (chosen, others) = catalog.options.partition { it == selected }
    return (chosen + others).associate { option ->
      val state = when {
        option == selected -> if (option.isDefault) AliasState.DEFAULT else AliasState.ENABLED
        option.isDefault -> AliasState.DISABLED
        else -> AliasState.DEFAULT
      }
      option.launcherAlias to state
    }
  }

  private fun readLauncherOption(): AppIconOption {
    return if (available) {
      catalog.options.firstOrNull {
        !it.isDefault && aliasAccess.state(it.launcherAlias) == AliasState.ENABLED
      } ?: catalog.defaultOption
    } else {
      catalog.defaultOption
    }
  }
}
