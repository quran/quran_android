package com.quran.mobile.feature.appicon

import com.quran.data.di.AppScope
import com.quran.mobile.feature.appicon.model.AppIconCatalog
import com.quran.mobile.feature.appicon.model.AppIconOption
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppIconPreference {
  var savedIconId: String?
  var hasPendingIcon: Boolean
  var savedAvailability: Boolean?
}

@SingleIn(AppScope::class)
@Inject
class SavedAppIcon(
  catalog: AppIconCatalog,
  private val preference: AppIconPreference
) {
  private val optionFlow = MutableStateFlow(
    catalog.options.firstOrNull { it.id == preference.savedIconId } ?: catalog.defaultOption
  )
  val option: StateFlow<AppIconOption> = optionFlow.asStateFlow()

  val isAvailable: Boolean?
    get() = preference.savedAvailability

  fun saveAvailability(available: Boolean) {
    if (preference.savedAvailability != available) {
      preference.savedAvailability = available
    }
  }

  val isPending: Boolean
    get() = preference.hasPendingIcon

  fun select(option: AppIconOption) {
    preference.hasPendingIcon = true
    save(option)
  }

  fun markApplied() {
    preference.hasPendingIcon = false
  }

  fun save(option: AppIconOption) {
    if (option == optionFlow.value) return
    preference.savedIconId = if (option.isDefault) null else option.id
    optionFlow.value = option
  }
}
