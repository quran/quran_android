package com.quran.labs.androidquran.memorization

import android.content.Context
import android.util.AttributeSet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager
import com.quran.labs.androidquran.memorization.tasmee.TasmeeStage
import com.quran.labs.androidquran.memorization.tasmee.TasmeeUiState
import com.quran.labs.androidquran.memorization.tasmee.VadWaveformVisualizer

class MemorizationBottomBarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

  var onToggleHideText: (() -> Unit)? = null
  var onSelectFullAyahMode: (() -> Unit)? = null
  var onSelectWordByWordMode: (() -> Unit)? = null
  var onSelectWordWindowMode: (() -> Unit)? = null
  var onSelectSingleWordMode: (() -> Unit)? = null
  var onSelectSingleWordTajweedMode: (() -> Unit)? = null
  var onResetPage: (() -> Unit)? = null
  var onToggleTajweedMushaf: (() -> Unit)? = null
  var onToggleLiveTasmee: (() -> Unit)? = null
  var onRetryTasmeeDownload: (() -> Unit)? = null
  var isTajweedMushaf: Boolean by mutableStateOf(false)
  var showInlineTasmeeStrip: Boolean by mutableStateOf(true)

  @Composable
  override fun Content() {
    val state by QuranMemorizationManager.uiState.collectAsState()
    val tasmeeState by LiveTasmeeManager.uiState.collectAsState()
    QuranTheme {
      MemorizationBottomBar(
        state = state,
        tasmeeState = tasmeeState,
        showInlineTasmeeStrip = showInlineTasmeeStrip,
        isTajweedMushaf = isTajweedMushaf,
        onToggleHide = { onToggleHideText?.invoke() },
        onFullAyahClick = { onSelectFullAyahMode?.invoke() },
        onWordByWordClick = { onSelectWordByWordMode?.invoke() },
        onWordWindowClick = { onSelectWordWindowMode?.invoke() },
        onSingleWordClick = { onSelectSingleWordMode?.invoke() },
        onSingleWordTajweedClick = { onSelectSingleWordTajweedMode?.invoke() },
        onResetClick = { onResetPage?.invoke() },
        onToggleTajweedMushaf = { onToggleTajweedMushaf?.invoke() },
        onToggleLiveTasmee = { onToggleLiveTasmee?.invoke() },
        onRetryTasmeeDownload = { onRetryTasmeeDownload?.invoke() }
      )
    }
  }
}

@Composable
fun MemorizationBottomBar(
  state: MemorizationUiState,
  onToggleHide: () -> Unit,
  onFullAyahClick: () -> Unit,
  onWordByWordClick: () -> Unit,
  onResetClick: () -> Unit,
  modifier: Modifier = Modifier,
  onWordWindowClick: () -> Unit = {},
  onSingleWordClick: () -> Unit = {},
  onSingleWordTajweedClick: () -> Unit = {},
  isTajweedMushaf: Boolean = false,
  onToggleTajweedMushaf: () -> Unit = {},
  tasmeeState: TasmeeUiState = TasmeeUiState(),
  showInlineTasmeeStrip: Boolean = true,
  onToggleLiveTasmee: () -> Unit = {},
  onRetryTasmeeDownload: () -> Unit = {}
) {
  val isHidden = state.isHiddenModeEnabled
  val revealMode = state.revealMode
  val progress = state.pageProgress

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("memorization_bottom_bar"),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 2.dp
  ) {
    Column(
      modifier = Modifier.fillMaxWidth()
    ) {
      HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        // Interactive Memorization Mode toggle & Tajweed Mushaf toggle row in the upper permanent setting bar
        val statusText = when {
          !isHidden -> stringResource(R.string.memorization_mode)
          progress.isPageFullyRevealed -> stringResource(R.string.memorization_page_completed)
          revealMode == RevealMode.FULL_AYAH -> stringResource(
            R.string.memorization_tap_hint_ayah,
            progress.revealedAyahs,
            progress.totalAyahs
          )
          revealMode == RevealMode.WORD_WINDOW -> stringResource(
            R.string.memorization_tap_hint_window,
            progress.revealedWords,
            progress.totalWords
          )
          revealMode == RevealMode.SINGLE_WORD -> stringResource(
            R.string.memorization_tap_hint_single_word,
            progress.revealedWords,
            progress.totalWords
          )
          revealMode == RevealMode.SINGLE_WORD_TAJWEED -> stringResource(
            R.string.memorization_tap_hint_single_word_tajweed,
            progress.revealedWords,
            progress.totalWords
          )
          else -> stringResource(
            R.string.memorization_tap_hint_word,
            progress.revealedWords,
            progress.totalWords
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .clickable(
                role = Role.Switch,
                onClick = onToggleHide
              )
              .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .background(
                    color = if (isHidden) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape
                  )
              )
              Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (isHidden) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("memorization_status_text")
              )
            }

            Text(
              text = if (isHidden) "ON" else "OFF",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (isHidden) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              }
            )
          }

          Spacer(modifier = Modifier.width(6.dp))

          // Quick 1-tap toggle for Live Recitation Follow-Along ("Tasmee' / تسميع") in One-Word Mode
          val isTasmeeActive = tasmeeState.isEnabled
          val tasmeeLabel = stringResource(R.string.live_tasmee_short)
          val tasmeeContainer by animateColorAsState(
            targetValue = if (isTasmeeActive) {
              Color(0xFF0D9488)
            } else {
              MaterialTheme.colorScheme.surfaceContainerHighest
            },
            label = "tasmeeChipContainer"
          )
          val tasmeeContent by animateColorAsState(
            targetValue = if (isTasmeeActive) {
              Color.White
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            label = "tasmeeChipContent"
          )
          Surface(
            onClick = onToggleLiveTasmee,
            shape = RoundedCornerShape(8.dp),
            color = tasmeeContainer,
            contentColor = tasmeeContent,
            border = BorderStroke(
              width = 1.dp,
              color = if (isTasmeeActive) {
                Color(0xFF0F766E)
              } else {
                MaterialTheme.colorScheme.outlineVariant
              }
            ),
            modifier = Modifier
              .semantics {
                role = Role.Switch
                selected = isTasmeeActive
                contentDescription = tasmeeLabel
              }
              .testTag("toggle_live_tasmee_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = MemorizationIcons.Microphone,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
              )
              Text(
                text = tasmeeLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isTasmeeActive) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1
              )
              if (isTasmeeActive) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFFA7F3D0), CircleShape)
                )
              }
            }
          }

          Spacer(modifier = Modifier.width(5.dp))

          // Quick toggle for مصحف التجويد (Tajweed Mushaf)
          val tajweedLabel = stringResource(R.string.tajweed_mushaf_short)
          Surface(
            onClick = onToggleTajweedMushaf,
            shape = RoundedCornerShape(8.dp),
            color = if (isTajweedMushaf) {
              MaterialTheme.colorScheme.tertiaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (isTajweedMushaf) {
              MaterialTheme.colorScheme.onTertiaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = BorderStroke(
              width = 1.dp,
              color = if (isTajweedMushaf) {
                MaterialTheme.colorScheme.tertiary
              } else {
                MaterialTheme.colorScheme.outlineVariant
              }
            ),
            modifier = Modifier
              .semantics {
                role = Role.Switch
                selected = isTajweedMushaf
                contentDescription = tajweedLabel
              }
              .testTag("toggle_tajweed_mushaf_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(5.dp)
                    .background(Color(0xFFD32F2F), CircleShape)
                )
                Box(
                  modifier = Modifier
                    .size(5.dp)
                    .background(Color(0xFF2E7D32), CircleShape)
                )
                Box(
                  modifier = Modifier
                    .size(5.dp)
                    .background(Color(0xFF1565C0), CircleShape)
                )
              }
              Text(
                text = tajweedLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isTajweedMushaf) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
              )
            }
          }
        }

        AnimatedVisibility(visible = tasmeeState.isEnabled && showInlineTasmeeStrip) {
          LiveTasmeeStatusStrip(
            tasmeeState = tasmeeState,
            onStopClick = onToggleLiveTasmee,
            onRetryDownloadClick = onRetryTasmeeDownload
          )
        }

        // Controls row: Memorization Mode ON/OFF Toggle + Reveal Full Ayah + Word by Word + Reset
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val hideBtnContainer by animateColorAsState(
            targetValue = if (isHidden) {
              MaterialTheme.colorScheme.errorContainer
            } else {
              MaterialTheme.colorScheme.secondaryContainer
            },
            label = "hideButtonContainer"
          )
          val hideBtnContent by animateColorAsState(
            targetValue = if (isHidden) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.onSecondaryContainer
            },
            label = "hideButtonContent"
          )
          val hideLabel = stringResource(
            if (isHidden) R.string.show_quran_text else R.string.hide_quran_text
          )

          Surface(
            onClick = onToggleHide,
            shape = RoundedCornerShape(10.dp),
            color = hideBtnContainer,
            contentColor = hideBtnContent,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .semantics {
                role = Role.Switch
                selected = isHidden
                contentDescription = hideLabel
              }
              .testTag("hide_quran_text_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = if (isHidden) MemorizationIcons.Visibility else MemorizationIcons.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = hideLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
              )
            }
          }

          // Main Function 1: Reveal Full Ayah
          val isFullAyahActive = isHidden && revealMode == RevealMode.FULL_AYAH
          val fullAyahLabel = stringResource(R.string.reveal_full_ayah)
          val fullAyahBadge = if (isHidden && progress.totalAyahs > 0) {
            "${progress.revealedAyahs}/${progress.totalAyahs}"
          } else null

          ProminentModeButton(
            label = fullAyahLabel,
            badge = fullAyahBadge,
            icon = MemorizationIcons.FullAyah,
            isSelected = isFullAyahActive,
            isHighlighted = revealMode == RevealMode.FULL_AYAH,
            onClick = onFullAyahClick,
            testTag = "reveal_full_ayah_button",
            modifier = Modifier.weight(1f)
          )

          // Main Function 2: Reveal Word by Word
          val isWordByWordActive = isHidden && revealMode == RevealMode.WORD_BY_WORD
          val wordByWordLabel = stringResource(R.string.reveal_word_by_word)
          val wordByWordBadge = if (isHidden && progress.totalWords > 0) {
            "${progress.revealedWords}/${progress.totalWords}"
          } else null

          ProminentModeButton(
            label = wordByWordLabel,
            badge = wordByWordBadge,
            icon = MemorizationIcons.WordByWord,
            isSelected = isWordByWordActive,
            isHighlighted = revealMode == RevealMode.WORD_BY_WORD,
            onClick = onWordByWordClick,
            testTag = "reveal_word_by_word_button",
            modifier = Modifier.weight(1f)
          )

          // Main Function 3: Word Window (last N words of previous ayah + current ayah)
          val isWordWindowActive = isHidden && revealMode == RevealMode.WORD_WINDOW
          val wordWindowLabel = stringResource(R.string.reveal_word_window)

          ProminentModeButton(
            label = wordWindowLabel,
            badge = wordByWordBadge,
            icon = MemorizationIcons.WordWindow,
            isSelected = isWordWindowActive,
            isHighlighted = revealMode == RevealMode.WORD_WINDOW,
            onClick = onWordWindowClick,
            testTag = "reveal_word_window_button",
            modifier = Modifier.weight(1f)
          )

          // Main Function 4: Single Word Only (everything blank except the word pressed)
          val isSingleWordActive = isHidden && revealMode == RevealMode.SINGLE_WORD
          val singleWordLabel = stringResource(R.string.reveal_single_word)

          ProminentModeButton(
            label = singleWordLabel,
            badge = wordByWordBadge,
            icon = MemorizationIcons.SingleWord,
            isSelected = isSingleWordActive,
            isHighlighted = revealMode == RevealMode.SINGLE_WORD,
            onClick = onSingleWordClick,
            testTag = "reveal_single_word_button",
            modifier = Modifier.weight(1f)
          )

          // Main Function 5: Single Word Tajweed (black Madani text -> Tajweed colors on press)
          val isSingleWordTajweedActive = isHidden && revealMode == RevealMode.SINGLE_WORD_TAJWEED
          val singleWordTajweedLabel = stringResource(R.string.reveal_single_word_tajweed)

          ProminentModeButton(
            label = singleWordTajweedLabel,
            badge = wordByWordBadge,
            icon = MemorizationIcons.SingleWord,
            isSelected = isSingleWordTajweedActive,
            isHighlighted = revealMode == RevealMode.SINGLE_WORD_TAJWEED,
            onClick = onSingleWordTajweedClick,
            testTag = "reveal_single_word_tajweed_button",
            modifier = Modifier.weight(1f)
          )

          // Reset / Re-hide All button when in hidden mode and some words are revealed
          AnimatedVisibility(visible = isHidden && progress.revealedWords > 0) {
            val resetDesc = stringResource(R.string.reset_hidden_text)
            FilledIconButton(
              onClick = onResetClick,
              colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
              ),
              modifier = Modifier
                .size(48.dp)
                .testTag("reset_hidden_text_button")
            ) {
              Icon(
                imageVector = MemorizationIcons.Reset,
                contentDescription = resetDesc,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
      )
    }
  }
}

@Composable
private fun LiveTasmeeStatusStrip(
  tasmeeState: TasmeeUiState,
  onStopClick: () -> Unit,
  onRetryDownloadClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = Color(0xFF0D9488).copy(alpha = 0.12f),
    border = BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.38f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("live_tasmee_status_strip")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 5.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          VadWaveformVisualizer(
            isVadActive = tasmeeState.isVadActive,
            micLevel = tasmeeState.micLevel,
            waveformBars = tasmeeState.waveformBars,
            compact = true,
            modifier = Modifier.width(76.dp)
          )

          val activeAyah = tasmeeState.activeAyah
          val primaryStatus = when (tasmeeState.stage) {
            TasmeeStage.DOWNLOADING_MODELS -> {
              val pct = if (tasmeeState.downloadProgress.totalBytes > 0L) {
                ((tasmeeState.downloadProgress.bytesDownloaded * 100L) /
                  tasmeeState.downloadProgress.totalBytes).toInt().coerceIn(0, 100)
              } else 0
              stringResource(R.string.live_tasmee_downloading, pct)
            }
            TasmeeStage.WARMING_UP -> stringResource(R.string.live_tasmee_warming_up)
            TasmeeStage.ERROR -> tasmeeState.errorMessage ?: stringResource(R.string.kids_classroom_download_retry)
            else -> {
              if (activeAyah != null && tasmeeState.totalWordsInAyah > 0) {
                stringResource(
                  R.string.live_tasmee_listening_status,
                  activeAyah.sura,
                  activeAyah.ayah,
                  tasmeeState.revealedWordsInAyah,
                  tasmeeState.totalWordsInAyah
                )
              } else {
                stringResource(R.string.live_tasmee_listening_idle)
              }
            }
          }

          Text(
            text = primaryStatus,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (tasmeeState.skippedWordsCount > 0) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFFD97706).copy(alpha = 0.18f)
            ) {
              Text(
                text = stringResource(R.string.live_tasmee_skipped_badge, tasmeeState.skippedWordsCount),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB45309),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          if (tasmeeState.stage == TasmeeStage.ERROR) {
            Surface(
              onClick = onRetryDownloadClick,
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.testTag("retry_live_tasmee_button")
            ) {
              Text(
                text = stringResource(R.string.kids_classroom_download_retry),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }

          Surface(
            onClick = onStopClick,
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.testTag("stop_live_tasmee_button")
          ) {
            Text(
              text = stringResource(R.string.live_tasmee_stop),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }
      }

      if (tasmeeState.stage == TasmeeStage.DOWNLOADING_MODELS) {
        val fraction = if (tasmeeState.downloadProgress.totalBytes > 0L) {
          (tasmeeState.downloadProgress.bytesDownloaded.toFloat() /
            tasmeeState.downloadProgress.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
        LinearProgressIndicator(
          progress = { fraction },
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp),
          color = Color(0xFF0D9488)
        )
      }
    }
  }
}

@Composable
private fun ProminentModeButton(
  label: String,
  badge: String?,
  icon: ImageVector,
  isSelected: Boolean,
  isHighlighted: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  val containerColor by animateColorAsState(
    targetValue = when {
      isSelected -> MaterialTheme.colorScheme.primary
      isHighlighted -> MaterialTheme.colorScheme.primaryContainer
      else -> MaterialTheme.colorScheme.surfaceContainerHighest
    },
    label = "modeContainerColor"
  )
  val contentColor by animateColorAsState(
    targetValue = when {
      isSelected -> MaterialTheme.colorScheme.onPrimary
      isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    },
    label = "modeContentColor"
  )
  val border = if (isSelected) {
    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
  } else if (isHighlighted) {
    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
  } else {
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = containerColor,
    contentColor = contentColor,
    border = border,
    modifier = modifier
      .heightIn(min = 48.dp)
      .semantics {
        role = Role.Button
        selected = isSelected || isHighlighted
        contentDescription = if (badge != null) "$label ($badge)" else label
      }
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(PaddingValues(horizontal = 8.dp, vertical = 6.dp)),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(5.dp))
      Column(
        horizontalAlignment = Alignment.Start
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp),
          fontWeight = if (isSelected || isHighlighted) FontWeight.Bold else FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (badge != null) {
          Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = contentColor.copy(alpha = 0.85f),
            maxLines = 1
          )
        }
      }
    }
  }
}

private object MemorizationIcons {
  val VisibilityOff: ImageVector by lazy {
    ImageVector.Builder(
      name = "VisibilityOff",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(12f, 7f)
        curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
        curveTo(17f, 12.65f, 16.87f, 13.26f, 16.64f, 13.83f)
        lineTo(19.56f, 16.75f)
        curveTo(21.07f, 15.49f, 22.26f, 13.86f, 22.99f, 12f)
        curveTo(21.26f, 7.61f, 16.99f, 4.5f, 11.99f, 4.5f)
        curveTo(10.59f, 4.5f, 9.25f, 4.75f, 8.01f, 5.2f)
        lineTo(10.17f, 7.36f)
        curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
        close()
        moveTo(2f, 4.27f)
        lineTo(4.28f, 6.55f)
        lineTo(4.74f, 7.01f)
        curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
        curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
        curveTo(13.55f, 19.5f, 15.03f, 19.2f, 16.38f, 18.66f)
        lineTo(16.8f, 19.08f)
        lineTo(19.73f, 22f)
        lineTo(21f, 20.73f)
        lineTo(3.27f, 3f)
        lineTo(2f, 4.27f)
        close()
        moveTo(7.53f, 9.8f)
        lineTo(9.08f, 11.35f)
        curveTo(9.03f, 11.56f, 9f, 11.78f, 9f, 12f)
        curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
        curveTo(12.22f, 15f, 12.44f, 14.97f, 12.65f, 14.92f)
        lineTo(14.2f, 16.47f)
        curveTo(13.53f, 16.8f, 12.79f, 17f, 12f, 17f)
        curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
        curveTo(7f, 11.21f, 7.2f, 10.47f, 7.53f, 9.8f)
        close()
      }
    }.build()
  }

  val Visibility: ImageVector by lazy {
    ImageVector.Builder(
      name = "Visibility",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(12f, 4.5f)
        curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
        curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
        curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
        curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
        close()
        moveTo(12f, 17f)
        curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
        curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
        curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
        curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
        close()
        moveTo(12f, 9f)
        curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
        curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
        curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
        curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
        close()
      }
    }.build()
  }

  val FullAyah: ImageVector by lazy {
    ImageVector.Builder(
      name = "FullAyah",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(4f, 6f)
        horizontalLineTo(20f)
        verticalLineTo(8f)
        horizontalLineTo(4f)
        verticalLineTo(6f)
        close()
        moveTo(4f, 11f)
        horizontalLineTo(20f)
        verticalLineTo(13f)
        horizontalLineTo(4f)
        verticalLineTo(11f)
        close()
        moveTo(9f, 16f)
        horizontalLineTo(20f)
        verticalLineTo(18f)
        horizontalLineTo(9f)
        verticalLineTo(16f)
        close()
        moveTo(5.5f, 15.5f)
        curveTo(6.33f, 15.5f, 7f, 16.17f, 7f, 17f)
        curveTo(7f, 17.83f, 6.33f, 18.5f, 5.5f, 18.5f)
        curveTo(4.67f, 18.5f, 4f, 17.83f, 4f, 17f)
        curveTo(4f, 16.17f, 4.67f, 15.5f, 5.5f, 15.5f)
        close()
      }
    }.build()
  }

  val WordByWord: ImageVector by lazy {
    ImageVector.Builder(
      name = "WordByWord",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(15f, 6f)
        horizontalLineTo(21f)
        verticalLineTo(10f)
        horizontalLineTo(15f)
        verticalLineTo(6f)
        close()
        moveTo(9f, 6f)
        horizontalLineTo(13f)
        verticalLineTo(10f)
        horizontalLineTo(9f)
        verticalLineTo(6f)
        close()
        moveTo(3f, 6f)
        horizontalLineTo(7f)
        verticalLineTo(10f)
        horizontalLineTo(3f)
        verticalLineTo(6f)
        close()
        moveTo(14f, 14f)
        horizontalLineTo(21f)
        verticalLineTo(18f)
        horizontalLineTo(14f)
        verticalLineTo(14f)
        close()
        moveTo(5.5f, 14f)
        curveTo(6.88f, 14f, 8f, 15.12f, 8f, 16.5f)
        curveTo(8f, 17.88f, 6.88f, 19f, 5.5f, 19f)
        curveTo(4.12f, 19f, 3f, 17.88f, 3f, 16.5f)
        curveTo(3f, 15.12f, 4.12f, 14f, 5.5f, 14f)
        close()
      }
    }.build()
  }

  val Reset: ImageVector by lazy {
    ImageVector.Builder(
      name = "Reset",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(17.65f, 6.35f)
        curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
        curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
        curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
        curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
        horizontalLineTo(17.65f)
        curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
        curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
        curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
        curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
        lineTo(13f, 11f)
        horizontalLineTo(20f)
        verticalLineTo(4f)
        lineTo(17.65f, 6.35f)
        close()
      }
    }.build()
  }

  val WordWindow: ImageVector by lazy {
    ImageVector.Builder(
      name = "WordWindow",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(3f, 5f)
        horizontalLineTo(21f)
        verticalLineTo(19f)
        horizontalLineTo(3f)
        verticalLineTo(5f)
        close()
        moveTo(5f, 7f)
        verticalLineTo(17f)
        horizontalLineTo(19f)
        verticalLineTo(7f)
        horizontalLineTo(5f)
        close()
        moveTo(11f, 10f)
        horizontalLineTo(17f)
        verticalLineTo(14f)
        horizontalLineTo(11f)
        verticalLineTo(10f)
        close()
        moveTo(7f, 10f)
        horizontalLineTo(9.5f)
        verticalLineTo(14f)
        horizontalLineTo(7f)
        verticalLineTo(10f)
        close()
      }
    }.build()
  }

  val SingleWord: ImageVector by lazy {
    ImageVector.Builder(
      name = "SingleWord",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(4f, 6f)
        horizontalLineTo(20f)
        verticalLineTo(18f)
        horizontalLineTo(4f)
        verticalLineTo(6f)
        close()
        moveTo(6f, 8f)
        verticalLineTo(16f)
        horizontalLineTo(18f)
        verticalLineTo(8f)
        horizontalLineTo(6f)
        close()
        moveTo(9f, 10.5f)
        horizontalLineTo(15f)
        verticalLineTo(13.5f)
        horizontalLineTo(9f)
        verticalLineTo(10.5f)
        close()
      }
    }.build()
  }

  val Microphone: ImageVector by lazy {
    ImageVector.Builder(
      name = "Microphone",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(12f, 14f)
        curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
        verticalLineTo(5f)
        curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
        curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
        verticalLineTo(11f)
        curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
        close()
        moveTo(17.3f, 11f)
        curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
        curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
        horizontalLineTo(5f)
        curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
        verticalLineTo(21f)
        horizontalLineTo(13f)
        verticalLineTo(17.72f)
        curveTo(16.28f, 17.23f, 19f, 14.41f, 19f, 11f)
        horizontalLineTo(17.3f)
        close()
      }
    }.build()
  }
}
