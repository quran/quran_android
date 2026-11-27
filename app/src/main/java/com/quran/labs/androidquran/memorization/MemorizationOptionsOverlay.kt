package com.quran.labs.androidquran.memorization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import kotlinx.coroutines.launch
import java.util.Locale
import com.quran.mobile.common.ui.core.R as UiCoreR

object MemorizationFormatting {

  fun localizedResources(context: Context, isArabic: Boolean): Resources {
    return try {
      val targetLocale = if (isArabic) Locale("ar") else Locale.ENGLISH
      val config = Configuration(context.resources.configuration)
      config.setLocale(targetLocale)
      config.setLayoutDirection(targetLocale)
      context.createConfigurationContext(config).resources
    } catch (_: Exception) {
      context.resources
    }
  }

  fun formatWordWindowMenuTitle(
    context: Context,
    windowWords: Int = QuranMemorizationManager.getPreviousAyahWindowWords()
  ): String {
    val isArabic = MemoryFlashcardManager.isAppInArabicMode(context)
    val res = localizedResources(context, isArabic)
    return when (windowWords) {
      QuranMemorizationManager.WINDOW_NONE ->
        res.getString(R.string.memorization_word_window_menu_zero)
      QuranMemorizationManager.WINDOW_FULL_AYAH ->
        res.getString(R.string.memorization_word_window_menu_ayah)
      1 -> res.getString(
        R.string.memorization_word_window_menu_one,
        MemoryFlashcardManager.formatNumber(1, isArabic)
      )
      else -> res.getString(
        R.string.memorization_word_window_menu_words,
        MemoryFlashcardManager.formatNumber(windowWords, isArabic)
      )
    }
  }

  fun formatWordWindowOptionLabel(
    res: Resources,
    windowWords: Int,
    isArabic: Boolean
  ): String {
    return when (windowWords) {
      QuranMemorizationManager.WINDOW_NONE -> res.getString(R.string.word_window_option_zero)
      QuranMemorizationManager.WINDOW_FULL_AYAH -> res.getString(R.string.word_window_option_ayah)
      1 -> res.getString(
        R.string.word_window_option_one,
        MemoryFlashcardManager.formatNumber(1, isArabic)
      )
      else -> res.getString(
        R.string.word_window_option_words,
        MemoryFlashcardManager.formatNumber(windowWords, isArabic)
      )
    }
  }

  fun formatActiveRangeMenuTitle(
    context: Context,
    range: MemorizationRange?
  ): String {
    val isArabic = MemoryFlashcardManager.isAppInArabicMode(context)
    val res = localizedResources(context, isArabic)
    if (range == null) {
      return res.getString(R.string.memorization_repeat_range)
    }
    val rangeStr = "${MemoryFlashcardManager.formatNumber(range.startAyah.sura, isArabic)}:${
      MemoryFlashcardManager.formatNumber(range.startAyah.ayah, isArabic)
    }–${MemoryFlashcardManager.formatNumber(range.endAyah.sura, isArabic)}:${
      MemoryFlashcardManager.formatNumber(range.endAyah.ayah, isArabic)
    }"
    val repStr = if (range.targetRepetitions == QuranMemorizationManager.REPEAT_UNLIMITED) {
      res.getString(R.string.memorization_repeat_unlimited_short)
    } else {
      "${MemoryFlashcardManager.formatNumber(range.completedRepetitions, isArabic)}/${
        MemoryFlashcardManager.formatNumber(range.targetRepetitions, isArabic)
      }"
    }
    return res.getString(R.string.memorization_repeat_range_active, rangeStr, repStr)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
class WordWindowSetupOverlayView(
  context: Context,
  private val isNightPage: Boolean = false,
  private val onApplyWindow: (Int) -> Unit,
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  init {
    val composeView = ComposeView(context).apply {
      setContent {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        QuranTheme(useDarkTheme = isSystemInDarkTheme() || isNightPage) {
          ModalBottomSheet(
            onDismissRequest = onDismissed,
            sheetState = sheetState,
            scrimColor = Color.Black.copy(alpha = 0.35f)
          ) {
            WordWindowSetupSheetContent(
              initialWindowWords = QuranMemorizationManager.getPreviousAyahWindowWords(),
              onConfirm = { chosen ->
                onApplyWindow(chosen)
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissed() }
              }
            )
          }
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordWindowSetupSheetContent(
  initialWindowWords: Int,
  onConfirm: (Int) -> Unit
) {
  val context = LocalContext.current
  val config = LocalConfiguration.current
  val isArabic = remember(context, config) {
    MemoryFlashcardManager.isAppInArabicMode(context)
  }
  val res = remember(context, isArabic) {
    MemorizationFormatting.localizedResources(context, isArabic)
  }
  val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

  var selectedWindow by remember { mutableIntStateOf(initialWindowWords) }

  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp)
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .testTag("word_window_setup_sheet"),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = res.getString(R.string.word_window_sheet_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = res.getString(R.string.word_window_sheet_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        QuranMemorizationManager.WORD_WINDOW_OPTIONS.forEach { option ->
          val isSelected = selectedWindow == option
          val label = MemorizationFormatting.formatWordWindowOptionLabel(res, option, isArabic)
          MemorizationOptionChip(
            label = label,
            selected = isSelected,
            onClick = { selectedWindow = option },
            testTag = "word_window_option_$option"
          )
        }
      }

      Button(
        onClick = { onConfirm(selectedWindow) },
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 50.dp)
          .testTag("apply_word_window_button"),
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(
          text = res.getString(R.string.word_window_apply_button),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
class MemorizationRepetitionOverlayView(
  context: Context,
  private val quranInfo: QuranInfo,
  private val currentPage: Int,
  private val initialStartAyah: SuraAyah? = null,
  private val initialEndAyah: SuraAyah? = null,
  private val isNightPage: Boolean = false,
  private val onStartRange: (
    startAyah: SuraAyah,
    endAyah: SuraAyah,
    repetitions: Int,
    revealMode: RevealMode,
    windowWords: Int
  ) -> Unit,
  private val onStopRange: () -> Unit,
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  init {
    val composeView = ComposeView(context).apply {
      setContent {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        QuranTheme(useDarkTheme = isSystemInDarkTheme() || isNightPage) {
          ModalBottomSheet(
            onDismissRequest = onDismissed,
            sheetState = sheetState,
            scrimColor = Color.Black.copy(alpha = 0.35f)
          ) {
            MemorizationRepetitionSheetContent(
              quranInfo = quranInfo,
              currentPage = currentPage,
              initialStartAyah = initialStartAyah,
              initialEndAyah = initialEndAyah,
              activeRange = QuranMemorizationManager.getActiveRange(),
              onStartClick = { start, end, reps, mode, windowWords ->
                onStartRange(start, end, reps, mode, windowWords)
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissed() }
              },
              onStopClick = {
                onStopRange()
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissed() }
              }
            )
          }
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemorizationRepetitionSheetContent(
  quranInfo: QuranInfo,
  currentPage: Int,
  initialStartAyah: SuraAyah?,
  initialEndAyah: SuraAyah?,
  activeRange: MemorizationRange?,
  onStartClick: (SuraAyah, SuraAyah, Int, RevealMode, Int) -> Unit,
  onStopClick: () -> Unit
) {
  val context = LocalContext.current
  val config = LocalConfiguration.current
  val isArabic = remember(context, config) {
    MemoryFlashcardManager.isAppInArabicMode(context)
  }
  val res = remember(context, isArabic) {
    MemorizationFormatting.localizedResources(context, isArabic)
  }
  val suraNames = remember(res, isArabic) {
    res.getStringArray(UiCoreR.array.sura_names)
  }
  val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

  val pageBounds = remember(currentPage) { quranInfo.getPageBounds(currentPage.coerceIn(1, 604)) }
  val defaultStart = initialStartAyah
    ?: activeRange?.startAyah
    ?: SuraAyah(pageBounds[0].coerceIn(1, 114), pageBounds[1].coerceAtLeast(1))
  val defaultEnd = initialEndAyah
    ?: activeRange?.endAyah
    ?: SuraAyah(pageBounds[2].coerceIn(1, 114), pageBounds[3].coerceAtLeast(1))

  var fromSura by remember { mutableIntStateOf(defaultStart.sura.coerceIn(1, 114)) }
  var fromAyah by remember { mutableIntStateOf(defaultStart.ayah.coerceAtLeast(1)) }
  var toSura by remember { mutableIntStateOf(defaultEnd.sura.coerceIn(1, 114)) }
  var toAyah by remember { mutableIntStateOf(defaultEnd.ayah.coerceAtLeast(1)) }

  var selectedRepetitions by remember {
    mutableIntStateOf(activeRange?.targetRepetitions ?: 3)
  }
  var selectedMode by remember {
    mutableStateOf(QuranMemorizationManager.getRevealMode())
  }
  var selectedWindowWords by remember {
    mutableIntStateOf(QuranMemorizationManager.getPreviousAyahWindowWords())
  }

  val startAyahObj = SuraAyah(fromSura, fromAyah)
  val endAyahObj = SuraAyah(toSura, toAyah)
  val (orderedStart, orderedEnd) = if (startAyahObj <= endAyahObj) {
    startAyahObj to endAyahObj
  } else {
    endAyahObj to startAyahObj
  }
  val computedStartPage = quranInfo.getPageFromSuraAyah(orderedStart.sura, orderedStart.ayah)
  val computedEndPage = quranInfo.getPageFromSuraAyah(orderedEnd.sura, orderedEnd.ayah)
  val pagesBadgeText = if (computedStartPage == computedEndPage) {
    res.getString(
      R.string.flashcard_single_page_label,
      MemoryFlashcardManager.formatNumber(computedStartPage, isArabic)
    )
  } else {
    res.getString(
      R.string.flashcard_page_range_label,
      MemoryFlashcardManager.formatNumber(computedStartPage, isArabic),
      MemoryFlashcardManager.formatNumber(computedEndPage, isArabic)
    )
  }

  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .testTag("memorization_repetition_sheet"),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = res.getString(R.string.memorization_repeat_sheet_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.secondaryContainer
        ) {
          Text(
            text = pagesBadgeText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }

      Text(
        text = res.getString(R.string.memorization_repeat_sheet_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // Quick range helpers: Current Ayah, Full Surah, Current Page & + Next Page
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = {
            val target = initialStartAyah
              ?: activeRange?.startAyah
              ?: SuraAyah(fromSura, fromAyah)
            fromSura = target.sura.coerceIn(1, 114)
            fromAyah = target.ayah.coerceAtLeast(1)
            toSura = fromSura
            toAyah = fromAyah
          },
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("quick_current_ayah_range_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text(
            text = res.getString(R.string.memorization_preset_current_ayah),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        OutlinedButton(
          onClick = {
            val targetSura = fromSura.coerceIn(1, 114)
            val maxAyah = quranInfo.getNumberOfAyahs(targetSura).coerceAtLeast(1)
            fromSura = targetSura
            fromAyah = 1
            toSura = targetSura
            toAyah = maxAyah
          },
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("quick_full_surah_range_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text(
            text = res.getString(R.string.memorization_preset_full_surah),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = {
            val bounds = quranInfo.getPageBounds(currentPage.coerceIn(1, 604))
            fromSura = bounds[0]
            fromAyah = bounds[1]
            toSura = bounds[2]
            toAyah = bounds[3]
          },
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("quick_current_page_range_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text(
            text = res.getString(R.string.memorization_repeat_quick_current_page),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (computedEndPage < 604) {
          OutlinedButton(
            onClick = {
              val nextPage = (computedEndPage + 1).coerceIn(1, 604)
              val nextBounds = quranInfo.getPageBounds(nextPage)
              toSura = nextBounds[2]
              toAyah = nextBounds[3]
            },
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("quick_include_next_page_button"),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text(
              text = res.getString(R.string.memorization_repeat_quick_include_next_page),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      // From Sura : Ayah
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        SuraSelectorBox(
          label = res.getString(R.string.flashcard_sura_label),
          selectedSura = fromSura,
          suraNames = suraNames,
          isArabic = isArabic,
          onSuraSelected = { newSura ->
            fromSura = newSura
            val maxAyah = quranInfo.getNumberOfAyahs(newSura).coerceAtLeast(1)
            if (fromAyah > maxAyah) fromAyah = maxAyah
            if (SuraAyah(fromSura, fromAyah) > SuraAyah(toSura, toAyah)) {
              toSura = fromSura
              toAyah = maxAyah
            }
          },
          modifier = Modifier.weight(1.6f),
          testTag = "repeat_from_sura_selector"
        )
        AyahSelectorBox(
          label = res.getString(R.string.flashcard_from_ayah_label),
          selectedAyah = fromAyah,
          maxAyah = quranInfo.getNumberOfAyahs(fromSura).coerceAtLeast(1),
          isArabic = isArabic,
          onAyahSelected = { newAyah ->
            fromAyah = newAyah
            if (fromSura == toSura && toAyah < newAyah) {
              toAyah = newAyah
            }
          },
          modifier = Modifier.weight(1f),
          testTag = "repeat_from_ayah_selector"
        )
      }

      // To Sura : Ayah
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        SuraSelectorBox(
          label = res.getString(R.string.flashcard_to_sura_label),
          selectedSura = toSura,
          suraNames = suraNames,
          isArabic = isArabic,
          onSuraSelected = { newSura ->
            toSura = newSura
            val maxAyah = quranInfo.getNumberOfAyahs(newSura).coerceAtLeast(1)
            if (toAyah > maxAyah) toAyah = maxAyah
          },
          modifier = Modifier.weight(1.6f),
          testTag = "repeat_to_sura_selector"
        )
        AyahSelectorBox(
          label = res.getString(R.string.flashcard_to_ayah_label),
          selectedAyah = toAyah,
          maxAyah = quranInfo.getNumberOfAyahs(toSura).coerceAtLeast(1),
          isArabic = isArabic,
          onAyahSelected = { newAyah -> toAyah = newAyah },
          modifier = Modifier.weight(1f),
          testTag = "repeat_to_ayah_selector"
        )
      }

      Text(
        text = res.getString(R.string.memorization_repeat_tip_highlight),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // Number of Repetitions (1, 2, 3, 5, 10, Unlimited)
      Text(
        text = res.getString(R.string.memorization_repeat_count_label),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        QuranMemorizationManager.REPETITION_OPTIONS.forEach { rep ->
          val label = if (rep == QuranMemorizationManager.REPEAT_UNLIMITED) {
            res.getString(R.string.memorization_repeat_unlimited)
          } else {
            MemoryFlashcardManager.formatNumber(rep, isArabic)
          }
          MemorizationOptionChip(
            label = label,
            selected = selectedRepetitions == rep,
            onClick = { selectedRepetitions = rep },
            testTag = "repeat_count_option_$rep"
          )
        }
      }

      // Reveal Mode Selection + Optional Word Window Size
      Text(
        text = res.getString(R.string.memorization_repeat_mode_label),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        MemorizationOptionChip(
          label = res.getString(R.string.reveal_full_ayah),
          selected = selectedMode == RevealMode.FULL_AYAH,
          onClick = { selectedMode = RevealMode.FULL_AYAH },
          testTag = "repeat_mode_full_ayah"
        )
        MemorizationOptionChip(
          label = res.getString(R.string.reveal_word_by_word),
          selected = selectedMode == RevealMode.WORD_BY_WORD,
          onClick = { selectedMode = RevealMode.WORD_BY_WORD },
          testTag = "repeat_mode_word_by_word"
        )
        MemorizationOptionChip(
          label = res.getString(R.string.reveal_word_window),
          selected = selectedMode == RevealMode.WORD_WINDOW,
          onClick = { selectedMode = RevealMode.WORD_WINDOW },
          testTag = "repeat_mode_word_window"
        )
        MemorizationOptionChip(
          label = res.getString(R.string.memorization_single_word),
          selected = selectedMode == RevealMode.SINGLE_WORD,
          onClick = { selectedMode = RevealMode.SINGLE_WORD },
          testTag = "repeat_mode_single_word"
        )
        MemorizationOptionChip(
          label = res.getString(R.string.memorization_single_word_tajweed),
          selected = selectedMode == RevealMode.SINGLE_WORD_TAJWEED,
          onClick = { selectedMode = RevealMode.SINGLE_WORD_TAJWEED },
          testTag = "repeat_mode_single_word_tajweed"
        )
      }

      if (selectedMode == RevealMode.WORD_WINDOW) {
        Text(
          text = res.getString(R.string.word_window_sheet_title),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          QuranMemorizationManager.WORD_WINDOW_OPTIONS.forEach { option ->
            val label = MemorizationFormatting.formatWordWindowOptionLabel(res, option, isArabic)
            MemorizationOptionChip(
              label = label,
              selected = selectedWindowWords == option,
              onClick = { selectedWindowWords = option },
              testTag = "repeat_word_window_option_$option"
            )
          }
        }
      }

      Button(
        onClick = {
          onStartClick(
            orderedStart,
            orderedEnd,
            selectedRepetitions,
            selectedMode,
            selectedWindowWords
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 50.dp)
          .testTag("start_memorization_repetition_button"),
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(
          text = res.getString(R.string.memorization_repeat_start_button),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
      }

      if (activeRange != null) {
        OutlinedButton(
          onClick = onStopClick,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("stop_memorization_repetition_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        ) {
          Text(
            text = res.getString(R.string.memorization_repeat_stop_button),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun MemorizationOptionChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = if (selected) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.surfaceContainerHigh
    },
    contentColor = if (selected) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSurface
    },
    border = BorderStroke(
      width = if (selected) 1.5.dp else 1.dp,
      color = if (selected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.outlineVariant
      }
    ),
    modifier = Modifier
      .heightIn(min = 48.dp)
      .testTag(testTag)
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
      )
    }
  }
}

@Composable
private fun SuraSelectorBox(
  label: String,
  selectedSura: Int,
  suraNames: Array<String>,
  isArabic: Boolean,
  onSuraSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
  testTag: String
) {
  var expanded by remember { mutableStateOf(false) }
  val safeSura = selectedSura.coerceIn(1, 114)
  val suraTitle = suraNames.getOrNull(safeSura - 1) ?: safeSura.toString()

  Box(modifier = modifier) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clickable { expanded = true }
        .testTag(testTag)
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "${MemoryFlashcardManager.formatNumber(safeSura, isArabic)}. $suraTitle",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.heightIn(max = 320.dp)
    ) {
      for (s in 1..114) {
        val name = suraNames.getOrNull(s - 1) ?: s.toString()
        DropdownMenuItem(
          text = {
            Text("${MemoryFlashcardManager.formatNumber(s, isArabic)}. $name")
          },
          onClick = {
            expanded = false
            onSuraSelected(s)
          }
        )
      }
    }
  }
}

@Composable
private fun AyahSelectorBox(
  label: String,
  selectedAyah: Int,
  maxAyah: Int,
  isArabic: Boolean,
  onAyahSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
  testTag: String
) {
  var expanded by remember { mutableStateOf(false) }
  val safeAyah = selectedAyah.coerceIn(1, maxAyah.coerceAtLeast(1))

  Box(modifier = modifier) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clickable { expanded = true }
        .testTag(testTag)
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = MemoryFlashcardManager.formatNumber(safeAyah, isArabic),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.heightIn(max = 320.dp)
    ) {
      for (a in 1..maxAyah.coerceAtLeast(1)) {
        DropdownMenuItem(
          text = {
            Text(MemoryFlashcardManager.formatNumber(a, isArabic))
          },
          onClick = {
            expanded = false
            onAyahSelected(a)
          }
        )
      }
    }
  }
}
