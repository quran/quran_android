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
import com.quran.mobile.feature.pagetheme.state.PageColor
import com.quran.mobile.feature.pagetheme.state.PageThemeCard
import com.quran.mobile.feature.pagetheme.state.PageThemeCardBackground
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetEvent
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

class PageThemeSheetPresenter(
  private val context: Context,
  private val settings: PageThemeSettings
) {

  @Composable
  fun present(): PageThemeSheetState {
    var pageTheme by remember { mutableStateOf(PageTheme.fromKey(settings.pageTheme)) }
    var pageAppearance by remember { mutableStateOf(settings.pageAppearance) }
    var textBrightness by remember { mutableIntStateOf(settings.nightModeTextBrightness) }
    var backgroundBrightness by remember {
      mutableIntStateOf(
        settings.nightModeBackgroundBrightness.coerceIn(0, PageThemeConstants.MAX_NIGHT_MODE_BACKGROUND_BRIGHTNESS)
      )
    }
    var useOldBackground by remember { mutableStateOf(settings.useOldBackground()) }
    val hasOldBackgroundOption = remember { settings.hasOldBackgroundOption() }
    val isNightPage = remember(pageAppearance) { settings.isNightMode }
    val nightTextBrightness = remember(textBrightness, backgroundBrightness) { settings.pageTextBrightness }
    val matchAppTheme = pageAppearance == PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC

    val cards = remember(pageTheme, isNightPage, useOldBackground, backgroundBrightness, nightTextBrightness) {
      pageCards(pageTheme, isNightPage, useOldBackground, backgroundBrightness, nightTextBrightness)
    }

    val setAppearance: (String) -> Unit = { appearance ->
      settings.pageAppearance = appearance
      pageAppearance = appearance
    }

    val eventSink: (PageThemeSheetEvent) -> Unit = { event ->
      when (event) {
        is PageThemeSheetEvent.SelectColor -> {
          if (event.color == PageColor.Dark) {
            setAppearance(
              if (matchAppTheme && isNightPage) PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
              else PageThemeConstants.PAGE_APPEARANCE_DARK
            )
          } else {
            val theme = if (event.color == PageColor.Sepia) PageTheme.Sepia else PageTheme.Light
            settings.pageTheme = theme.storageKey
            pageTheme = theme
            setAppearance(
              if (matchAppTheme && !isNightPage) PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
              else PageThemeConstants.PAGE_APPEARANCE_LIGHT
            )
          }
        }

        is PageThemeSheetEvent.MatchAppThemeChanged -> {
          setAppearance(
            when {
              event.matchAppTheme -> PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
              isNightPage -> PageThemeConstants.PAGE_APPEARANCE_DARK
              else -> PageThemeConstants.PAGE_APPEARANCE_LIGHT
            }
          )
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
      cards = cards,
      isNightPage = isNightPage,
      matchAppTheme = matchAppTheme,
      textBrightness = textBrightness,
      backgroundBrightness = backgroundBrightness,
      showBrightness = isNightPage,
      showOldBackground = !isNightPage && pageTheme == PageTheme.Light && hasOldBackgroundOption,
      useOldBackground = useOldBackground,
      eventSink = eventSink
    )
  }

  private fun pageCards(
    pageTheme: PageTheme,
    isNightPage: Boolean,
    useOldBackground: Boolean,
    backgroundBrightness: Int,
    nightTextBrightness: Int
  ): ImmutableList<PageThemeCard> {
    val lightBackground = if (useOldBackground) {
      PageThemeCardBackground.Solid(context.getColor(R.color.page_background))
    } else {
      PageThemeCardBackground.Shaded
    }
    return persistentListOf(
      PageThemeCard(
        color = PageColor.Light,
        isSelected = !isNightPage && pageTheme == PageTheme.Light,
        background = lightBackground,
        textColor = Color.BLACK
      ),
      PageThemeCard(
        color = PageColor.Sepia,
        isSelected = !isNightPage && pageTheme == PageTheme.Sepia,
        background = PageThemeCardBackground.Solid(PageTheme.Sepia.backgroundColor(context)),
        textColor = Color.BLACK
      ),
      PageThemeCard(
        color = PageColor.Dark,
        isSelected = isNightPage,
        background = PageThemeCardBackground.Solid(
          Color.rgb(backgroundBrightness, backgroundBrightness, backgroundBrightness)
        ),
        textColor = Color.rgb(nightTextBrightness, nightTextBrightness, nightTextBrightness)
      )
    )
  }
}
