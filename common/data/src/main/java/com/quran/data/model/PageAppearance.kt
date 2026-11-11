package com.quran.data.model

enum class PageAppearance {
  Light,
  Dark,
  Automatic;

  fun isNightMode(isAppDark: Boolean): Boolean = this == Dark || (this == Automatic && isAppDark)
}
