package com.quran.mobile.feature.memorize.prompt

import com.quran.data.di.AppScope
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import com.quran.mobile.common.download.Downloader
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Told which suras a session has loaded, e.g. to download their hint audio ahead of time. */
fun interface UpcomingSuras {
  fun loaded(suras: IntRange)
}

/**
 * Downloads the hint voice's recitation with the app's own audio downloads, so hints can be spoken
 * offline: one sura, the whole Quran, or (with automatic downloads on) the suras a session is about
 * to reach, without interrupting the user.
 */
@SingleIn(AppScope::class)
class HintVoiceDownloads @Inject constructor(
  private val promptPlayer: PromptPlayer,
  private val downloader: Downloader,
  private val pageProvider: PageProvider,
  private val preferences: MemorizePreferences,
) : UpcomingSuras {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** The reciter giving the hints, if it exists in the app. */
  fun voice(): Qari? = pageProvider.getQaris().firstOrNull { it.id == preferences.promptQariId }

  /** The suras of [suras] whose audio in the hint voice isn't downloaded. Reads files. */
  fun missing(suras: IntRange = ALL_SURAS): List<Int> {
    val qari = voice() ?: return emptyList()
    return suras.filter { promptPlayer.voiceStatus(qari.id, it) == PromptPlayer.VoiceStatus.AUDIO_MISSING }
  }

  /** How much downloading [suras] takes in the hint voice, or null if unknown. Reads files. */
  fun size(suras: List<Int>): Long? = voice()?.let { promptPlayer.audioSize(it.id, suras) }

  /** Downloads [suras] in the background; the app's download notification shows the progress. */
  fun download(suras: List<Int>) {
    val qari = voice() ?: return
    if (suras.isNotEmpty()) downloader.downloadCompleteSuras(qari, suras, downloadDatabase = true)
  }

  override fun loaded(suras: IntRange) {
    if (!preferences.autoDownload || !preferences.correctorEnabled) return
    scope.launch {
      // e.g. the system not letting a service start: never worth interrupting the session for
      runCatching { download(missing(suras)) }
    }
  }

  companion object {
    val ALL_SURAS = 1..114
  }
}
