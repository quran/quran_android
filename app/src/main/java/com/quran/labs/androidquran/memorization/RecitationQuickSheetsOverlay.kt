package com.quran.labs.androidquran.memorization

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import com.quran.labs.androidquran.kidsclassroom.ClassroomStage
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomManager
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomUiState
import com.quran.labs.androidquran.kidsclassroom.KidsDifficultyLevel
import com.quran.labs.androidquran.kidsclassroom.KidsSurahAnalysisStore
import com.quran.labs.androidquran.kidsclassroom.KidsTeacherQari
import com.quran.labs.androidquran.kidsclassroom.RewardsDataStore
import com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager
import kotlinx.coroutines.launch

/**
 * Clean, unified Normal-Mode Material 3 Options sheet consolidating **Live Recite & Reveal
 * (Tasmee')**, **Word Masking & Reveal Modes**, and **Mushaf & Audio Tools**, with a clean
 * gateway card into the dedicated **Full Kids Mode** world (all kids progress, rewards, and
 * classroom controls live exclusively inside Kids Mode).
 */
@OptIn(ExperimentalMaterial3Api::class)
class MemorizationControlsSheetOverlayView(
  context: Context,
  private val isNightPage: Boolean = false,
  private val isTajweedMushaf: Boolean = false,
  private val currentReciterName: String = "",
  private val onOpenKidsMode: () -> Unit = {},
  private val onToggleLiveTasmee: () -> Unit,
  private val onToggleHideText: () -> Unit,
  private val onSelectRevealMode: (RevealMode) -> Unit,
  private val onOpenWordWindowSetup: () -> Unit,
  private val onOpenRepetitionSetup: () -> Unit,
  private val onResetPage: () -> Unit,
  private val onToggleTajweedMushaf: () -> Unit,
  private val onPlayPageAudio: () -> Unit = {},
  private val onSelectReciter: () -> Unit = {},
  private val onOpenFlashcards: () -> Unit = {},
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  init {
    val composeView = ComposeView(context).apply {
      setContent {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val memState by QuranMemorizationManager.uiState.collectAsState()
        val tasmeeState by LiveTasmeeManager.uiState.collectAsState()

        fun dismissThen(action: () -> Unit) {
          scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismissed()
            action()
          }
        }

        QuranTheme(useDarkTheme = isSystemInDarkTheme() || isNightPage) {
          ModalBottomSheet(
            onDismissRequest = onDismissed,
            sheetState = sheetState,
            scrimColor = Color.Black.copy(alpha = 0.36f)
          ) {
            MemorizationControlsSheetContent(
              memState = memState,
              isTasmeeEnabled = tasmeeState.isEnabled,
              isTajweedMushaf = isTajweedMushaf,
              currentReciterName = currentReciterName,
              onOpenKidsMode = { dismissThen(onOpenKidsMode) },
              onToggleLiveTasmee = { dismissThen(onToggleLiveTasmee) },
              onToggleHideText = onToggleHideText,
              onSelectRevealMode = onSelectRevealMode,
              onOpenWordWindowSetup = { dismissThen(onOpenWordWindowSetup) },
              onOpenRepetitionSetup = { dismissThen(onOpenRepetitionSetup) },
              onResetPage = { dismissThen(onResetPage) },
              onToggleTajweedMushaf = { dismissThen(onToggleTajweedMushaf) },
              onPlayPageAudio = { dismissThen(onPlayPageAudio) },
              onSelectReciter = { dismissThen(onSelectReciter) },
              onOpenFlashcards = { dismissThen(onOpenFlashcards) }
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
private fun MemorizationControlsSheetContent(
  memState: MemorizationUiState,
  isTasmeeEnabled: Boolean,
  isTajweedMushaf: Boolean,
  currentReciterName: String,
  onOpenKidsMode: () -> Unit,
  onToggleLiveTasmee: () -> Unit,
  onToggleHideText: () -> Unit,
  onSelectRevealMode: (RevealMode) -> Unit,
  onOpenWordWindowSetup: () -> Unit,
  onOpenRepetitionSetup: () -> Unit,
  onResetPage: () -> Unit,
  onToggleTajweedMushaf: () -> Unit,
  onPlayPageAudio: () -> Unit,
  onSelectReciter: () -> Unit,
  onOpenFlashcards: () -> Unit
) {
  val context = LocalContext.current
  val config = LocalConfiguration.current
  val isArabic = remember(context, config) { MemoryFlashcardManager.isAppInArabicMode(context) }
  val res = remember(context, isArabic) { MemorizationFormatting.localizedResources(context, isArabic) }
  val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp)
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .testTag("memorization_controls_sheet"),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = res.getString(R.string.memorization_options_sheet_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = res.getString(R.string.memorization_options_sheet_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Text(
        text = res.getString(R.string.options_section_voice_modes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
      )

      // 1. Recite & Reveal (Live Tasmee' — Glowing Mic)
      Surface(
        onClick = onToggleLiveTasmee,
        shape = RoundedCornerShape(16.dp),
        color = if (isTasmeeEnabled) {
          Color(0xFF0D9488).copy(alpha = 0.16f)
        } else {
          MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
          width = 1.dp,
          color = if (isTasmeeEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 56.dp)
          .testTag("sheet_toggle_live_tasmee")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = res.getString(R.string.live_tasmee_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = res.getString(R.string.live_tasmee_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = isTasmeeEnabled,
            onCheckedChange = { onToggleLiveTasmee() }
          )
        }
      }

      HorizontalDivider()

      // 2. Hide / Show Quran Text Switch Row + Quick Reset
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 48.dp)
          .testTag("sheet_toggle_hide_text"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = res.getString(
            if (memState.isHiddenModeEnabled) R.string.show_quran_text else R.string.hide_quran_text
          ),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f)
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (memState.isHiddenModeEnabled) {
            OutlinedButton(
              onClick = onResetPage,
              modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
              Text(res.getString(R.string.reset_hidden_text))
            }
          }
          Switch(
            checked = memState.isHiddenModeEnabled,
            onCheckedChange = { onToggleHideText() }
          )
        }
      }

      // 3. Reveal Mode Chips
      Text(
        text = res.getString(R.string.memorization_repeat_mode_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
      )

      val modes = listOf(
        RevealMode.WORD_BY_WORD to res.getString(R.string.memorization_word_by_word),
        RevealMode.SINGLE_WORD to res.getString(R.string.memorization_single_word),
        RevealMode.WORD_WINDOW to res.getString(R.string.reveal_word_window),
        RevealMode.FULL_AYAH to res.getString(R.string.memorization_full_ayah),
        RevealMode.SINGLE_WORD_TAJWEED to res.getString(R.string.memorization_single_word_tajweed)
      )

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        modes.forEach { (mode, label) ->
          FilterChip(
            selected = memState.revealMode == mode,
            onClick = { onSelectRevealMode(mode) },
            label = { Text(label, fontWeight = FontWeight.Medium) },
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
          )
        }
        OutlinedButton(
          onClick = onOpenWordWindowSetup,
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
          Text(MemorizationFormatting.formatWordWindowMenuTitle(context))
        }
      }

      HorizontalDivider()

      // 4. Mushaf & Audio Tools (Normal Mode)
      Text(
        text = res.getString(R.string.options_section_mushaf_tools),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
      )

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = isTajweedMushaf,
          onClick = onToggleTajweedMushaf,
          label = { Text(res.getString(R.string.tajweed_mushaf_short), fontWeight = FontWeight.Medium) },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        )
        OutlinedButton(
          onClick = onOpenRepetitionSetup,
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
          Text(res.getString(R.string.memorization_repeat_range))
        }
        OutlinedButton(
          onClick = onPlayPageAudio,
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
          Text(res.getString(R.string.play_page_audio_menu))
        }
        OutlinedButton(
          onClick = onSelectReciter,
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
          val reciterLabel = if (currentReciterName.isNotBlank()) {
            res.getString(R.string.select_reciter_with_current, currentReciterName)
          } else {
            res.getString(R.string.select_reciter_title)
          }
          Text(reciterLabel)
        }
        OutlinedButton(
          onClick = onOpenFlashcards,
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
          Text(res.getString(R.string.memory_flashcards_menu))
        }
      }

      HorizontalDivider()

      // 5. Clean Gateway Portal into Dedicated Full Kids Mode (all kids features live there)
      Surface(
        onClick = onOpenKidsMode,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F766E).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.45f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sheet_open_kids_mode_portal")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(text = "🏝️", style = MaterialTheme.typography.headlineSmall)
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = res.getString(R.string.kids_mode_portal_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold
            )
            Text(
              text = res.getString(R.string.kids_mode_portal_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Text(
            text = if (isArabic) "←" else "→",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F766E)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

/**
 * Focused Children Quran Classroom options sheet retained for direct Kids lesson controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
class KidsClassroomOptionsOverlayView(
  context: Context,
  private val isNightPage: Boolean = false,
  private val isKidsSessionMode: Boolean = false,
  private val onExitClassroomMode: () -> Unit,
  private val onOpenSurahAnalysis: () -> Unit = {},
  private val onOpenAchievements: () -> Unit = {},
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  init {
    val composeView = ComposeView(context).apply {
      setContent {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val state by KidsClassroomManager.uiState.collectAsState()
        val isArabic = remember(context) { MemoryFlashcardManager.isAppInArabicMode(context) }
        val res = remember(context, isArabic) { MemorizationFormatting.localizedResources(context, isArabic) }
        val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

        QuranTheme(useDarkTheme = isSystemInDarkTheme() || isNightPage) {
          ModalBottomSheet(
            onDismissRequest = onDismissed,
            sheetState = sheetState,
            scrimColor = Color.Black.copy(alpha = 0.38f)
          ) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .widthIn(max = 600.dp)
                  .verticalScroll(rememberScrollState())
                  .navigationBarsPadding()
                  .padding(horizontal = 20.dp, vertical = 12.dp)
                  .testTag("kids_classroom_options_sheet"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
              ) {
                val surahNum = MemoryFlashcardManager.formatNumber(state.currentAyah.sura, isArabic)
                val ayahNum = MemoryFlashcardManager.formatNumber(state.currentAyah.ayah, isArabic)
                val turnNum = MemoryFlashcardManager.formatNumber(state.currentTurnIndex + 1, isArabic)
                val totTurns = MemoryFlashcardManager.formatNumber(state.totalTurns, isArabic)

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text(
                    text = "${res.getString(R.string.kids_classroom_header_title)} · ${state.surahNameAr} ($surahNum:$ayahNum)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "${res.getString(R.string.kids_classroom_turn_badge, turnNum, totTurns)} · ${res.getString(R.string.kids_options_sheet_subtitle)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  OutlinedButton(
                    onClick = { KidsClassroomManager.goToPreviousTurnOrAyah(context, autoPlay = false) },
                    modifier = Modifier
                      .weight(0.8f)
                      .heightIn(min = 48.dp)
                  ) {
                    Text(res.getString(R.string.kids_classroom_prev_turn))
                  }

                  Button(
                    onClick = {
                      scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDismissed()
                        KidsClassroomManager.startReciteOnlyForCurrentTurn(context)
                      }
                    },
                    modifier = Modifier
                      .weight(1.3f)
                      .heightIn(min = 48.dp)
                  ) {
                    Text(res.getString(R.string.kids_classroom_recite_only_btn))
                  }

                  OutlinedButton(
                    onClick = { KidsClassroomManager.advanceToNextTurnOrAyah(context, autoPlay = false) },
                    modifier = Modifier
                      .weight(0.8f)
                      .heightIn(min = 48.dp)
                  ) {
                    Text(res.getString(R.string.kids_classroom_next_turn))
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = res.getString(R.string.kids_classroom_auto_next),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                  )
                  Switch(
                    checked = state.autoNextEnabled,
                    onCheckedChange = { enabled ->
                      KidsClassroomManager.setAutoNextEnabled(context, enabled)
                    }
                  )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                KidsTeacherQariSelectorSection(
                  selectedQari = state.selectedTeacherQari,
                  res = res,
                  onSelectQari = { qari -> KidsClassroomManager.setSelectedTeacherQari(context, qari) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                KidsRepetitionControlsSection(
                  kidsState = state,
                  res = res,
                  isArabic = isArabic,
                  onSetAyahRepeat = { count -> KidsClassroomManager.setAyahRepeatTarget(context, count) },
                  onSetSurahRepeat = { count -> KidsClassroomManager.setSurahRepeatTarget(context, count) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                KidsMasteryAndRevisionControlsSection(
                  kidsState = state,
                  res = res,
                  isArabic = isArabic,
                  onSelectDifficulty = { level -> KidsClassroomManager.setDifficultyLevel(context, level) },
                  onToggleRepeatFromMistake = { enabled ->
                    KidsClassroomManager.setRepeatFromFirstMistakeOnly(context, enabled)
                  },
                  onOpenSurahAnalysis = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                      onDismissed()
                      onOpenSurahAnalysis()
                    }
                  },
                  onOpenAchievements = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                      onDismissed()
                      onOpenAchievements()
                    }
                  }
                )

                Button(
                  onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                      onDismissed()
                      onExitClassroomMode()
                    }
                  },
                  colors = if (isKidsSessionMode) {
                    ButtonDefaults.buttonColors(
                      containerColor = Color(0xFF0F766E),
                      contentColor = Color.White
                    )
                  } else {
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer,
                      contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("kids_sheet_back_to_islands_btn")
                ) {
                  Text(
                    text = if (isKidsSessionMode) {
                      "⛵ ${res.getString(R.string.kids_mode_back_to_islands_btn)}"
                    } else {
                      res.getString(R.string.kids_exit_mode_button)
                    },
                    fontWeight = FontWeight.Bold
                  )
                }

                Spacer(modifier = Modifier.height(6.dp))
              }
            }
          }
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }
}

@Composable
private fun KidsTeacherQariSelectorSection(
  selectedQari: KidsTeacherQari,
  res: android.content.res.Resources,
  onSelectQari: (KidsTeacherQari) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("kids_teacher_qari_selector_section"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = res.getString(R.string.kids_teacher_qari_section_title),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(
        selected = selectedQari == KidsTeacherQari.HUSARY_MUALLIM,
        onClick = { onSelectQari(KidsTeacherQari.HUSARY_MUALLIM) },
        label = { Text(res.getString(R.string.kids_teacher_qari_husary)) },
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 44.dp)
          .testTag("sheet_qari_husary_chip")
      )
      FilterChip(
        selected = selectedQari == KidsTeacherQari.MINSHAWI_TEACHER,
        onClick = { onSelectQari(KidsTeacherQari.MINSHAWI_TEACHER) },
        label = { Text(res.getString(R.string.kids_teacher_qari_minshawi)) },
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 44.dp)
          .testTag("sheet_qari_minshawi_chip")
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KidsRepetitionControlsSection(
  kidsState: KidsClassroomUiState,
  res: android.content.res.Resources,
  isArabic: Boolean,
  onSetAyahRepeat: (Int) -> Unit,
  onSetSurahRepeat: (Int) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("kids_repetition_controls_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      text = res.getString(R.string.kids_repeat_section_title),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold
    )

    KidsRepeatLevelControlRow(
      label = res.getString(R.string.kids_repeat_ayah_label),
      currentCount = kidsState.ayahRepeatTarget,
      maxCount = KidsClassroomManager.MAX_AYAH_REPEAT_TARGET,
      presets = listOf(1, 2, 3, 5, 7, 10),
      res = res,
      isArabic = isArabic,
      onValueChanged = onSetAyahRepeat,
      rowTag = "kids_ayah_repeat_row",
      minusTag = "kids_ayah_repeat_minus",
      plusTag = "kids_ayah_repeat_plus"
    )

    KidsRepeatLevelControlRow(
      label = res.getString(R.string.kids_repeat_surah_label),
      currentCount = kidsState.surahRepeatTarget,
      maxCount = KidsClassroomManager.MAX_SURAH_REPEAT_TARGET,
      presets = listOf(1, 2, 3, 5, 10),
      res = res,
      isArabic = isArabic,
      onValueChanged = onSetSurahRepeat,
      rowTag = "kids_surah_repeat_row",
      minusTag = "kids_surah_repeat_minus",
      plusTag = "kids_surah_repeat_plus"
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KidsRepeatLevelControlRow(
  label: String,
  currentCount: Int,
  maxCount: Int,
  presets: List<Int>,
  res: android.content.res.Resources,
  isArabic: Boolean,
  onValueChanged: (Int) -> Unit,
  rowTag: String,
  minusTag: String,
  plusTag: String
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(rowTag),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f)
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = { onValueChanged((currentCount - 1).coerceAtLeast(1)) },
          enabled = currentCount > 1,
          shape = CircleShape,
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(minusTag)
        ) {
          Text("−", fontWeight = FontWeight.Bold)
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.secondaryContainer
        ) {
          val formatted = res.getString(
            R.string.kids_repeat_times_format,
            MemoryFlashcardManager.formatNumber(currentCount, isArabic)
          )
          Text(
            text = formatted,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
          )
        }

        OutlinedButton(
          onClick = { onValueChanged((currentCount + 1).coerceAtMost(maxCount)) },
          enabled = currentCount < maxCount,
          shape = CircleShape,
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(plusTag)
        ) {
          Text("+", fontWeight = FontWeight.Bold)
        }
      }
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      presets.forEach { preset ->
        FilterChip(
          selected = currentCount == preset,
          onClick = { onValueChanged(preset) },
          label = {
            Text(
              text = res.getString(
                R.string.kids_repeat_times_format,
                MemoryFlashcardManager.formatNumber(preset, isArabic)
              ),
              fontWeight = FontWeight.SemiBold
            )
          },
          modifier = Modifier.defaultMinSize(minHeight = 44.dp)
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KidsMasteryAndRevisionControlsSection(
  kidsState: KidsClassroomUiState,
  res: android.content.res.Resources,
  isArabic: Boolean,
  onSelectDifficulty: (KidsDifficultyLevel) -> Unit,
  onToggleRepeatFromMistake: (Boolean) -> Unit,
  onOpenSurahAnalysis: () -> Unit,
  onOpenAchievements: () -> Unit
) {
  val analysisMap by KidsSurahAnalysisStore.summaries.collectAsState()
  val rewardsState by RewardsDataStore.state.collectAsState()
  val surahMistakeCount = analysisMap[kidsState.currentAyah.sura]?.uniqueDifficultWordCount ?: 0
  val surahBadgeCount = rewardsState.earnedSurahBadgesList.size

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("kids_mastery_revision_controls_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Difficulty / Pass-Score Selector (70%, 80%, 90% Proficient)
    Text(
      text = res.getString(R.string.kids_difficulty_section_title),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold
    )

    FlowRow(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("kids_difficulty_selector"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      val levels = listOf(
        Triple(KidsDifficultyLevel.STARTER_70, res.getString(R.string.kids_difficulty_70), "kids_difficulty_70_chip"),
        Triple(KidsDifficultyLevel.INTERMEDIATE_80, res.getString(R.string.kids_difficulty_80), "kids_difficulty_80_chip"),
        Triple(KidsDifficultyLevel.PROFICIENT_90, res.getString(R.string.kids_difficulty_90_proficient), "kids_difficulty_90_chip")
      )
      levels.forEach { (level, label, tag) ->
        FilterChip(
          selected = kidsState.difficultyLevel == level,
          onClick = { onSelectDifficulty(level) },
          label = { Text(label, fontWeight = FontWeight.SemiBold) },
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(tag)
        )
      }
    }

    // 2. Repeat From First Mistake (Sheikh Only, No Kids Audio)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .testTag("kids_repeat_from_mistake_row"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = res.getString(R.string.kids_repeat_from_mistake_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = res.getString(R.string.kids_repeat_from_mistake_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Switch(
        checked = kidsState.repeatFromFirstMistakeOnly,
        onCheckedChange = onToggleRepeatFromMistake,
        modifier = Modifier.testTag("kids_repeat_from_mistake_switch")
      )
    }

    // 3. Per-Surah Revision Analysis CTA
    OutlinedButton(
      onClick = onOpenSurahAnalysis,
      modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .testTag("kids_open_surah_analysis_btn")
    ) {
      val label = if (surahMistakeCount > 0) {
        val countStr = MemoryFlashcardManager.formatNumber(surahMistakeCount, isArabic)
        "📊 ${res.getString(R.string.kids_surah_analysis_btn)} ($countStr)"
      } else {
        "📊 ${res.getString(R.string.kids_surah_analysis_btn)}"
      }
      Text(text = label, fontWeight = FontWeight.Bold)
    }

    // 4. Profile & Surah Achievements CTA
    OutlinedButton(
      onClick = onOpenAchievements,
      modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .testTag("kids_open_achievements_btn")
    ) {
      val label = if (surahBadgeCount > 0) {
        val countStr = MemoryFlashcardManager.formatNumber(surahBadgeCount, isArabic)
        "🏅 ${res.getString(R.string.kids_achievements_open_btn)} ($countStr)"
      } else {
        "🏅 ${res.getString(R.string.kids_achievements_open_btn)}"
      }
      Text(text = label, fontWeight = FontWeight.Bold)
    }
  }
}
