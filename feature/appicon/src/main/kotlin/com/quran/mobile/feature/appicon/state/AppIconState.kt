package com.quran.mobile.feature.appicon.state

import androidx.compose.runtime.Immutable
import com.quran.mobile.feature.appicon.model.AppIconOption
import com.quran.mobile.feature.appicon.model.AppIconSection
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class AppIconState(
  val sections: ImmutableList<AppIconSection>,
  val selectedOption: AppIconOption,
  val pendingOption: AppIconOption? = null,
  val eventSink: (AppIconEvent) -> Unit = {}
)

sealed interface AppIconEvent {
  data class Select(val option: AppIconOption) : AppIconEvent
  data object ConfirmChange : AppIconEvent
  data object CancelChange : AppIconEvent
}
