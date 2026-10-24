package com.quran.labs.androidquran.appicon

import androidx.compose.ui.graphics.Color
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranAccent
import com.quran.mobile.feature.appicon.model.AppIconCatalog
import com.quran.mobile.feature.appicon.model.AppIconOption
import com.quran.mobile.feature.appicon.model.AppIconSection
import kotlinx.collections.immutable.persistentListOf
import com.quran.mobile.feature.appicon.R as AppIconR

object QuranAppIconCatalog {
  private const val ALIAS_PREFIX = "com.quran.labs.androidquran.AppIcon"

  private val navyGold = AppIconOption(
    id = "navy-gold",
    launcherAlias = "com.quran.labs.androidquran.QuranDataActivity",
    name = AppIconR.string.app_icon_name_navy_gold,
    icon = R.mipmap.ic_launcher,
    accent = QuranAccent.NavyGold,
    isDefault = true
  )

  private val tealWhite = AppIconOption(
    id = "teal-white",
    launcherAlias = "${ALIAS_PREFIX}TealWhite",
    name = AppIconR.string.app_icon_name_teal_white,
    icon = R.mipmap.ic_launcher_teal_white,
    accent = QuranAccent(light = Color(0xFF1B6B71), dark = Color(0xFF2BA9B2), onDark = Color(0xFF00292D))
  )

  private val blackGold = AppIconOption(
    id = "black-gold",
    launcherAlias = "${ALIAS_PREFIX}BlackGold",
    name = AppIconR.string.app_icon_name_black_gold,
    icon = R.mipmap.ic_launcher_black_gold,
    accent = QuranAccent(light = Color(0xFF7A5503), dark = Color(0xFFE2AF56), onDark = Color(0xFF161616))
  )

  private val blue = AppIconOption(
    id = "blue",
    launcherAlias = "${ALIAS_PREFIX}Blue",
    name = AppIconR.string.app_icon_name_blue,
    icon = R.mipmap.ic_launcher_blue,
    accent = QuranAccent(light = Color(0xFF025F9D), dark = Color(0xFF56B1FE), onDark = Color(0xFF00163D))
  )

  private val purple = AppIconOption(
    id = "purple",
    launcherAlias = "${ALIAS_PREFIX}Purple",
    name = AppIconR.string.app_icon_name_purple,
    icon = R.mipmap.ic_launcher_purple,
    accent = QuranAccent(light = Color(0xFF8D19CB), dark = Color(0xFFCE90FE), onDark = Color(0xFF330066))
  )

  private val red = AppIconOption(
    id = "red",
    launcherAlias = "${ALIAS_PREFIX}Red",
    name = AppIconR.string.app_icon_name_red,
    icon = R.mipmap.ic_launcher_red,
    accent = QuranAccent(light = Color(0xFFBA0120), dark = Color(0xFFFE8881), onDark = Color(0xFF3D0000))
  )

  private val orange = AppIconOption(
    id = "orange",
    launcherAlias = "${ALIAS_PREFIX}Orange",
    name = AppIconR.string.app_icon_name_orange,
    icon = R.mipmap.ic_launcher_orange,
    accent = QuranAccent(light = Color(0xFF9B3F01), dark = Color(0xFFFF8B52), onDark = Color(0xFF3A1400))
  )

  private val green = AppIconOption(
    id = "green",
    launcherAlias = "${ALIAS_PREFIX}Green",
    name = AppIconR.string.app_icon_name_green,
    icon = R.mipmap.ic_launcher_green,
    accent = QuranAccent(light = Color(0xFF03694E), dark = Color(0xFF1EC192), onDark = Color(0xFF011B11))
  )

  val catalog = AppIconCatalog(
    persistentListOf(
      AppIconSection(
        id = "signature",
        title = AppIconR.string.app_icon_section_signature,
        previewSize = AppIconSection.PreviewSize.LARGE,
        options = persistentListOf(navyGold, tealWhite, blackGold)
      ),
      AppIconSection(
        id = "colors",
        title = AppIconR.string.app_icon_section_colors,
        previewSize = AppIconSection.PreviewSize.REGULAR,
        options = persistentListOf(blue, purple, red, orange, green)
      )
    )
  )
}
