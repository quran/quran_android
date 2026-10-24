package com.quran.mobile.feature.appicon

import com.quran.data.di.AppScope
import com.quran.mobile.feature.appicon.LauncherAliasAccess.AliasState
import com.quran.mobile.feature.appicon.model.AppIconCatalog
import com.quran.mobile.feature.appicon.model.AppIconOption
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
class AppIconManager(
  val catalog: AppIconCatalog,
  private val aliasAccess: LauncherAliasAccess
) {
  val isAvailable: Boolean by lazy {
    aliasAccess.supportsAlternateIcons &&
        catalog.options.all { aliasAccess.isDeclared(it.launcherAlias) }
  }

  private val currentOptionFlow = MutableStateFlow(readCurrentOption())
  val currentOption: StateFlow<AppIconOption> = currentOptionFlow.asStateFlow()

  suspend fun select(option: AppIconOption) {
    withContext(Dispatchers.IO) {
      try {
        aliasAccess.setStates(aliasStatesFor(option))
      } finally {
        // publish before returning, since the return throws if the caller was cancelled meanwhile
        currentOptionFlow.value = readCurrentOption()
      }
    }
  }

  fun closesTask(launcherAlias: String?, option: AppIconOption): Boolean {
    return launcherAlias != null && aliasStatesFor(option)[launcherAlias] == AliasState.DISABLED
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

  private fun readCurrentOption(): AppIconOption {
    if (!isAvailable) return catalog.defaultOption
    return catalog.options.firstOrNull {
      !it.isDefault && aliasAccess.state(it.launcherAlias) == AliasState.ENABLED
    } ?: catalog.defaultOption
  }
}
