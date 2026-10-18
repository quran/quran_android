package com.quran.mobile.feature.pagetheme.state

import androidx.compose.runtime.Immutable
import com.quran.mobile.feature.pagetheme.PageTheme
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class PageThemeSheetState(
  val pageAppearance: String,
  val cards: ImmutableList<PageThemeCard>,
  val textBrightness: Int,
  val backgroundBrightness: Int,
  val showBrightness: Boolean,
  val showOldBackground: Boolean,
  val useOldBackground: Boolean,
  val eventSink: (PageThemeSheetEvent) -> Unit = {}
)

@Immutable
data class PageThemeCard(
  val theme: PageTheme,
  val isSelected: Boolean,
  val background: PageThemeCardBackground,
  val textColor: Int
)

sealed interface PageThemeCardBackground {
  data object Shaded : PageThemeCardBackground
  data class Solid(val color: Int) : PageThemeCardBackground
}

sealed interface PageThemeSheetEvent {
  data class SelectTheme(val theme: String) : PageThemeSheetEvent
  data class SelectAppearance(val appearance: String) : PageThemeSheetEvent
  data class TextBrightnessChanged(val brightness: Int) : PageThemeSheetEvent
  data class BackgroundBrightnessChanged(val brightness: Int) : PageThemeSheetEvent
  data class UseOldBackgroundChanged(val useOldBackground: Boolean) : PageThemeSheetEvent
}
