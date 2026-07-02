package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class AyahBookmarkWarningColors(
  val background: Color,
  val border: Color,
  val content: Color
)

private val LightWarningColors = AyahBookmarkWarningColors(
  background = Color(0xFFFBF2DD),
  border = Color(0xFFECD9A8),
  content = Color(0xFF7A5A10)
)

private val DarkWarningColors = AyahBookmarkWarningColors(
  background = Color(0xFF3A331E),
  border = Color(0xFF5C4E28),
  content = Color(0xFFE0C589)
)

internal val ayahBookmarkWarningColors: AyahBookmarkWarningColors
  @Composable get() = if (isSystemInDarkTheme()) DarkWarningColors else LightWarningColors
