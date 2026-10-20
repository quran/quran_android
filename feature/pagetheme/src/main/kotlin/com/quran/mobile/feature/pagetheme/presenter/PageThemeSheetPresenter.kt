package com.quran.mobile.feature.pagetheme.presenter

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quran.mobile.feature.pagetheme.PageTheme
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.PageThemeSettings
import com.quran.mobile.feature.pagetheme.R
import com.quran.mobile.feature.pagetheme.state.PageThemeCard
import com.quran.mobile.feature.pagetheme.state.PageThemeCardBackground
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetEvent
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class PageThemeSheetPresenter(
  private val context: Context,
  private val settings: PageThemeSettings
) {

  @Composable
  fun present(): PageThemeSheetState {
    var pageTheme by remember { mutableStateOf(settings.pageTheme) }
    var pageAppearance by remember { mutableStateOf(settings.pageAppearance) }
    var textBrightness by remember { mutableIntStateOf(settings.nightModeTextBrightness) }
    var backgroundBrightness by remember {
      mutableIntStateOf(
        settings.nightModeBackgroundBrightness.coerceIn(0, PageThemeConstants.MAX_NIGHT_MODE_BACKGROUND_BRIGHTNESS)
      )
    }
    var useOldBackground by remember { mutableStateOf(settings.useOldBackground()) }
    val hasOldBackgroundOption = remember { settings.hasOldBackgroundOption() }
    val isNightMode = remember(pageAppearance) { settings.isNightMode }

    val cards = remember(pageTheme, useOldBackground) {
      themeCards(pageTheme, useOldBackground)
    }
    val isLight = pageTheme == PageThemeConstants.PAGE_THEME_LIGHT

    val eventSink: (PageThemeSheetEvent) -> Unit = { event ->
      when (event) {
        is PageThemeSheetEvent.SelectTheme -> {
          settings.pageTheme = event.theme
          pageTheme = event.theme
        }

        is PageThemeSheetEvent.SelectAppearance -> {
          settings.pageAppearance = event.appearance
          pageAppearance = event.appearance
        }

        is PageThemeSheetEvent.TextBrightnessChanged -> {
          settings.nightModeTextBrightness = event.brightness
          textBrightness = event.brightness
        }

        is PageThemeSheetEvent.BackgroundBrightnessChanged -> {
          settings.nightModeBackgroundBrightness = event.brightness
          backgroundBrightness = event.brightness
        }

        is PageThemeSheetEvent.UseOldBackgroundChanged -> {
          settings.setUseOldBackground(event.useOldBackground)
          useOldBackground = event.useOldBackground
        }
      }
    }

    return PageThemeSheetState(
      pageAppearance = pageAppearance,
      cards = cards,
      textBrightness = textBrightness,
      backgroundBrightness = backgroundBrightness,
      showBrightness = isNightMode,
      showOldBackground = isLight && !isNightMode && hasOldBackgroundOption,
      useOldBackground = useOldBackground,
      eventSink = eventSink
    )
  }

  private fun themeCards(
    selectedTheme: String,
    useOldBackground: Boolean
  ): ImmutableList<PageThemeCard> {
    return PageTheme.entries.map { theme ->
      val background = when {
        theme != PageTheme.Light -> PageThemeCardBackground.Solid(theme.backgroundColor(context))
        useOldBackground -> PageThemeCardBackground.Solid(context.getColor(R.color.page_background))
        else -> PageThemeCardBackground.Shaded
      }
      PageThemeCard(
        theme = theme,
        isSelected = theme.storageKey == selectedTheme,
        background = background,
        textColor = Color.BLACK
      )
    }.toImmutableList()
  }
}
