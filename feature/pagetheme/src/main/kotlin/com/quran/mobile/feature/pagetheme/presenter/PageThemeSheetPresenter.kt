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
    val isAppearanceDark = remember(pageAppearance) { settings.isPageAppearanceDark }

    val cards = remember(pageTheme, isAppearanceDark, textBrightness, backgroundBrightness, useOldBackground) {
      themeCards(pageTheme, isAppearanceDark, useOldBackground)
    }
    val isOriginal = pageTheme == PageThemeConstants.PAGE_THEME_ORIGINAL

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
      showBrightness = isOriginal && isAppearanceDark,
      showOldBackground = isOriginal && !isAppearanceDark && hasOldBackgroundOption,
      useOldBackground = useOldBackground,
      eventSink = eventSink
    )
  }

  private fun themeCards(
    selectedTheme: String,
    isAppearanceDark: Boolean,
    useOldBackground: Boolean
  ): ImmutableList<PageThemeCard> {
    return PageTheme.entries.map { theme ->
      val isSelected = theme.storageKey == selectedTheme
      if (theme == PageTheme.Original) {
        originalCard(isSelected, isAppearanceDark, useOldBackground)
      } else {
        PageThemeCard(
          theme = theme,
          isSelected = isSelected,
          background = PageThemeCardBackground.Solid(theme.backgroundColor(context, isAppearanceDark)),
          textColor = theme.textColor(context, isAppearanceDark)
        )
      }
    }.toImmutableList()
  }

  private fun originalCard(
    isSelected: Boolean,
    isAppearanceDark: Boolean,
    useOldBackground: Boolean
  ): PageThemeCard {
    return if (isAppearanceDark) {
      val background = settings.nightModeBackgroundBrightness
      val text = settings.originalNightTextBrightness
      PageThemeCard(
        theme = PageTheme.Original,
        isSelected = isSelected,
        background = PageThemeCardBackground.Solid(Color.rgb(background, background, background)),
        textColor = Color.rgb(text, text, text)
      )
    } else {
      val background = if (useOldBackground) {
        PageThemeCardBackground.Solid(context.getColor(R.color.page_background))
      } else {
        PageThemeCardBackground.Shaded
      }
      PageThemeCard(
        theme = PageTheme.Original,
        isSelected = isSelected,
        background = background,
        textColor = Color.BLACK
      )
    }
  }
}
