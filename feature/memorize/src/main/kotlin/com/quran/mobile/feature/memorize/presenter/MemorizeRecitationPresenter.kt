package com.quran.mobile.feature.memorize.presenter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.quran.data.core.QuranInfo
import com.quran.data.di.QuranScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.audio.Qari
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.check.DoubleCheckAdvice
import com.quran.mobile.feature.memorize.check.DoubleCheckModel
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.HintVoiceDownloads
import com.quran.mobile.feature.memorize.prompt.PromptPlayer
import com.quran.mobile.feature.memorize.recording.RecordingPlayer
import com.quran.mobile.feature.memorize.recording.SessionPlayback
import com.quran.mobile.feature.memorize.service.MemorizeService
import com.quran.mobile.feature.memorize.session.MemorizeSessionController
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.ui.MicButtonState
import com.quran.recitation.presenter.RecitationPresenter
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Starts and controls memorization sessions from the reading screen ("Memorize" / "Recite from
 * here" / the audio bar's microphone).
 */
@QuranScope
class MemorizeRecitationPresenter @Inject constructor(
  private val controller: MemorizeSessionController,
  private val models: SpeechModelStore,
  private val preferences: MemorizePreferences,
  private val promptPlayer: PromptPlayer,
  private val voiceDownloads: HintVoiceDownloads,
  private val recordingPlayer: RecordingPlayer,
  private val sessionPlayback: SessionPlayback,
  private val quranInfo: QuranInfo,
  private val doubleCheck: DoubleCheckModel,
  private val doubleCheckAdvice: DoubleCheckAdvice,
) : RecitationPresenter {

  private val enabled = MutableStateFlow(true)
  private var activityRef = WeakReference<Activity>(null)
  private var scope: CoroutineScope? = null
  private var pendingStart: (() -> SuraAyah)? = null
  private var pendingMode = MemorizeState.Mode.MEMORIZE
  private var voiceChecked = false
  // whether to record the session about to start (null: not decided yet)
  private var pendingRecord: Boolean? = null
  private var serviceSessionId: String? = null
  private var downloadDialog: AlertDialog? = null
  private var micButton: FloatingMicButton? = null
  // from the reading screen: the pages on screen and how to start a session there
  private var pagesOnScreen: () -> List<Int> = { emptyList() }
  private var startOnScreen: (Boolean) -> Unit = {}

  override fun bind(what: Activity) {
    activityRef = WeakReference(what)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    this.scope = scope
    controller.state
      .onEach { state ->
        // keeps listening with the screen off; started once the session exists, while it's visible
        if (state != null && state.isActive && state.id != serviceSessionId) {
          serviceSessionId = state.id
          activityRef.get()?.let { MemorizeService.start(it) }
        }
      }
      .launchIn(scope)
    controller.failures
      .onEach { activityRef.get()?.let { Toast.makeText(it, R.string.memorize_start_failed, Toast.LENGTH_LONG).show() } }
      .launchIn(scope)

    // a clear way to pause, resume and end the session, wherever the user is on the page
    val micButton = FloatingMicButton(
      activity = what,
      preferences = preferences,
      onToggle = { if (controller.state.value == null) startOnScreen(false) else controller.pauseOrResume() },
      onChooseMode = { startOnScreen(true) },
      onEnd = ::endSession,
      onReplay = { if (sessionPlayback.isPlaying) sessionPlayback.pause() else sessionPlayback.play() },
    ).also { it.attach() }
    this.micButton = micButton
    combine(controller.state, recordingPlayer.playback) { state, playback ->
      // before a session too: it is how a session starts
      if (state == null) {
        MicButtonState(idle = true, listening = false, preparing = false, recording = false, canReplay = false, replaying = false)
      } else state.let {
        MicButtonState(
          idle = false,
          listening = it.status == MemorizeState.Status.LISTENING || it.status == MemorizeState.Status.PREPARING,
          preparing = it.status == MemorizeState.Status.PREPARING,
          recording = it.recordingId != null,
          canReplay = it.recordingId != null,
          replaying = playback?.isPlaying == true && playback.recording.id == it.recordingId,
        )
      }
    }
      .onEach { micButton.show(it) }
      .launchIn(scope)
  }

  override fun unbind(what: Activity) {
    if (activityRef.get() === what) activityRef.clear()
    micButton?.detach()
    micButton = null
    // a model download carries on; the dialog shows it again on the next start
    downloadDialog?.dismiss()
    downloadDialog = null
    scope?.cancel()
    scope = null
    // the session itself keeps running (e.g. with the screen off during salah)
  }

  override fun isRecitationEnabled(): Boolean = true

  override fun isRecitationEnabledFlow(): StateFlow<Boolean> = enabled

  // memorization keeps no transcript
  override fun supportsTranscript(): Boolean = false

  // the user's own recording, when they chose to record the session
  override fun supportsPlayback(): Boolean = controller.state.value?.recordingId != null

  override fun supportsHints(): Boolean = true

  override fun onHintRequested() = controller.requestHint()

  override fun setReadingScreen(pagesOnScreen: () -> List<Int>, start: (showModeSelection: Boolean) -> Unit) {
    this.pagesOnScreen = pagesOnScreen
    startOnScreen = start
  }

  /**
   * Where the user may start: anywhere from [PAGES_AROUND] pages before the page(s) on screen to as
   * many after (null without a reading screen).
   */
  private fun startPages(): IntRange? {
    val pages = pagesOnScreen().takeIf { it.isNotEmpty() } ?: return null
    return maxOf(1, pages.min() - PAGES_AROUND)..minOf(quranInfo.numberOfPages, pages.max() + PAGES_AROUND)
  }

  private fun firstAyah(page: Int) = quranInfo.getVerseRangeForPage(page).let { SuraAyah(it.startSura, it.startAyah) }

  private fun lastAyah(page: Int) = quranInfo.getVerseRangeForPage(page).let { SuraAyah(it.endingSura, it.endingAyah) }

  override fun startOrStopRecitation(startAyah: () -> SuraAyah, showModeSelection: Boolean) {
    val state = controller.state.value
    if (state != null && state.isActive) {
      controller.pauseOrResume()
      return
    }
    if (state?.status == MemorizeState.Status.FINISHED) {
      endSession()
    }
    pendingStart = startAyah
    voiceChecked = false
    pendingRecord = null
    if (showModeSelection) chooseMode() else {
      pendingMode = MemorizeState.Mode.MEMORIZE
      proceed()
    }
  }

  /** Long press: memorize on screen, or follow the recitation during salah. */
  private fun chooseMode() {
    val activity = activityRef.get() ?: return
    val modes = arrayOf(MemorizeState.Mode.MEMORIZE, MemorizeState.Mode.SALAH)
    AlertDialog.Builder(activity)
      .setTitle(R.string.memorize_choose_mode)
      .setItems(arrayOf(activity.getString(R.string.memorize_mode_memorize), activity.getString(R.string.memorize_mode_salah))) { _, which ->
        pendingMode = modes[which]
        proceed()
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> pendingStart = null }
      .show()
  }

  override fun endSession() {
    controller.stop()
    activityRef.get()?.let { MemorizeService.stop(it) }
  }

  override fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
    if (requestCode != REQUEST_PERMISSIONS) return
    if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
      proceed()
    } else {
      activityRef.get()?.let {
        Toast.makeText(it, R.string.memorize_microphone_denied, Toast.LENGTH_LONG).show()
      }
      pendingStart = null
    }
  }

  /** Walks through the one-time steps (disclaimer, model, permission) and then starts. */
  private fun proceed() {
    val activity = activityRef.get() ?: return
    val start = pendingStart ?: return
    when {
      !preferences.disclaimerAccepted -> showDisclaimer(activity)
      !models.isInstalled() -> when {
        models.isDownloading -> showDownload(activity)
        preferences.autoDownload -> {
          models.startDownload()
          showDownload(activity)
        }
        else -> offerModelDownload(activity)
      }
      !hasMicrophonePermission(activity) -> requestPermissions(activity)
      !voiceChecked -> {
        // the double-check is turned on but not downloaded: with automatic downloads, now (the
        // session doesn't wait for it)
        if (preferences.autoDownload && preferences.doubleCheck && !doubleCheck.isInstalled) {
          doubleCheck.store.startDownload()
        }
        voiceChecked = true
        // with automatic downloads, the session downloads the suras it reaches on its own
        if (preferences.autoDownload) proceed() else checkHintVoice(activity, start())
      }
      pendingRecord == null -> when (preferences.recordingMode) {
        MemorizePreferences.RecordingMode.OFF -> {
          pendingRecord = false
          proceed()
        }
        MemorizePreferences.RecordingMode.ALWAYS -> {
          pendingRecord = true
          proceed()
        }
        MemorizePreferences.RecordingMode.ASK -> askToRecord(activity)
      }
      else -> {
        pendingStart = null
        // the user is expected where they asked to start (the page on screen, or an ayah they chose)
        val home = start()
        val pages = startPages()
        if (pages == null) {
          controller.start(home, pendingMode, record = pendingRecord == true)
        } else {
          val first = firstAyah(pages.first).takeIf { it <= home } ?: home
          controller.start(
            first, pendingMode, record = pendingRecord == true, startAnywhereUntil = lastAyah(pages.last), home = home,
          )
        }
        if (pendingMode == MemorizeState.Mode.SALAH) {
          // recognition degrades with distance and room echo: keep the phone close
          Toast.makeText(activity, R.string.memorize_salah_placement, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  /** Offers to download the sura (or the whole Quran) in the hint voice so hints can be spoken offline. */
  private fun checkHintVoice(activity: Activity, start: SuraAyah) {
    val qari = voiceDownloads.voice()
    if (!preferences.correctorEnabled || qari == null) {
      proceed()
      return
    }
    val scope = scope ?: return
    scope.launch {
      // reads the reciter's timings and files
      val (status, missing) = withContext(Dispatchers.IO) {
        promptPlayer.voiceStatus(qari.id, start.sura) to voiceDownloads.missing()
      }
      if (activityRef.get() !== activity) return@launch
      if (status == PromptPlayer.VoiceStatus.AUDIO_MISSING) {
        val size = withContext(Dispatchers.IO) { voiceDownloads.size(missing) }
        offerVoiceDownload(activity, qari, start, missing, size)
      } else {
        proceed()
      }
    }
  }

  private fun offerVoiceDownload(activity: Activity, qari: Qari, start: SuraAyah, missing: List<Int>, size: Long?) {
    val whole = if (size != null) {
      activity.getString(R.string.memorize_voice_download_all_size, Formatter.formatShortFileSize(activity, size))
    } else {
      activity.getString(R.string.memorize_voice_download_all)
    }
    AlertDialog.Builder(activity)
      .setTitle(R.string.memorize_voice_download_title)
      .setMessage(activity.getString(R.string.memorize_voice_download_message, activity.getString(qari.nameResource)))
      .setPositiveButton(R.string.memorize_voice_download_sura) { _, _ ->
        voiceDownloads.download(listOf(start.sura))
        proceed()
      }
      .setNeutralButton(whole) { _, _ ->
        voiceDownloads.download(missing)
        proceed()
      }
      .setNegativeButton(R.string.memorize_voice_download_skip) { _, _ -> proceed() }
      .setOnCancelListener { pendingStart = null }
      .show()
  }

  private fun askToRecord(activity: Activity) {
    AlertDialog.Builder(activity)
      .setTitle(R.string.memorize_record_ask_title)
      .setMessage(R.string.memorize_record_ask_message)
      .setPositiveButton(R.string.memorize_record_ask_yes) { _, _ ->
        pendingRecord = true
        proceed()
      }
      .setNegativeButton(R.string.memorize_record_ask_no) { _, _ ->
        pendingRecord = false
        proceed()
      }
      .setOnCancelListener { pendingStart = null }
      .show()
  }

  private fun showDisclaimer(activity: Activity) {
    AlertDialog.Builder(activity)
      .setTitle(R.string.memorize_title)
      .setMessage(R.string.memorize_disclaimer)
      .setPositiveButton(R.string.memorize_disclaimer_accept) { _, _ ->
        preferences.disclaimerAccepted = true
        proceed()
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> pendingStart = null }
      .show()
  }

  private fun offerModelDownload(activity: Activity) {
    val sizeMb = models.spec.downloadSize / 1_000_000
    // where it suits the phone, the double-check is offered with it (off by default: a download)
    val offerCheck = !doubleCheck.isInstalled && doubleCheckAdvice.advice().recommended
    val withCheck = CheckBox(activity).apply {
      text = activity.getString(
        R.string.memorize_double_check_offer,
        Formatter.formatShortFileSize(activity, DoubleCheckModel.SPEC.downloadSize),
      )
      isChecked = preferences.doubleCheck
    }
    AlertDialog.Builder(activity)
      .setTitle(R.string.memorize_model_title)
      .setMessage(activity.getString(R.string.memorize_model_message, sizeMb, models.spec.attribution, models.spec.license))
      .apply {
        if (offerCheck) {
          setView(FrameLayout(activity).apply { setPadding(48, 8, 48, 0); addView(withCheck) })
        }
      }
      .setPositiveButton(R.string.memorize_model_download) { _, _ ->
        models.startDownload()
        if (offerCheck && withCheck.isChecked) {
          preferences.doubleCheck = true
          doubleCheck.store.startDownload()
        }
        showDownload(activity)
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> pendingStart = null }
      .show()
  }

  /** Shows the model download's progress, and goes on with the start once it is installed. */
  private fun showDownload(activity: Activity) {
    val scope = scope ?: return
    val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
      max = 1000
      setPadding(48, 24, 48, 0)
    }
    val dialog = AlertDialog.Builder(activity)
      .setTitle(R.string.memorize_model_downloading)
      .setView(progress)
      .setCancelable(false)
      .setNegativeButton(android.R.string.cancel) { _, _ ->
        models.cancelDownload()
        pendingStart = null
      }
      .show()
    downloadDialog = dialog
    scope.launch {
      val result = models.state
        .onEach { state ->
          if (state is SpeechModelStore.State.Downloading && state.totalBytes > 0) {
            progress.progress = (1000 * state.downloadedBytes / state.totalBytes).toInt()
          }
        }
        .first { it !is SpeechModelStore.State.Downloading }
      dialog.dismiss()
      if (downloadDialog === dialog) downloadDialog = null
      when (result) {
        is SpeechModelStore.State.Installed -> proceed()
        is SpeechModelStore.State.Failed -> {
          Toast.makeText(activity, activity.getString(R.string.memorize_model_failed, result.reason), Toast.LENGTH_LONG).show()
          pendingStart = null
        }
        else -> pendingStart = null
      }
    }
  }

  private fun hasMicrophonePermission(activity: Activity) =
    ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

  private fun requestPermissions(activity: Activity) {
    val permissions = buildList {
      add(Manifest.permission.RECORD_AUDIO)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), REQUEST_PERMISSIONS)
  }

  private companion object {
    const val REQUEST_PERMISSIONS = 4242
    /** Pages before and after the one on screen where the user may start. */
    const val PAGES_AROUND = 3
  }
}
