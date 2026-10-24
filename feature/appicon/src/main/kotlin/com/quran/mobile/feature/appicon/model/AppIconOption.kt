package com.quran.mobile.feature.appicon.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.quran.labs.androidquran.common.ui.core.QuranAccent

@Immutable
data class AppIconOption(
  val id: String,
  val launcherAlias: String,
  @StringRes val name: Int,
  @DrawableRes val icon: Int,
  val accent: QuranAccent,
  val isDefault: Boolean = false
)
