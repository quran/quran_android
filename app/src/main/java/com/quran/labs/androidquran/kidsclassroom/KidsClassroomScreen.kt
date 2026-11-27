package com.quran.labs.androidquran.kidsclassroom

import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import com.quran.labs.androidquran.memorization.tasmee.VadWaveformVisualizer
import kotlin.math.roundToInt

private val EmeraldPrimary = Color(0xFF0F766E)
private val EmeraldBright = Color(0xFF10B981)
private val EmeraldContainer = Color(0xFFD1FAE5)
private val EmeraldDarkText = Color(0xFF065F46)

private val AmberPrimary = Color(0xFFD97706)
private val AmberContainer = Color(0xFFFEF3C7)
private val AmberDarkText = Color(0xFF92400E)

private val RedWordError = Color(0xFFDC2626)
private val RedWordContainer = Color(0xFFFEE2E2)
private val DarkRedMissing = Color(0xFF991B1B)
private val DarkRedMissingContainer = Color(0xFFFECACA)

private val TasmeeTargetBlue = Color(0xFF0284C7)
private val TasmeePlaceholderFill = Color(0x1464748B)
private val TasmeePlaceholderStroke = Color(0x3894A3B8)
private val ClassroomEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KidsClassroomScreen(
  state: KidsClassroomUiState,
  surahs: List<KidsSurahInfo>,
  onSelectMode: (KidsPracticeMode) -> Unit,
  onSelectSurahAyah: (SuraAyah) -> Unit,
  onStartModeALoop: () -> Unit,
  onStartModeAReciteOnly: () -> Unit,
  onFinishChildSpeakingEarly: () -> Unit,
  onStopLoop: () -> Unit,
  onStartModeBStreaming: () -> Unit,
  onRevealHintModeB: () -> Unit,
  onResetModeBAyah: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToggleAutoNext: (Boolean) -> Unit,
  onRetryDownload: () -> Unit,
  onOpenInTajweedMushaf: (SuraAyah) -> Unit,
  avatarState: TeacherAvatarState = TeacherAvatarState(),
  audioClueState: AudioTajweedClueSample = AudioTajweedClueSample(),
  tajweedAnalysis: TajweedAyahAnalysis = remember(state.currentTurn, state.ayahWords) {
    val words = state.currentTurn?.uthmaniWords?.takeIf { it.isNotEmpty() } ?: state.ayahWords
    TajweedSpanAdapter.analyzeWords(words)
  },
  rewards: KidsRewardsState = KidsRewardsState(),
  focusedSlowMoWordIndex: Int? = null,
  avatarRenderer: TeacherAvatarRenderer = ComposeTeacherAvatar,
  onToggleSlowMo: () -> Unit = {},
  onToggleAudioCluesMode: () -> Unit = {},
  onSelectTeacherQari: (KidsTeacherQari) -> Unit = {},
  onTapWordForSlowMo: (Int) -> Unit = {},
  onOpenSurahMap: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isArabic = remember(context) { MemoryFlashcardManager.isAppInArabicMode(context) }

  val uthmaniFontFamily = remember(context) {
    try {
      FontFamily(Typeface.createFromAsset(context.assets, "uthmanic_hafs_ver12.otf"))
    } catch (_: Throwable) {
      try {
        FontFamily(Typeface.createFromAsset(context.assets, "kitab.ttf"))
      } catch (_: Throwable) {
        FontFamily.Serif
      }
    }
  }

  var showSurahPicker by rememberSaveable { mutableStateOf(false) }
  var showAyahPicker by rememberSaveable { mutableStateOf(false) }

  val currentSurahInfo = remember(surahs, state.currentAyah.sura) {
    surahs.firstOrNull { it.surahNumber == state.currentAyah.sura }
      ?: KidsSurahInfo(
        surahNumber = state.currentAyah.sura,
        nameAr = state.surahNameAr,
        ayahCount = maxOf(state.currentAyah.ayah, 7),
        isLoadedOffline = state.isFullQuranLoaded
      )
  }

  val isModeB = state.practiceMode == KidsPracticeMode.RECITE_AND_REVEAL
  val isLoopActive = state.stage == ClassroomStage.QARI_RECITING ||
    state.stage == ClassroomStage.KIDS_REPEATING ||
    state.stage == ClassroomStage.CHILD_RECITING ||
    state.stage == ClassroomStage.SCORING ||
    state.stage == ClassroomStage.TASMEE_STREAMING

  // Calm focus dimming for secondary setup controls while a learning/recording session is active
  val secondarySetupAlpha by animateFloatAsState(
    targetValue = if (isLoopActive) 0.48f else 1.0f,
    animationSpec = tween(durationMillis = if (isLoopActive) 200 else 160, easing = ClassroomEaseOut),
    label = "classroomSecondaryAlpha"
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(Color(0xFFFFFBF2), Color(0xFFF0FDF4), Color(0xFFF8FAFC))
        )
      )
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .navigationBarsPadding()
      .testTag("kids_classroom_screen"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Column(
      modifier = Modifier.widthIn(max = 620.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // 1. Adventure Rewards Header + Surah Map shortcut
      AdventureHeaderRow(
        rewards = rewards,
        isStreamingReady = state.isStreamingAsrReady,
        onOpenSurahMap = onOpenSurahMap
      )

      // 2. Mode Segmented Toggle (Mode A: Teacher & Kids Repeat | Mode B: Recite & Reveal)
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier
          .fillMaxWidth()
          .alpha(secondarySetupAlpha)
          .testTag("kids_mode_segmented_toggle")
      ) {
        SegmentedButton(
          selected = !isModeB,
          onClick = { onSelectMode(KidsPracticeMode.LISTEN_AND_REPEAT) },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
          colors = SegmentedButtonDefaults.colors(
            activeContainerColor = EmeraldPrimary,
            activeContentColor = Color.White
          ),
          modifier = Modifier.testTag("kids_mode_teacher_repeat_btn")
        ) {
          Text(
            text = stringResource(R.string.kids_mode_teacher_repeat),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        SegmentedButton(
          selected = isModeB,
          onClick = { onSelectMode(KidsPracticeMode.RECITE_AND_REVEAL) },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
          colors = SegmentedButtonDefaults.colors(
            activeContainerColor = TasmeeTargetBlue,
            activeContentColor = Color.White
          ),
          modifier = Modifier.testTag("kids_mode_recite_reveal_btn")
        ) {
          Text(
            text = stringResource(R.string.kids_mode_recite_reveal),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // 3. Offline & Streaming Asset Download Card (auto-hides once ready)
      if (!state.downloadProgress.isReady || state.downloadProgress.isDownloading || state.downloadProgress.errorMessage != null) {
        AssetDownloadBannerCard(
          progress = state.downloadProgress,
          onRetryDownload = onRetryDownload
        )
      }

      // 4. Surah & Ayah Selector Row + Open in Tajweed Mushaf
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .alpha(secondarySetupAlpha),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = { showSurahPicker = true },
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.2.dp, EmeraldPrimary.copy(alpha = 0.45f)),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1.25f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("kids_surah_picker_btn")
        ) {
          Text(
            text = stringResource(
              R.string.kids_surah_picker_label,
              currentSurahInfo.surahNumber.toString(),
              currentSurahInfo.nameAr
            ),
            color = EmeraldDarkText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        OutlinedButton(
          onClick = { showAyahPicker = true },
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.2.dp, EmeraldPrimary.copy(alpha = 0.45f)),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(0.75f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("kids_ayah_picker_btn")
        ) {
          Text(
            text = stringResource(
              R.string.kids_ayah_picker_label,
              state.currentAyah.ayah.toString(),
              currentSurahInfo.ayahCount.toString()
            ),
            color = EmeraldDarkText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = EmeraldContainer,
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpenInTajweedMushaf(state.currentAyah) }
            .testTag("kids_open_mushaf_btn")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "📖",
              fontSize = 20.sp
            )
          }
        }
      }

      // 5. HERO: Ustadh Noor Tajweed-Driven Lip-Sync Teacher Card + "Watch Mouth Slowly (0.75×)" Toggle
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(
          width = 1.5.dp,
          color = if (avatarState.isSlowMo) AmberPrimary else EmeraldPrimary.copy(alpha = 0.22f)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("kids_teacher_hero_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Title row + Turn badge
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = if (state.useAudioCluesMode) {
                  if (state.selectedTeacherQari == KidsTeacherQari.MINSHAWI_TEACHER) {
                    stringResource(R.string.kids_minshawi_audio_title_minshawi)
                  } else {
                    stringResource(R.string.kids_minshawi_audio_title)
                  }
                } else {
                  stringResource(R.string.kids_teacher_name)
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = EmeraldDarkText
              )
              if (state.totalTurns > 1 && !isModeB) {
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = EmeraldContainer
                ) {
                  Text(
                    text = stringResource(
                      R.string.kids_classroom_turn_badge,
                      (state.currentTurnIndex + 1).toString(),
                      state.totalTurns.toString()
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldDarkText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }

          // Teacher Reciter Switcher: Al-Husary (Muallim) default vs Al-Minshawi (Kids)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .alpha(secondarySetupAlpha)
              .padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(
              selected = state.selectedTeacherQari == KidsTeacherQari.HUSARY_MUALLIM,
              onClick = { onSelectTeacherQari(KidsTeacherQari.HUSARY_MUALLIM) },
              label = {
                Text(
                  text = stringResource(R.string.kids_teacher_qari_husary),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EmeraldContainer,
                selectedLabelColor = EmeraldDarkText
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("kids_qari_husary_chip")
            )

            FilterChip(
              selected = state.selectedTeacherQari == KidsTeacherQari.MINSHAWI_TEACHER,
              onClick = { onSelectTeacherQari(KidsTeacherQari.MINSHAWI_TEACHER) },
              label = {
                Text(
                  text = stringResource(R.string.kids_teacher_qari_minshawi),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EmeraldContainer,
                selectedLabelColor = EmeraldDarkText
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("kids_qari_minshawi_chip")
            )
          }

          // Compact control row: Toggle between Ustadh Noor figure vs. Audio Clues + Slow-Mo (0.75×)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(
              selected = state.useAudioCluesMode,
              onClick = onToggleAudioCluesMode,
              label = {
                Text(
                  text = if (state.useAudioCluesMode) {
                    stringResource(R.string.kids_audio_clues_active)
                  } else {
                    stringResource(R.string.kids_audio_clues_enable)
                  },
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EmeraldContainer,
                selectedLabelColor = EmeraldDarkText,
                selectedLeadingIconColor = EmeraldDarkText
              ),
              modifier = Modifier.testTag("kids_audio_clues_toggle_btn")
            )

            FilterChip(
              selected = state.isSlowMoEnabled || focusedSlowMoWordIndex != null,
              onClick = onToggleSlowMo,
              label = {
                Text(
                  text = if (state.isSlowMoEnabled) {
                    stringResource(R.string.kids_slow_mo_active)
                  } else {
                    stringResource(R.string.kids_slow_mo_toggle)
                  },
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AmberContainer,
                selectedLabelColor = AmberDarkText,
                selectedLeadingIconColor = AmberDarkText
              ),
              modifier = Modifier.testTag("kids_slow_mo_toggle_btn")
            )
          }

          // Replace Ustadh Noor figure completely with Sheikh Al-Minshawi Audio-Driven Clues when enabled
          if (state.useAudioCluesMode) {
            MinshawiAudioCluesCanvas(
              state = audioClueState,
              modifier = Modifier.fillMaxWidth()
            )
          } else {
            avatarRenderer.Render(
              state = avatarState,
              modifier = Modifier.fillMaxWidth()
            )
          }

          Spacer(Modifier.height(6.dp))

          // Stage status banner pill
          StageStatusBanner(state = state, isModeB = isModeB)

          // Live microphone VAD waveform during Stage 3 or Mode B
          AnimatedVisibility(
            visible = state.stage == ClassroomStage.CHILD_RECITING ||
              state.stage == ClassroomStage.TASMEE_STREAMING,
            enter = fadeIn(tween(200, easing = ClassroomEaseOut)) +
              expandVertically(tween(200, easing = ClassroomEaseOut)),
            exit = fadeOut(tween(150, easing = ClassroomEaseOut)) +
              shrinkVertically(tween(150, easing = ClassroomEaseOut))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              VadWaveformVisualizer(
                waveformBars = state.waveformBars,
                micLevel = state.micLevelRms,
                isVadActive = state.isVadActive,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(32.dp)
              )
              if (state.streamingPartialTranscript.isNotBlank() && !isModeB) {
                Text(
                  text = state.streamingPartialTranscript,
                  fontSize = 12.sp,
                  color = EmeraldDarkText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(top = 4.dp)
                )
              }
            }
          }
        }
      }

      // 6. Color-Coded Tajweed Ayah Card (1-to-1 colors matching Ustadh Noor's visual cues!)
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.2.dp, Color(0xFFE7DFC8)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("kids_tajweed_ayah_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Mode A step indicator (2-step for Husary Muallim, 3-step for Minshawi with Kids)
          if (!isModeB) {
            ThreeStepProgressBar(
              stage = state.stage,
              hasChildrenRepeat = state.selectedTeacherQari.hasChildrenRepeat
            )
          }

          // RTL Tajweed Color-Coded Words FlowRow
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              if (isModeB) {
                val allWords = state.ayahWords
                val fullAnalysis = remember(allWords) { TajweedSpanAdapter.analyzeWords(allWords) }
                fullAnalysis.words.forEachIndexed { idx, wordAnalysis ->
                  val revealed = idx < state.revealedCount
                  val isFrontier = idx == state.revealedCount
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                      revealed -> EmeraldContainer.copy(alpha = 0.45f)
                      isFrontier -> TasmeeTargetBlue.copy(alpha = 0.10f)
                      else -> TasmeePlaceholderFill
                    },
                    border = BorderStroke(
                      width = if (isFrontier) 1.5.dp else 1.dp,
                      color = if (isFrontier) TasmeeTargetBlue else TasmeePlaceholderStroke
                    ),
                    modifier = Modifier.testTag("tasmee_word_$idx")
                  ) {
                    Text(
                      text = TajweedSpanAdapter.buildAnnotatedWord(wordAnalysis),
                      fontFamily = uthmaniFontFamily,
                      fontSize = 27.sp,
                      lineHeight = 44.sp,
                      modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .alpha(if (revealed) 1f else 0f)
                    )
                  }
                }
              } else {
                val turn = state.currentTurn
                val turnWordStart = turn?.wordStartIdx ?: 0
                tajweedAnalysis.words.forEachIndexed { wordIdxInTurn, wordAnalysis ->
                  val mushafPos = turnWordStart + wordIdxInTurn + 1
                  val grade = state.wordGradesByMushafPos[mushafPos]
                  val isActivePlayback = state.activePlaybackWordPosition == mushafPos ||
                    focusedSlowMoWordIndex == wordIdxInTurn
                  val isLiveStreamMatched = state.stage == ClassroomStage.CHILD_RECITING &&
                    wordIdxInTurn < state.streamingMatchedWordCount

                  val bgColor by animateColorAsState(
                    targetValue = when {
                      isActivePlayback -> Color(0xFFFEF08A)
                      isLiveStreamMatched -> EmeraldContainer
                      grade?.status == WordGradeStatus.CORRECT -> EmeraldContainer.copy(alpha = 0.65f)
                      grade?.status == WordGradeStatus.TASHKEEL_ERROR -> AmberContainer
                      grade?.status == WordGradeStatus.WORD_ERROR -> RedWordContainer
                      grade?.status == WordGradeStatus.MISSING -> DarkRedMissingContainer
                      else -> Color.Transparent
                    },
                    label = "wordBg"
                  )

                  val borderColor = when {
                    isActivePlayback -> AmberPrimary
                    isLiveStreamMatched -> EmeraldBright
                    grade?.status == WordGradeStatus.WORD_ERROR -> RedWordError
                    grade?.status == WordGradeStatus.MISSING -> DarkRedMissing
                    grade?.status == WordGradeStatus.TASHKEEL_ERROR -> AmberPrimary
                    else -> Color.Transparent
                  }

                  val badgeText = when {
                    isLiveStreamMatched -> "✨"
                    grade?.status == WordGradeStatus.CORRECT -> stringResource(R.string.kids_classroom_badge_correct)
                    grade?.status == WordGradeStatus.TASHKEEL_ERROR -> stringResource(R.string.kids_classroom_badge_tashkeel)
                    grade?.status == WordGradeStatus.WORD_ERROR -> stringResource(R.string.kids_classroom_badge_word_error)
                    grade?.status == WordGradeStatus.MISSING -> stringResource(R.string.kids_classroom_badge_missing)
                    else -> null
                  }

                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                      .clip(RoundedCornerShape(14.dp))
                      .background(bgColor)
                      .border(
                        width = if (borderColor != Color.Transparent) 1.5.dp else 0.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(14.dp)
                      )
                      .clickable { onTapWordForSlowMo(wordIdxInTurn) }
                      .padding(horizontal = 8.dp, vertical = 4.dp)
                      .testTag("tajweed_word_$wordIdxInTurn")
                  ) {
                    Text(
                      text = TajweedSpanAdapter.buildAnnotatedWord(wordAnalysis),
                      fontFamily = uthmaniFontFamily,
                      fontSize = 29.sp,
                      lineHeight = 46.sp,
                      textAlign = TextAlign.Center
                    )
                    if (badgeText != null) {
                      Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                          isLiveStreamMatched || grade?.status == WordGradeStatus.CORRECT -> EmeraldDarkText
                          grade?.status == WordGradeStatus.TASHKEEL_ERROR -> AmberDarkText
                          else -> DarkRedMissing
                        }
                      )
                    }
                  }
                }
              }
            }
          }

          // Helpful hint telling kids they can tap any word for 0.75x Slow-Mo mouth demo
          if (!isModeB) {
            Text(
              text = stringResource(R.string.kids_tap_word_slow_mo_hint),
              fontSize = 11.sp,
              color = Color(0xFF64748B),
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
          }

          // Compact Color-Matched Tajweed Legend
          TajweedColorLegendRow()
        }
      }

      // 7. Primary Action Controls (48dp+ kid-friendly buttons)
      if (isModeB) {
        ModeBControlsRow(
          isStreaming = state.stage == ClassroomStage.TASMEE_STREAMING,
          onStart = onStartModeBStreaming,
          onStop = onStopLoop,
          onHint = onRevealHintModeB,
          onReset = onResetModeBAyah
        )
      } else {
        ModeAControlsRow(
          state = state,
          isLoopActive = isLoopActive,
          onStartLoop = onStartModeALoop,
          onReciteOnly = onStartModeAReciteOnly,
          onFinishSpeaking = onFinishChildSpeakingEarly,
          onStopLoop = onStopLoop
        )
      }

      // 8. Navigation Bar (Prev | Auto-Next | Next)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .alpha(secondarySetupAlpha),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = onPrevious,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("kids_prev_turn_btn")
        ) {
          Text(
            text = stringResource(R.string.kids_classroom_prev_turn),
            fontWeight = FontWeight.Bold
          )
        }

        FilterChip(
          selected = state.autoNextEnabled,
          onClick = { onToggleAutoNext(!state.autoNextEnabled) },
          label = {
            Text(
              text = stringResource(R.string.kids_classroom_auto_next),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EmeraldContainer,
            selectedLabelColor = EmeraldDarkText
          ),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("kids_auto_next_chip")
        )

        OutlinedButton(
          onClick = onNext,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("kids_next_turn_btn")
        ) {
          Text(
            text = stringResource(R.string.kids_classroom_next_turn),
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }

  // Surah Picker Dialog
  if (showSurahPicker) {
    SurahPickerDialog(
      surahs = surahs,
      selectedSurah = state.currentAyah.sura,
      isArabic = isArabic,
      onSelect = { surahNum ->
        showSurahPicker = false
        onSelectSurahAyah(SuraAyah(surahNum, 1))
      },
      onDismiss = { showSurahPicker = false }
    )
  }

  // Ayah Picker Dialog
  if (showAyahPicker) {
    AyahPickerDialog(
      surahInfo = currentSurahInfo,
      selectedAyah = state.currentAyah.ayah,
      onSelect = { ayahNum ->
        showAyahPicker = false
        onSelectSurahAyah(SuraAyah(currentSurahInfo.surahNumber, ayahNum))
      },
      onDismiss = { showAyahPicker = false }
    )
  }
}

@Composable
private fun AdventureHeaderRow(
  rewards: KidsRewardsState,
  isStreamingReady: Boolean,
  onOpenSurahMap: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFFFF8E1),
        border = BorderStroke(1.dp, Color(0xFFF59E0B))
      ) {
        Text(
          text = "⭐ ${rewards.totalStars}",
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold,
          color = Color(0xFFB45309),
          modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("kids_total_stars_pill")
        )
      }
      if (rewards.streakDays > 0) {
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = Color(0xFFFFEDD5),
          border = BorderStroke(1.dp, Color(0xFFF97316))
        ) {
          Text(
            text = "🔥 ${rewards.streakDays}",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFC2410C),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }
      if (isStreamingReady) {
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = EmeraldContainer
        ) {
          Text(
            text = stringResource(R.string.kids_live_streaming_badge),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldDarkText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    }

    Button(
      onClick = onOpenSurahMap,
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      modifier = Modifier
        .defaultMinSize(minHeight = 48.dp)
        .testTag("kids_open_surah_map_btn")
    ) {
      Text(
        text = "🗺️ " + stringResource(R.string.kids_surah_map_btn),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun StageStatusBanner(state: KidsClassroomUiState, isModeB: Boolean) {
  val eval = state.lastEvaluation
  val scoreStr = eval?.scorePercent?.roundToInt()?.toString() ?: "0"
  val text = state.statusMessageOverride ?: when {
    isModeB && state.stage == ClassroomStage.TASMEE_STREAMING ->
      stringResource(R.string.kids_tasmee_streaming_active, state.revealedCount.toString(), state.ayahWords.size.toString())
    isModeB ->
      stringResource(R.string.kids_tasmee_streaming_idle, state.revealedCount.toString(), state.ayahWords.size.toString())
    state.stage == ClassroomStage.QARI_RECITING ->
      if (state.selectedTeacherQari == KidsTeacherQari.MINSHAWI_TEACHER) {
        stringResource(R.string.kids_classroom_stage_qari_minshawi)
      } else {
        stringResource(R.string.kids_classroom_stage_qari)
      }
    state.stage == ClassroomStage.KIDS_REPEATING -> stringResource(R.string.kids_classroom_stage_kids)
    state.stage == ClassroomStage.CHILD_RECITING ->
      if (state.isRetryAttempt) stringResource(R.string.kids_classroom_stage_child_repeat)
      else stringResource(R.string.kids_classroom_stage_child)
    state.stage == ClassroomStage.SCORING -> stringResource(R.string.kids_classroom_stage_scoring)
    state.stage == ClassroomStage.TURN_PASSED ->
      "${"⭐".repeat(state.lastStarsEarned.coerceIn(1, 3))} " + stringResource(R.string.kids_classroom_stage_passed, scoreStr)
    state.stage == ClassroomStage.TURN_RETRY ->
      if (eval?.noSpeechDetected == true) stringResource(R.string.kids_classroom_no_speech_hint)
      else stringResource(R.string.kids_classroom_stage_retry, scoreStr)
    else -> stringResource(R.string.kids_classroom_stage_ready)
  }

  val bg by animateColorAsState(
    targetValue = when (state.stage) {
      ClassroomStage.TURN_PASSED -> EmeraldContainer
      ClassroomStage.TURN_RETRY -> AmberContainer
      ClassroomStage.CHILD_RECITING, ClassroomStage.TASMEE_STREAMING -> Color(0xFFE0F2FE)
      ClassroomStage.QARI_RECITING -> Color(0xFFCCFBF1)
      else -> Color(0xFFF1F5F9)
    },
    animationSpec = tween(durationMillis = 190, easing = ClassroomEaseOut),
    label = "stageBannerBg"
  )
  val fg by animateColorAsState(
    targetValue = when (state.stage) {
      ClassroomStage.TURN_PASSED -> EmeraldDarkText
      ClassroomStage.TURN_RETRY -> AmberDarkText
      ClassroomStage.CHILD_RECITING, ClassroomStage.TASMEE_STREAMING -> Color(0xFF0369A1)
      ClassroomStage.QARI_RECITING -> EmeraldDarkText
      else -> Color(0xFF334155)
    },
    animationSpec = tween(durationMillis = 190, easing = ClassroomEaseOut),
    label = "stageBannerFg"
  )

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = bg,
    modifier = Modifier.fillMaxWidth()
  ) {
    AnimatedContent(
      targetState = text,
      transitionSpec = {
        (fadeIn(tween(180, easing = ClassroomEaseOut)) +
          scaleIn(initialScale = 0.96f, animationSpec = tween(180, easing = ClassroomEaseOut))) togetherWith
          fadeOut(tween(120, easing = ClassroomEaseOut))
      },
      label = "stageBannerText"
    ) { bannerText ->
      Text(
        text = bannerText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        textAlign = TextAlign.Center,
        style = TextStyle(fontFeatureSettings = "tnum"),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp)
      )
    }
  }
}

@Composable
private fun ThreeStepProgressBar(
  stage: ClassroomStage,
  hasChildrenRepeat: Boolean = KidsTeacherQari.DEFAULT.hasChildrenRepeat
) {
  val labels = if (hasChildrenRepeat) {
    listOf(
      stringResource(R.string.kids_step_sheikh),
      stringResource(R.string.kids_step_kids),
      stringResource(R.string.kids_step_child)
    )
  } else {
    listOf(
      stringResource(R.string.kids_step_sheikh),
      stringResource(R.string.kids_step_child_two_step)
    )
  }
  val stepIndex = if (hasChildrenRepeat) {
    when (stage) {
      ClassroomStage.QARI_RECITING -> 0
      ClassroomStage.KIDS_REPEATING -> 1
      ClassroomStage.CHILD_RECITING, ClassroomStage.SCORING, ClassroomStage.TURN_PASSED -> 2
      else -> -1
    }
  } else {
    when (stage) {
      ClassroomStage.QARI_RECITING -> 0
      ClassroomStage.CHILD_RECITING, ClassroomStage.SCORING, ClassroomStage.TURN_PASSED -> 1
      else -> -1
    }
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    labels.forEachIndexed { idx, lbl ->
      val active = idx == stepIndex
      val done = stepIndex > idx
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = when {
          active -> EmeraldPrimary
          done -> EmeraldContainer
          else -> Color(0xFFF1F5F9)
        },
        modifier = Modifier.weight(1f)
      ) {
        Text(
          text = lbl,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = when {
            active -> Color.White
            done -> EmeraldDarkText
            else -> Color(0xFF64748B)
          },
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(vertical = 5.dp)
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TajweedColorLegendRow() {
  val items = listOf(
    TajweedRuleType.MADD_6.defaultColor to stringResource(R.string.kids_tajweed_legend_madd6),
    TajweedRuleType.MADD_4_5.defaultColor to stringResource(R.string.kids_tajweed_legend_madd4),
    TajweedRuleType.MADD_2.defaultColor to stringResource(R.string.kids_tajweed_legend_madd2),
    TajweedRuleType.GHUNNAH_NOON.defaultColor to stringResource(R.string.kids_tajweed_legend_ghunnah),
    TajweedRuleType.QALQALAH.defaultColor to stringResource(R.string.kids_tajweed_legend_qalqalah),
    TajweedRuleType.SILENT.defaultColor to stringResource(R.string.kids_tajweed_legend_silent)
  )
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    items.forEach { (color, label) ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
        )
        Text(
          text = label,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF475569)
        )
      }
    }
  }
}

@Composable
private fun ModeAControlsRow(
  state: KidsClassroomUiState,
  isLoopActive: Boolean,
  onStartLoop: () -> Unit,
  onReciteOnly: () -> Unit,
  onFinishSpeaking: () -> Unit,
  onStopLoop: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    when {
      state.stage == ClassroomStage.CHILD_RECITING -> {
        Button(
          onClick = onFinishSpeaking,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldBright),
          modifier = Modifier
            .weight(1.3f)
            .defaultMinSize(minHeight = 52.dp)
            .testTag("kids_done_speaking_btn")
        ) {
          Text(
            text = stringResource(R.string.kids_classroom_stop_mic_btn),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
          )
        }
        OutlinedButton(
          onClick = onStopLoop,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .weight(0.7f)
            .defaultMinSize(minHeight = 52.dp)
            .testTag("kids_stop_loop_btn")
        ) {
          Text("⏹ " + stringResource(R.string.kids_classroom_stop_loop_btn), fontWeight = FontWeight.Bold)
        }
      }
      isLoopActive -> {
        Button(
          onClick = onStopLoop,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = RedWordError),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .testTag("kids_stop_loop_btn")
        ) {
          Text(
            text = "⏹ " + stringResource(R.string.kids_classroom_stop_loop_btn),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
          )
        }
      }
      else -> {
        Button(
          onClick = onStartLoop,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
          modifier = Modifier
            .weight(1.2f)
            .defaultMinSize(minHeight = 52.dp)
            .testTag("kids_start_lesson_btn")
        ) {
          Text(
            text = "▶ " + stringResource(R.string.kids_classroom_listen_repeat_btn),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
          )
        }
        OutlinedButton(
          onClick = onReciteOnly,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.5.dp, EmeraldPrimary),
          modifier = Modifier
            .weight(0.95f)
            .defaultMinSize(minHeight = 52.dp)
            .testTag("kids_recite_now_btn")
        ) {
          Text(
            text = "🎙️ " + stringResource(R.string.kids_classroom_recite_only_btn),
            color = EmeraldPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
      }
    }
  }
}

@Composable
private fun ModeBControlsRow(
  isStreaming: Boolean,
  onStart: () -> Unit,
  onStop: () -> Unit,
  onHint: () -> Unit,
  onReset: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Button(
      onClick = if (isStreaming) onStop else onStart,
      shape = RoundedCornerShape(16.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = if (isStreaming) RedWordError else TasmeeTargetBlue
      ),
      modifier = Modifier
        .weight(1.3f)
        .defaultMinSize(minHeight = 52.dp)
        .testTag("kids_tasmee_start_stop_btn")
    ) {
      Text(
        text = (if (isStreaming) "⏹ " else "🎙️ ") + stringResource(
          if (isStreaming) R.string.kids_tasmee_stop_btn else R.string.kids_tasmee_start_btn
        ),
        fontWeight = FontWeight.ExtraBold
      )
    }
    OutlinedButton(
      onClick = onHint,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .weight(0.75f)
        .defaultMinSize(minHeight = 52.dp)
        .testTag("kids_tasmee_hint_btn")
    ) {
      Text(stringResource(R.string.kids_tasmee_hint_btn), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    OutlinedButton(
      onClick = onReset,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .weight(0.75f)
        .defaultMinSize(minHeight = 52.dp)
        .testTag("kids_tasmee_reset_btn")
    ) {
      Text(stringResource(R.string.kids_tasmee_reset_btn), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
  }
}

@Composable
private fun AssetDownloadBannerCard(
  progress: AssetDownloadProgress,
  onRetryDownload: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("kids_download_banner")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = if (progress.isDownloading) {
          stringResource(
            R.string.kids_classroom_downloading_progress,
            progress.currentFileName.ifBlank { "ONNX ASR" },
            progress.progressPercent.toString()
          )
        } else {
          stringResource(R.string.kids_classroom_download_title)
        },
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        color = AmberDarkText
      )
      if (progress.isDownloading) {
        LinearProgressIndicator(
          progress = { progress.progressPercent / 100f },
          color = EmeraldPrimary,
          trackColor = EmeraldContainer,
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
        )
      } else {
        Text(
          text = progress.errorMessage ?: stringResource(R.string.kids_classroom_download_subtitle),
          fontSize = 12.sp,
          color = Color(0xFF78350F)
        )
        Button(
          onClick = onRetryDownload,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("kids_download_retry_btn")
        ) {
          Text(
            text = if (progress.errorMessage != null) {
              stringResource(R.string.kids_classroom_download_retry)
            } else {
              stringResource(R.string.kids_classroom_download_btn)
            },
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun SurahPickerDialog(
  surahs: List<KidsSurahInfo>,
  selectedSurah: Int,
  isArabic: Boolean,
  onSelect: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  var query by remember { mutableStateOf("") }
  val filtered = remember(surahs, query) {
    val q = query.trim()
    if (q.isEmpty()) surahs
    else surahs.filter { it.nameAr.contains(q, ignoreCase = true) || it.surahNumber.toString() == q }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.kids_select_surah_dialog_title),
        fontWeight = FontWeight.ExtraBold
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          placeholder = { Text(stringResource(R.string.kids_search_surah_placeholder)) },
          modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
          items(filtered, key = { it.surahNumber }) { info ->
            val isSel = info.surahNumber == selectedSurah
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isSel) EmeraldContainer else Color.Transparent,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(info.surahNumber) }
                .padding(vertical = 2.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "${info.surahNumber}. ${info.nameAr}",
                  fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                  color = if (isSel) EmeraldDarkText else MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = if (isArabic) "${info.ayahCount} آية" else "${info.ayahCount} Ayat",
                  fontSize = 12.sp,
                  color = Color(0xFF64748B)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(android.R.string.cancel))
      }
    }
  )
}

@Composable
private fun AyahPickerDialog(
  surahInfo: KidsSurahInfo,
  selectedAyah: Int,
  onSelect: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "${stringResource(R.string.kids_select_ayah_dialog_title)} · ${surahInfo.nameAr}",
        fontWeight = FontWeight.ExtraBold
      )
    },
    text = {
      LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
        items((1..surahInfo.ayahCount).toList()) { ayahNum ->
          val isSel = ayahNum == selectedAyah
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isSel) EmeraldContainer else Color.Transparent,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelect(ayahNum) }
          ) {
            Text(
              text = stringResource(
                R.string.kids_ayah_picker_label,
                ayahNum.toString(),
                surahInfo.ayahCount.toString()
              ),
              fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
              color = if (isSel) EmeraldDarkText else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(android.R.string.cancel))
      }
    }
  )
}
