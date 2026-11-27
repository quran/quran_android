package com.quran.labs.androidquran

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.lightQuranColors
import com.quran.labs.androidquran.common.ui.core.lightSecondaryContainer
import com.quran.labs.androidquran.common.ui.core.lightSurface
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranColorsContrastTest {

  @Test
  fun `lightQuranColors success achieves WCAG AA contrast on lightSurface and lightSecondaryContainer`() {
    val successArgb = lightQuranColors.success.toArgb()
    val surfaceArgb = lightSurface.toArgb()
    val secondaryContainerArgb = lightSecondaryContainer.toArgb()

    val surfaceContrast = ColorUtils.calculateContrast(successArgb, surfaceArgb)
    val secondaryContrast = ColorUtils.calculateContrast(successArgb, secondaryContainerArgb)

    assertThat(surfaceContrast).isAtLeast(4.5)
    assertThat(secondaryContrast).isAtLeast(4.5)
  }

  @Test
  fun `night mode sura_number_color achieves WCAG AA contrast on header background #424242`() {
    val baseContext = ApplicationProvider.getApplicationContext<Context>()
    val nightConfig = Configuration(baseContext.resources.configuration).apply {
      uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
    }
    val nightContext = baseContext.createConfigurationContext(nightConfig)

    val suraNumberColor = ContextCompat.getColor(nightContext, R.color.sura_number_color)
    val headerBackground = ContextCompat.getColor(nightContext, R.color.header_background)
    assertThat(headerBackground).isEqualTo(0xFF424242.toInt())

    val compositedForeground = ColorUtils.compositeColors(suraNumberColor, headerBackground)
    val contrast = ColorUtils.calculateContrast(compositedForeground, headerBackground)

    assertThat(contrast).isAtLeast(4.5)
  }
}
