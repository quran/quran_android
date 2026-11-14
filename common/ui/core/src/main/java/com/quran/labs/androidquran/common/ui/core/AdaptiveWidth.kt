package com.quran.labs.androidquran.common.ui.core

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material's window width classes: one pane on a phone, more side by side on larger screens. */
enum class WidthClass { Compact, Medium, Expanded }

fun widthClassOf(width: Dp): WidthClass = when {
  width < 600.dp -> WidthClass.Compact
  width < 840.dp -> WidthClass.Medium
  else -> WidthClass.Expanded
}
