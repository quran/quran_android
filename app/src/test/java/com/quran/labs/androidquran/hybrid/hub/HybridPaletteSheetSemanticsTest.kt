package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract.StudyTab
import com.quran.labs.androidquran.hybrid.reader.FrostedAudioCapsule
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.reader.ReaderAppearanceSheet
import com.quran.labs.androidquran.hybrid.reader.ReaderAppearanceState
import com.quran.labs.androidquran.hybrid.reader.StudyDrawerSheet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridPaletteSheetSemanticsTest {

  private fun composeAndMeasure(content: @androidx.compose.runtime.Composable () -> Unit): List<SemanticsNode> {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val composeView = ComposeView(activity)
    composeView.setContent(content)
    activity.setContentView(composeView)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    composeView.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
    )
    composeView.layout(0, 0, 720, 1604)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    val viewRoot = composeView.getChildAt(0) as ViewRootForTest
    return viewRoot.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
  }

  private fun SemanticsNode.mergedLabelText(): String {
    val texts = config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    val descs = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    return (texts + descs).joinToString(" ")
  }

  @Test
  fun `HybridPaletteSheetContent merges clickable and text semantics on done, mode, and style chips`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val doneLabel = context.getString(R.string.hybrid_hub_done)
    val modeLabels = context.resources.getStringArray(R.array.themes).toList()
    val styleLabels = context.resources.getStringArray(R.array.theme_styles).toList()

    val nodes = composeAndMeasure {
      QuranTheme(useDarkTheme = false, style = AppThemeStyle.BEIGE) {
        HybridPaletteSheetContent(
          currentAppTheme = Constants.THEME_LIGHT,
          currentThemeStyle = AppThemeStyle.BEIGE,
          isDarkActive = false,
          onSelectThemeMode = {},
          onSelectThemeStyle = {},
          onDismiss = {}
        )
      }
    }

    val clickableNodes = nodes.filter { it.config.contains(SemanticsActions.OnClick) }

    val doneNode = clickableNodes.firstOrNull { it.mergedLabelText().contains(doneLabel) }
    assertWithMessage("Done button '$doneLabel' must expose merged clickable semantics")
      .that(doneNode).isNotNull()

    for (modeLabel in modeLabels) {
      val modeNode = clickableNodes.firstOrNull { it.mergedLabelText().contains(modeLabel) }
      assertWithMessage("Theme mode chip '$modeLabel' must expose merged clickable + text semantics")
        .that(modeNode).isNotNull()
      assertThat(modeNode!!.config.isMergingSemanticsOfDescendants).isTrue()
    }

    for (style in AppThemeStyle.entries) {
      val styleNode = clickableNodes.firstOrNull {
        it.config.getOrNull(SemanticsProperties.TestTag) == "hub_palette_style_${style.storageKey}"
      }
      assertWithMessage("Style chip '${style.storageKey}' must expose merged clickable semantics")
        .that(styleNode).isNotNull()
      assertThat(styleNode!!.config.isMergingSemanticsOfDescendants).isTrue()
      val idx = AppThemeStyle.entries.indexOf(style)
      assertThat(styleNode.mergedLabelText()).contains(styleLabels[idx])
    }

    // OLED toggle is absent in Light mode
    assertThat(
      clickableNodes.any { it.config.getOrNull(SemanticsProperties.TestTag) == "hub_palette_oled_toggle" }
    ).isFalse()

    // OLED toggle appears with merged switch semantics in Dark mode
    val darkNodes = composeAndMeasure {
      QuranTheme(useDarkTheme = true, style = AppThemeStyle.BEIGE) {
        HybridPaletteSheetContent(
          currentAppTheme = Constants.THEME_DARK,
          currentThemeStyle = AppThemeStyle.BEIGE,
          isDarkActive = true,
          onSelectThemeMode = {},
          onSelectThemeStyle = {},
          onDismiss = {},
          isOledDarkMode = true,
          onToggleOledDarkMode = {}
        )
      }
    }
    val oledNode = darkNodes.firstOrNull {
      it.config.getOrNull(SemanticsProperties.TestTag) == "hub_palette_oled_toggle"
    }
    assertWithMessage("OLED toggle must appear in Dark mode with merged clickable semantics")
      .that(oledNode).isNotNull()
    assertThat(oledNode!!.config.contains(SemanticsActions.OnClick)).isTrue()
    assertThat(oledNode.mergedLabelText()).contains(context.getString(R.string.hybrid_appearance_oled_title))
  }

  @Test
  fun `StudyDrawerSheet merges 2-tab selection and transport controls while ReaderAppearanceSheet merges appearance chips`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val recitationLabel = context.getString(R.string.hybrid_reader_tab_recitation)
    val tafsirLabel = context.getString(R.string.hybrid_reader_tab_tafsir)
    val appearanceLabel = context.getString(R.string.hybrid_reader_tab_appearance)
    val playCd = context.getString(R.string.hybrid_reader_play_cd)
    val repeatLabel = context.getString(R.string.hybrid_reader_repeat)

    val recitationNodes = composeAndMeasure {
      QuranTheme(useDarkTheme = false, style = AppThemeStyle.BEIGE) {
        StudyDrawerSheet(
          uiState = HybridReaderUiState(
            suraNumber = 1,
            suraName = "الفاتحة",
            qariFullName = "المنشاوي",
            selectedStudyTab = StudyTab.RECITATION
          ),
          elapsedMs = 0L,
          progressFraction = 0f,
          onSelectTab = {},
          onCollapse = {},
          onShowQariList = {},
          onPlayPauseClick = {},
          onSkipNext = {},
          onSkipPrev = {},
          onSpeedCycle = {},
          onRepeatCycle = {},
          onTafsirStepAyah = {},
          onSelectTafsirSource = {},
          onOpenTafsirManager = {}
        )
      }
    }

    val clickableRecitation = recitationNodes.filter { it.config.contains(SemanticsActions.OnClick) }
    val tabRecitation = clickableRecitation.firstOrNull { it.mergedLabelText().contains(recitationLabel) }
    val tabTafsir = clickableRecitation.firstOrNull { it.mergedLabelText().contains(tafsirLabel) }
    val tabAppearance = clickableRecitation.firstOrNull { it.mergedLabelText().contains(appearanceLabel) }
    assertThat(tabRecitation?.config?.getOrNull(SemanticsProperties.Selected)).isTrue()
    assertThat(tabTafsir?.config?.getOrNull(SemanticsProperties.Selected)).isFalse()
    assertThat(tabAppearance).isNull()
    assertThat(clickableRecitation.any { it.mergedLabelText().contains(playCd) }).isTrue()
    assertThat(clickableRecitation.any { it.mergedLabelText().contains(repeatLabel) }).isTrue()

    val appearanceNodes = composeAndMeasure {
      QuranTheme(useDarkTheme = false, style = AppThemeStyle.BEIGE) {
        ReaderAppearanceSheet(
          appearance = ReaderAppearanceState(),
          onDismiss = {},
          onSelectAppThemeStyle = {},
          onSelectAppThemeMode = {},
          onSelectPageColor = {},
          onMatchAppThemeChanged = {},
          onNightTextBrightnessChanged = {},
          onNightBgBrightnessChanged = {}
        )
      }
    }
    val clickableAppearance = appearanceNodes.filter { it.config.contains(SemanticsActions.OnClick) }
    assertThat(
      clickableAppearance.any {
        it.mergedLabelText().contains(context.getString(R.string.hybrid_reader_mode_light))
      }
    ).isTrue()
    assertThat(
      clickableAppearance.any {
        it.mergedLabelText().contains(context.getString(R.string.hybrid_reader_palette_beige))
      }
    ).isTrue()
  }

  @Test
  fun `FrostedAudioCapsule merges play and expand button semantics`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val playCd = context.getString(R.string.hybrid_reader_play_cd)
    val expandCd = context.getString(R.string.hybrid_reader_expand_drawer_cd)

    val nodes = composeAndMeasure {
      QuranTheme(useDarkTheme = false, style = AppThemeStyle.BEIGE) {
        FrostedAudioCapsule(
          qariName = "المنشاوي",
          suraName = "الفاتحة",
          activeAyah = SuraAyah(1, 1),
          isPlaying = false,
          isPaused = false,
          isPreparing = false,
          elapsedMs = 0L,
          progressFraction = 0f,
          downloadProgress = null,
          showNetworkPrompt = false,
          onPlayPauseClick = {},
          onExpandStudyDrawer = {},
          onCancelDownload = {},
          onAllowMobileDownload = {}
        )
      }
    }

    val clickable = nodes.filter { it.config.contains(SemanticsActions.OnClick) }
    assertThat(clickable.any { it.mergedLabelText().contains(playCd) }).isTrue()
    assertThat(clickable.any { it.mergedLabelText().contains(expandCd) }).isTrue()
  }
}
