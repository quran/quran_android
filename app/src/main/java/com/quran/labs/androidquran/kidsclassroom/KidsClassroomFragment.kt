package com.quran.labs.androidquran.kidsclassroom

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.QuranSettings

/**
 * Home-screen **Kids Tab** fragment hosting [KidsClassroomScreen] (with 60 fps Tajweed-driven
 * Ustadh Noor lip-sync teacher + Dual Offline/Streaming ASR) and [SurahMapScreen] across all
 * 114 Surahs (`1..114`).
 */
class KidsClassroomFragment : Fragment() {

  private lateinit var viewModel: KidsClassroomViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewModel = ViewModelProvider(this)[KidsClassroomViewModel::class.java]
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val isDark = isSystemInDarkTheme()
        MaterialTheme(
          colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
        ) {
          val uiState by viewModel.classroomState.collectAsStateWithLifecycle()
          val avatarState by viewModel.avatarState.collectAsStateWithLifecycle()
          val audioClueState by viewModel.audioClueState.collectAsStateWithLifecycle()
          val tajweedAnalysis by viewModel.tajweedAnalysis.collectAsStateWithLifecycle()
          val rewardsState by viewModel.rewardsState.collectAsStateWithLifecycle()
          val showSurahMap by viewModel.showSurahMap.collectAsStateWithLifecycle()
          val focusedSlowMoWordIndex by viewModel.focusedSlowMoWordIndex.collectAsStateWithLifecycle()

          val surahs = remember(uiState.isFullQuranLoaded) {
            MinshawiJuz30Repository.getAllSurahs(context.applicationContext)
          }

          val micPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
          ) { granted ->
            KidsClassroomManager.onRecordAudioPermissionResult(context, granted)
            if (!granted) {
              Toast.makeText(
                context,
                R.string.kids_classroom_mic_permission_needed,
                Toast.LENGTH_SHORT
              ).show()
            }
          }

          LaunchedEffect(Unit) {
            viewModel.startSession(context.applicationContext)
          }

          DisposableEffect(Unit) {
            val cb: () -> Unit = {
              micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            KidsClassroomManager.onRequestMicPermission = cb
            onDispose {
              if (KidsClassroomManager.onRequestMicPermission === cb) {
                KidsClassroomManager.onRequestMicPermission = null
              }
              KidsClassroomManager.stopActiveLoop()
            }
          }

          if (showSurahMap) {
            BackHandler {
              viewModel.closeSurahMap()
            }
            SurahMapScreen(
              surahs = surahs,
              currentSurahNumber = uiState.currentAyah.sura,
              rewards = rewardsState,
              onSelectSurah = { surahNum ->
                viewModel.selectSurahFromMap(context, surahNum)
              },
              onBack = { viewModel.closeSurahMap() }
            )
          } else {
            val isLoopActive = uiState.stage == ClassroomStage.QARI_RECITING ||
              uiState.stage == ClassroomStage.KIDS_REPEATING ||
              uiState.stage == ClassroomStage.CHILD_RECITING ||
              uiState.stage == ClassroomStage.SCORING ||
              uiState.stage == ClassroomStage.TASMEE_STREAMING ||
              uiState.isAutoTransitionPending
            BackHandler(enabled = isLoopActive) {
              KidsClassroomManager.stopActiveLoop()
            }
            KidsClassroomScreen(
              state = uiState,
              surahs = surahs,
              avatarState = avatarState,
              audioClueState = audioClueState,
              tajweedAnalysis = tajweedAnalysis,
              rewards = rewardsState,
              focusedSlowMoWordIndex = focusedSlowMoWordIndex,
              onToggleSlowMo = { viewModel.toggleSlowMo(context) },
              onToggleAudioCluesMode = { viewModel.toggleAudioCluesMode(context) },
              onSelectTeacherQari = { qari -> KidsClassroomManager.setSelectedTeacherQari(context, qari) },
              onTapWordForSlowMo = { wordIdx -> viewModel.replayWordInSlowMo(context, wordIdx) },
              onOpenSurahMap = { viewModel.openSurahMap() },
              onSelectMode = { mode ->
                KidsClassroomManager.setPracticeMode(
                  context = context,
                  mode = mode,
                  autoStartStreamingInModeB = false
                )
              },
              onSelectSurahAyah = { suraAyah ->
                KidsClassroomManager.selectAyahAndTurn(
                  context = context,
                  suraAyah = suraAyah,
                  turnIndex = 0,
                  autoPlayLoop = false
                )
              },
              onStartModeALoop = {
                KidsClassroomManager.startThreeStageLoopForCurrentTurn(context)
              },
              onStartModeAReciteOnly = {
                KidsClassroomManager.startReciteOnlyForCurrentTurn(context)
              },
              onFinishChildSpeakingEarly = {
                KidsClassroomManager.finishChildRecitationEarly()
              },
              onStopLoop = {
                KidsClassroomManager.stopActiveLoop()
              },
              onStartModeBStreaming = {
                KidsClassroomManager.startReciteAndRevealStreaming(context)
              },
              onRevealHintModeB = {
                KidsClassroomManager.revealHintWordInTasmee(context)
              },
              onResetModeBAyah = {
                KidsClassroomManager.resetCurrentAyahTasmee()
              },
              onPrevious = {
                KidsClassroomManager.goToPreviousTurnOrAyah(context, autoPlay = false)
              },
              onNext = {
                KidsClassroomManager.advanceToNextTurnOrAyah(context, autoPlay = false)
              },
              onToggleAutoNext = { enabled ->
                KidsClassroomManager.setAutoNextEnabled(context, enabled)
              },
              onRetryDownload = {
                KidsClassroomManager.startModelDownload(context)
              },
              onOpenInTajweedMushaf = { targetAyah ->
                openInTajweedMushaf(targetAyah)
              }
            )
          }
        }
      }
    }
  }

  @Suppress("DEPRECATION")
  override fun setMenuVisibility(menuVisible: Boolean) {
    super.setMenuVisibility(menuVisible)
    if (!menuVisible) {
      KidsClassroomManager.stopActiveLoop()
    }
  }

  override fun onPause() {
    KidsClassroomManager.stopActiveLoop()
    super.onPause()
  }

  override fun onStop() {
    KidsClassroomManager.stopActiveLoop()
    super.onStop()
  }

  private fun openInTajweedMushaf(targetAyah: SuraAyah) {
    val ctx = context ?: return
    KidsClassroomManager.stopActiveLoop()
    val settings = QuranSettings.getInstance(ctx)
    settings.pageType = "tajweed"
    val quranInfo = QuranInfo(MadaniDataSource())
    val page = quranInfo.getPageFromSuraAyah(targetAyah.sura, targetAyah.ayah).coerceIn(1, 604)
    val intent = Intent(ctx, PagerActivity::class.java).apply {
      putExtra("page", page)
      putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, targetAyah.sura)
      putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, targetAyah.ayah)
      putExtra(PagerActivity.EXTRA_START_CHILDREN_LEARNING_MODE, true)
    }
    startActivity(intent)
  }

  companion object {
    @JvmStatic
    fun newInstance(): KidsClassroomFragment = KidsClassroomFragment()
  }
}
