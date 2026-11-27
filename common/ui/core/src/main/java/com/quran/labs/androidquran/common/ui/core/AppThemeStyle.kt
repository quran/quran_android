package com.quran.labs.androidquran.common.ui.core

import androidx.annotation.ColorInt
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

@Immutable
data class AppThemePalette(
  val surface: Color,
  val surfaceElevated: Color,
  val cardSurface: Color,
  val surfaceVariant: Color,
  val headerBackground: Color,
  val onHeader: Color,
  val toolbarBackground: Color,
  val onToolbar: Color,
  val onToolbarSecondary: Color,
  val tabSelectedText: Color,
  val tabUnselectedText: Color,
  val tabIndicator: Color,
  val accent: Color,
  val onAccent: Color,
  val tertiary: Color,
  val onTertiary: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val suraBadgeBackground: Color,
  val suraBadgeText: Color,
  val suraBadgeStroke: Color,
  val divider: Color,
  val searchHighlight: Color,
  val isLightToolbar: Boolean,
  val glassBg: Color = cardSurface.copy(alpha = if (isLightToolbar) 0.86f else 0.88f),
  val glassBorder: Color = accent.copy(alpha = if (isLightToolbar) 0.18f else 0.22f),
  val mushafOverlayBg: Color = if (isLightToolbar) Color(0xFFF8EBD5) else surface,
  val navBg: Color = cardSurface.copy(alpha = 0.94f),
  val navBorder: Color = divider,
  val ctaBackground: Color = accent,
  val onCta: Color = onAccent,
  val ayahHighlightFill: Color = accent.copy(alpha = if (isLightToolbar) 0.16f else 0.20f),
  val editorialAccent: Color = accent,
  val surfaceTopGlow: Color = surfaceElevated,
  val glassFloatBg: Color = surfaceElevated.copy(alpha = 0.93f),
  val shadowTint: Color = if (isLightToolbar) Color(0xFF18212C) else Color(0xFF000000)
)

enum class AppThemeStyle(
  val storageKey: String,
  val lightPalette: AppThemePalette,
  val darkPalette: AppThemePalette
) {
  DEFAULT(
    storageKey = "default",
    lightPalette = AppThemePalette(
      surface = Color(0xFFF6F5F1),
      surfaceElevated = Color(0xFFFFFFFF),
      cardSurface = Color(0xFFFFFFFF),
      surfaceVariant = Color(0xFFEAE8E1),
      headerBackground = Color(0xFFEEECE6),
      onHeader = Color(0xFF1B1E22),
      toolbarBackground = Color(0xFFF6F5F1),
      onToolbar = Color(0xFF1B1E22),
      onToolbarSecondary = Color(0xFF61666D),
      tabSelectedText = Color(0xFF23394F),
      tabUnselectedText = Color(0xFF61666D),
      tabIndicator = Color(0xFF23394F),
      accent = Color(0xFF23394F),
      onAccent = Color(0xFFFFFFFF),
      tertiary = Color(0xFFDCE4EC),
      onTertiary = Color(0xFF23394F),
      textPrimary = Color(0xFF1B1E22),
      textSecondary = Color(0xFF61666D),
      suraBadgeBackground = Color(0xFFF5EFE3),
      suraBadgeText = Color(0xFF7C5414),
      suraBadgeStroke = Color(0xFFD9C6A3),
      divider = Color(0xFFE4E1D9),
      searchHighlight = Color(0xFF7C5414),
      isLightToolbar = true,
      glassBg = Color(0xDBFFFFFF),
      glassBorder = Color(0x2E23394F),
      mushafOverlayBg = Color(0xFFF8EBD5),
      navBg = Color(0xF0FFFFFF),
      navBorder = Color(0xFFE4E1D9),
      ctaBackground = Color(0xFF23394F),
      onCta = Color(0xFFFFFFFF),
      ayahHighlightFill = Color(0xFF9B6D22).copy(alpha = 0.16f),
      editorialAccent = Color(0xFF9B6D22),
      surfaceTopGlow = Color(0xFFFFFDF9),
      glassFloatBg = Color(0xEDFFFEFA),
      shadowTint = Color(0xFF18212C)
    ),
    darkPalette = AppThemePalette(
      surface = Color(0xFF15171A),
      surfaceElevated = Color(0xFF272B32),
      cardSurface = Color(0xFF1E2126),
      surfaceVariant = Color(0xFF30353E),
      headerBackground = Color(0xFF1E2126),
      onHeader = Color(0xFFF0F2F5),
      toolbarBackground = Color(0xFF15171A),
      onToolbar = Color(0xFFF0F2F5),
      onToolbarSecondary = Color(0xFF9AA2AE),
      tabSelectedText = Color(0xFFF0F2F5),
      tabUnselectedText = Color(0xFF9AA2AE),
      tabIndicator = Color(0xFF89A5C4),
      accent = Color(0xFF89A5C4),
      onAccent = Color(0xFF15171A),
      tertiary = Color(0xFF273240),
      onTertiary = Color(0xFFB8CCE0),
      textPrimary = Color(0xFFF0F2F5),
      textSecondary = Color(0xFF9AA2AE),
      suraBadgeBackground = Color(0xFF2A251C),
      suraBadgeText = Color(0xFFE2B25C),
      suraBadgeStroke = Color(0xFF4A3F2B),
      divider = Color(0xFF2E333B),
      searchHighlight = Color(0xFFE2B25C),
      isLightToolbar = false,
      glassBg = Color(0xE01E2126),
      glassBorder = Color(0x3889A5C4),
      mushafOverlayBg = Color(0xFF131518),
      navBg = Color(0xF022262C),
      navBorder = Color(0xFF2E333B),
      ctaBackground = Color(0xFF2B3F56),
      onCta = Color(0xFFF2F5F8),
      ayahHighlightFill = Color(0xFFE2B25C).copy(alpha = 0.20f),
      editorialAccent = Color(0xFFE2B25C),
      surfaceTopGlow = Color(0xFF1F2329),
      glassFloatBg = Color(0xED272B32),
      shadowTint = Color(0xFF000000)
    )
  ),

  GREEN(
    storageKey = "green",
    lightPalette = AppThemePalette(
      surface = Color(0xFFEDF2EE),
      surfaceElevated = Color(0xFFFFFFFF),
      cardSurface = Color(0xFFF8FBF8),
      surfaceVariant = Color(0xFFDCE7DF),
      headerBackground = Color(0xFFE3EDE6),
      onHeader = Color(0xFF234E36),
      toolbarBackground = Color(0xFFE3EDE6),
      onToolbar = Color(0xFF1D2A22),
      onToolbarSecondary = Color(0xFF4B5C51),
      tabSelectedText = Color(0xFF234E36),
      tabUnselectedText = Color(0xFF4B5C51),
      tabIndicator = Color(0xFF234E36),
      accent = Color(0xFF234E36),
      onAccent = Color(0xFFF5FAF6),
      tertiary = Color(0xFFD3E2D7),
      onTertiary = Color(0xFF1F4530),
      textPrimary = Color(0xFF1D2A22),
      textSecondary = Color(0xFF506156),
      suraBadgeBackground = Color(0xFFF0ECE1),
      suraBadgeText = Color(0xFF72531C),
      suraBadgeStroke = Color(0xFFD4C3A0),
      divider = Color(0xFFD0DFD4),
      searchHighlight = Color(0xFF72531C),
      isLightToolbar = true,
      glassBg = Color(0xDBF8FBF8),
      glassBorder = Color(0x2E234E36),
      mushafOverlayBg = Color(0xFFF8EBD5),
      navBg = Color(0xF0FAFDFA),
      navBorder = Color(0xFFD0DFD4),
      ctaBackground = Color(0xFF234E36),
      onCta = Color(0xFFF5FAF6),
      ayahHighlightFill = Color(0xFF946F2D).copy(alpha = 0.16f),
      editorialAccent = Color(0xFF946F2D),
      surfaceTopGlow = Color(0xFFF7FBF7),
      glassFloatBg = Color(0xEDFCFEFC),
      shadowTint = Color(0xFF142A1D)
    ),
    darkPalette = AppThemePalette(
      surface = Color(0xFF0F1412),
      surfaceElevated = Color(0xFF202A24),
      cardSurface = Color(0xFF171F1B),
      surfaceVariant = Color(0xFF29352E),
      headerBackground = Color(0xFF171F1B),
      onHeader = Color(0xFFEEF2EF),
      toolbarBackground = Color(0xFF0F1412),
      onToolbar = Color(0xFFEEF2EF),
      onToolbarSecondary = Color(0xFF94A49A),
      tabSelectedText = Color(0xFFEEF2EF),
      tabUnselectedText = Color(0xFF94A49A),
      tabIndicator = Color(0xFF63AD82),
      accent = Color(0xFF63AD82),
      onAccent = Color(0xFF0F1412),
      tertiary = Color(0xFF1E2D24),
      onTertiary = Color(0xFFA8D1B8),
      textPrimary = Color(0xFFEEF2EF),
      textSecondary = Color(0xFF94A49A),
      suraBadgeBackground = Color(0xFF26231B),
      suraBadgeText = Color(0xFFD5B16D),
      suraBadgeStroke = Color(0xFF443C29),
      divider = Color(0xFF28312C),
      searchHighlight = Color(0xFFD5B16D),
      isLightToolbar = false,
      glassBg = Color(0xE0171F1B),
      glassBorder = Color(0x3863AD82),
      mushafOverlayBg = Color(0xFF0E1311),
      navBg = Color(0xF01B241F),
      navBorder = Color(0xFF28312C),
      ctaBackground = Color(0xFF214933),
      onCta = Color(0xFFEFF7F2),
      ayahHighlightFill = Color(0xFFD5B16D).copy(alpha = 0.20f),
      editorialAccent = Color(0xFFD5B16D),
      surfaceTopGlow = Color(0xFF18231D),
      glassFloatBg = Color(0xED202A24),
      shadowTint = Color(0xFF000000)
    )
  ),

  BLUE(
    storageKey = "blue",
    lightPalette = AppThemePalette(
      surface = Color(0xFFECF1F6),
      surfaceElevated = Color(0xFFFFFFFF),
      cardSurface = Color(0xFFF8FBFE),
      surfaceVariant = Color(0xFFDAE4EF),
      headerBackground = Color(0xFFE2EBF3),
      onHeader = Color(0xFF25496C),
      toolbarBackground = Color(0xFFE2EBF3),
      onToolbar = Color(0xFF1D2834),
      onToolbarSecondary = Color(0xFF4A5B6E),
      tabSelectedText = Color(0xFF25496C),
      tabUnselectedText = Color(0xFF4A5B6E),
      tabIndicator = Color(0xFF25496C),
      accent = Color(0xFF25496C),
      onAccent = Color(0xFFF5F9FD),
      tertiary = Color(0xFFD3DFEC),
      onTertiary = Color(0xFF204060),
      textPrimary = Color(0xFF1D2834),
      textSecondary = Color(0xFF4E5F72),
      suraBadgeBackground = Color(0xFFF1EAE1),
      suraBadgeText = Color(0xFF7B4E1E),
      suraBadgeStroke = Color(0xFFD5BFA8),
      divider = Color(0xFFCFDCE9),
      searchHighlight = Color(0xFF7B4E1E),
      isLightToolbar = true,
      glassBg = Color(0xDBF8FBFE),
      glassBorder = Color(0x2E25496C),
      mushafOverlayBg = Color(0xFFF8EBD5),
      navBg = Color(0xF0FAFCFF),
      navBorder = Color(0xFFCFDCE9),
      ctaBackground = Color(0xFF25496C),
      onCta = Color(0xFFF5F9FD),
      ayahHighlightFill = Color(0xFF9E6A33).copy(alpha = 0.16f),
      editorialAccent = Color(0xFF9E6A33),
      surfaceTopGlow = Color(0xFFF7FAFE),
      glassFloatBg = Color(0xEDFCFEFF),
      shadowTint = Color(0xFF16263A)
    ),
    darkPalette = AppThemePalette(
      surface = Color(0xFF0F1319),
      surfaceElevated = Color(0xFF202937),
      cardSurface = Color(0xFF171E28),
      surfaceVariant = Color(0xFF293446),
      headerBackground = Color(0xFF171E28),
      onHeader = Color(0xFFEEF1F6),
      toolbarBackground = Color(0xFF0F1319),
      onToolbar = Color(0xFFEEF1F6),
      onToolbarSecondary = Color(0xFF95A2B4),
      tabSelectedText = Color(0xFFEEF1F6),
      tabUnselectedText = Color(0xFF95A2B4),
      tabIndicator = Color(0xFF6BA0D6),
      accent = Color(0xFF6BA0D6),
      onAccent = Color(0xFF0F1319),
      tertiary = Color(0xFF1D2A3B),
      onTertiary = Color(0xFFACC8E6),
      textPrimary = Color(0xFFEEF1F6),
      textSecondary = Color(0xFF95A2B4),
      suraBadgeBackground = Color(0xFF27221B),
      suraBadgeText = Color(0xFFD8A96B),
      suraBadgeStroke = Color(0xFF463B2A),
      divider = Color(0xFF28303B),
      searchHighlight = Color(0xFFD8A96B),
      isLightToolbar = false,
      glassBg = Color(0xE0171E28),
      glassBorder = Color(0x386BA0D6),
      mushafOverlayBg = Color(0xFF0E1218),
      navBg = Color(0xF01B232F),
      navBorder = Color(0xFF28303B),
      ctaBackground = Color(0xFF24476D),
      onCta = Color(0xFFEDF4FC),
      ayahHighlightFill = Color(0xFFD8A96B).copy(alpha = 0.20f),
      editorialAccent = Color(0xFFD8A96B),
      surfaceTopGlow = Color(0xFF18212E),
      glassFloatBg = Color(0xED202937),
      shadowTint = Color(0xFF000000)
    )
  ),

  BEIGE(
    storageKey = "beige",
    lightPalette = AppThemePalette(
      surface = Color(0xFFF5F0E6),
      surfaceElevated = Color(0xFFFFFDF9),
      cardSurface = Color(0xFFFCF9F3),
      surfaceVariant = Color(0xFFE9E1D2),
      headerBackground = Color(0xFFEDE6D8),
      onHeader = Color(0xFF624022),
      toolbarBackground = Color(0xFFEDE6D8),
      onToolbar = Color(0xFF2B241D),
      onToolbarSecondary = Color(0xFF5A4F42),
      tabSelectedText = Color(0xFF624022),
      tabUnselectedText = Color(0xFF5A4F42),
      tabIndicator = Color(0xFF754C29),
      accent = Color(0xFF754C29),
      onAccent = Color(0xFFFCF9F3),
      tertiary = Color(0xFFE2D7C6),
      onTertiary = Color(0xFF593B1F),
      textPrimary = Color(0xFF2B241D),
      textSecondary = Color(0xFF615548),
      suraBadgeBackground = Color(0xFFE3EBE8),
      suraBadgeText = Color(0xFF295C52),
      suraBadgeStroke = Color(0xFFBDD0C9),
      divider = Color(0xFFE0D7C6),
      searchHighlight = Color(0xFF295C52),
      isLightToolbar = true,
      glassBg = Color(0xDBFCF9F3),
      glassBorder = Color(0x2E754C29),
      mushafOverlayBg = Color(0xFFF8EBD5),
      navBg = Color(0xF0FDFAF5),
      navBorder = Color(0xFFE0D7C6),
      ctaBackground = Color(0xFF754C29),
      onCta = Color(0xFFFCF9F3),
      ayahHighlightFill = Color(0xFF295C52).copy(alpha = 0.16f),
      editorialAccent = Color(0xFF295C52),
      surfaceTopGlow = Color(0xFFFFFBF4),
      glassFloatBg = Color(0xEDFEFCF8),
      shadowTint = Color(0xFF3A2916)
    ),
    darkPalette = AppThemePalette(
      surface = Color(0xFF1B1814),
      surfaceElevated = Color(0xFF302922),
      cardSurface = Color(0xFF25201B),
      surfaceVariant = Color(0xFF3B332A),
      headerBackground = Color(0xFF25201B),
      onHeader = Color(0xFFEFE8DF),
      toolbarBackground = Color(0xFF1B1814),
      onToolbar = Color(0xFFEFE8DF),
      onToolbarSecondary = Color(0xFFAA9F92),
      tabSelectedText = Color(0xFFEFE8DF),
      tabUnselectedText = Color(0xFFAA9F92),
      tabIndicator = Color(0xFFC8AA84),
      accent = Color(0xFFC8AA84),
      onAccent = Color(0xFF1B1814),
      tertiary = Color(0xFF342C24),
      onTertiary = Color(0xFFD4BFA5),
      textPrimary = Color(0xFFEFE8DF),
      textSecondary = Color(0xFFAA9F92),
      suraBadgeBackground = Color(0xFF1E2B27),
      suraBadgeText = Color(0xFF89C4B5),
      suraBadgeStroke = Color(0xFF314842),
      divider = Color(0xFF36312C),
      searchHighlight = Color(0xFF89C4B5),
      isLightToolbar = false,
      glassBg = Color(0xE025201B),
      glassBorder = Color(0x38C8AA84),
      mushafOverlayBg = Color(0xFF181511),
      navBg = Color(0xF02B251F),
      navBorder = Color(0xFF36312C),
      ctaBackground = Color(0xFF4B3826),
      onCta = Color(0xFFF5ECE0),
      ayahHighlightFill = Color(0xFF6EA99A).copy(alpha = 0.20f),
      editorialAccent = Color(0xFF6EA99A),
      surfaceTopGlow = Color(0xFF28221B),
      glassFloatBg = Color(0xED302922),
      shadowTint = Color(0xFF000000)
    )
  ),

  GRAY(
    storageKey = "gray",
    lightPalette = AppThemePalette(
      surface = Color(0xFFECECEB),
      surfaceElevated = Color(0xFFFFFFFF),
      cardSurface = Color(0xFFF8F8F7),
      surfaceVariant = Color(0xFFDCDCD9),
      headerBackground = Color(0xFFE4E4E2),
      onHeader = Color(0xFF34373C),
      toolbarBackground = Color(0xFFE4E4E2),
      onToolbar = Color(0xFF232528),
      onToolbarSecondary = Color(0xFF55585D),
      tabSelectedText = Color(0xFF34373C),
      tabUnselectedText = Color(0xFF55585D),
      tabIndicator = Color(0xFF34373C),
      accent = Color(0xFF34373C),
      onAccent = Color(0xFFF8F8F7),
      tertiary = Color(0xFFD7D7D5),
      onTertiary = Color(0xFF34373C),
      textPrimary = Color(0xFF232528),
      textSecondary = Color(0xFF5E6166),
      suraBadgeBackground = Color(0xFFEFEAE2),
      suraBadgeText = Color(0xFF744E1D),
      suraBadgeStroke = Color(0xFFD4C4AE),
      divider = Color(0xFFD5D5D2),
      searchHighlight = Color(0xFF744E1D),
      isLightToolbar = true,
      glassBg = Color(0xDBF8F8F7),
      glassBorder = Color(0x2E34373C),
      mushafOverlayBg = Color(0xFFF8EBD5),
      navBg = Color(0xF0FAFAF9),
      navBorder = Color(0xFFD5D5D2),
      ctaBackground = Color(0xFF34373C),
      onCta = Color(0xFFF8F8F7),
      ayahHighlightFill = Color(0xFF91652C).copy(alpha = 0.16f),
      editorialAccent = Color(0xFF91652C),
      surfaceTopGlow = Color(0xFFF9F9F8),
      glassFloatBg = Color(0xEDFDFDFC),
      shadowTint = Color(0xFF1C1E22)
    ),
    darkPalette = AppThemePalette(
      surface = Color(0xFF161719),
      surfaceElevated = Color(0xFF2A2D31),
      cardSurface = Color(0xFF202225),
      surfaceVariant = Color(0xFF34373D),
      headerBackground = Color(0xFF202225),
      onHeader = Color(0xFFECEEF0),
      toolbarBackground = Color(0xFF161719),
      onToolbar = Color(0xFFECEEF0),
      onToolbarSecondary = Color(0xFF989CA3),
      tabSelectedText = Color(0xFFECEEF0),
      tabUnselectedText = Color(0xFF989CA3),
      tabIndicator = Color(0xFFB2B7BF),
      accent = Color(0xFFB2B7BF),
      onAccent = Color(0xFF161719),
      tertiary = Color(0xFF2F3238),
      onTertiary = Color(0xFFC6CBD2),
      textPrimary = Color(0xFFECEEF0),
      textSecondary = Color(0xFF989CA3),
      suraBadgeBackground = Color(0xFF2A251E),
      suraBadgeText = Color(0xFFD3AA6B),
      suraBadgeStroke = Color(0xFF473D2D),
      divider = Color(0xFF323438),
      searchHighlight = Color(0xFFD3AA6B),
      isLightToolbar = false,
      glassBg = Color(0xE0202225),
      glassBorder = Color(0x38B2B7BF),
      mushafOverlayBg = Color(0xFF141517),
      navBg = Color(0xF025282C),
      navBorder = Color(0xFF323438),
      ctaBackground = Color(0xFF373B42),
      onCta = Color(0xFFF0F2F4),
      ayahHighlightFill = Color(0xFFD3AA6B).copy(alpha = 0.20f),
      editorialAccent = Color(0xFFD3AA6B),
      surfaceTopGlow = Color(0xFF222428),
      glassFloatBg = Color(0xED2A2D31),
      shadowTint = Color(0xFF000000)
    )
  );

  val accent: QuranAccent
    get() = QuranAccent(
      light = lightPalette.accent,
      dark = darkPalette.accent,
      onLight = lightPalette.onAccent,
      onDark = darkPalette.onAccent
    )

  val oledDarkPalette: AppThemePalette by lazy {
    val oledCard = when (this) {
      DEFAULT -> Color(0xFF0B0C0E)
      GREEN -> Color(0xFF090D0B)
      BLUE -> Color(0xFF090C10)
      BEIGE -> Color(0xFF0D0B09)
      GRAY -> Color(0xFF0B0B0C)
    }
    val oledElevated = when (this) {
      DEFAULT -> Color(0xFF131519)
      GREEN -> Color(0xFF101613)
      BLUE -> Color(0xFF10151C)
      BEIGE -> Color(0xFF161310)
      GRAY -> Color(0xFF131416)
    }
    val oledVariant = when (this) {
      DEFAULT -> Color(0xFF1A1D22)
      GREEN -> Color(0xFF17201B)
      BLUE -> Color(0xFF171E28)
      BEIGE -> Color(0xFF211C17)
      GRAY -> Color(0xFF1B1C1F)
    }
    val oledDivider = Color(0xFF1F2329)
    darkPalette.copy(
      surface = Color(0xFF000000),
      surfaceElevated = oledElevated,
      cardSurface = oledCard,
      surfaceVariant = oledVariant,
      headerBackground = oledCard,
      toolbarBackground = Color(0xFF000000),
      divider = oledDivider,
      glassBg = oledCard.copy(alpha = 0.88f),
      mushafOverlayBg = Color(0xFF000000),
      navBg = Color(0xF0000000),
      navBorder = oledDivider,
      surfaceTopGlow = Color(0xFF000000),
      glassFloatBg = oledElevated.copy(alpha = 0.94f),
      shadowTint = Color(0xFF000000)
    )
  }

  fun palette(useDarkTheme: Boolean): AppThemePalette =
    palette(isDark = useDarkTheme, oled = isOledDarkMode)

  fun palette(isDark: Boolean, oled: Boolean = isOledDarkMode): AppThemePalette {
    return when {
      !isDark -> lightPalette
      oled -> oledDarkPalette
      else -> darkPalette
    }
  }

  fun darkMushafBackground(brightness: Int = 0, oled: Boolean = isOledDarkMode): Color {
    if (oled) return Color(0xFF000000)
    val base = darkPalette.mushafOverlayBg
    val clamped = brightness.coerceIn(0, 255)
    if (clamped == 0) return base
    val fraction = clamped / 255f
    return Color(
      red = base.red + (1f - base.red) * fraction,
      green = base.green + (1f - base.green) * fraction,
      blue = base.blue + (1f - base.blue) * fraction,
      alpha = 1f
    )
  }

  @ColorInt
  fun darkMushafBackgroundArgb(brightness: Int = 0, oled: Boolean = isOledDarkMode): Int =
    darkMushafBackground(brightness, oled).toArgb()

  companion object {
    const val PREF_KEY: String = "appThemeStyle"

    @Volatile
    var isOledDarkMode: Boolean = false
      private set

    @JvmStatic
    fun setOledDarkMode(enabled: Boolean) {
      isOledDarkMode = enabled
    }

    var current: AppThemeStyle by mutableStateOf(DEFAULT)
      private set

    fun select(style: AppThemeStyle) {
      current = style
      QuranAccent.current = style.accent
    }

    @JvmStatic
    fun fromPreference(value: String?): AppThemeStyle {
      if (value.isNullOrBlank()) return DEFAULT
      val normalized = value.trim().lowercase()
      return entries.firstOrNull { it.storageKey == normalized } ?: DEFAULT
    }

    @JvmStatic
    @JvmOverloads
    @ColorInt
    fun resolveDarkMushafBackgroundArgb(
      preferenceValue: String?,
      brightness: Int,
      oled: Boolean = isOledDarkMode
    ): Int {
      return fromPreference(preferenceValue).darkMushafBackgroundArgb(brightness, oled)
    }

    fun fromKey(key: String?): AppThemeStyle = fromPreference(key)

    val calmStyles: List<AppThemeStyle> = listOf(
      GREEN,
      BLUE,
      BEIGE,
      GRAY
    )
  }
}
