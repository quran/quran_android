package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class AyahBookmarkHighlightColors(
  val background: Color,
  val border: Color,
  val icon: Color,
  val title: Color,
  val subtitle: Color,
  val swatchOutline: Color
)

private val LightHighlightColors = AyahBookmarkHighlightColors(
  background = Color(0xFFFCFAF4),
  border = Color(0xFFE6E2D6),
  icon = Color(0xFF8A7A4A),
  title = Color(0xFF4A422C),
  subtitle = Color(0xFF8A7F63),
  swatchOutline = Color(0xFFC4BDA6)
)

private val DarkHighlightColors = AyahBookmarkHighlightColors(
  background = Color(0xFF26231C),
  border = Color(0xFF46402F),
  icon = Color(0xFFC6B98E),
  title = Color(0xFFE7DFCB),
  subtitle = Color(0xFFA79C82),
  swatchOutline = Color(0xFF6E6752)
)

internal val ayahBookmarkHighlightColors: AyahBookmarkHighlightColors
  @Composable get() = if (isSystemInDarkTheme()) DarkHighlightColors else LightHighlightColors
