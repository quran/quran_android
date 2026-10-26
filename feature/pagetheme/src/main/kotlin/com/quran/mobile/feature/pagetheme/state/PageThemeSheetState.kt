package com.quran.mobile.feature.pagetheme.state

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class PageThemeSheetState(
  val cards: ImmutableList<PageThemeCard>,
  val isNightPage: Boolean,
  val matchAppTheme: Boolean,
  val textBrightness: Int,
  val backgroundBrightness: Int,
  val showBrightness: Boolean,
  val showOldBackground: Boolean,
  val useOldBackground: Boolean,
  val eventSink: (PageThemeSheetEvent) -> Unit = {}
)

enum class PageColor { Light, Sepia, Dark }

@Immutable
data class PageThemeCard(
  val color: PageColor,
  val isSelected: Boolean,
  val background: PageThemeCardBackground,
  val textColor: Int
)

sealed interface PageThemeCardBackground {
  data object Shaded : PageThemeCardBackground
  data class Solid(val color: Int) : PageThemeCardBackground
}

sealed interface PageThemeSheetEvent {
  data class SelectColor(val color: PageColor) : PageThemeSheetEvent
  data class MatchAppThemeChanged(val matchAppTheme: Boolean) : PageThemeSheetEvent
  data class TextBrightnessChanged(val brightness: Int) : PageThemeSheetEvent
  data class BackgroundBrightnessChanged(val brightness: Int) : PageThemeSheetEvent
  data class UseOldBackgroundChanged(val useOldBackground: Boolean) : PageThemeSheetEvent
}
