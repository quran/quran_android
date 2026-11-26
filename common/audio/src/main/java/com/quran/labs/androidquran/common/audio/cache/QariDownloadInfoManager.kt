package com.quran.labs.androidquran.common.audio.cache

import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.common.audio.cache.command.AudioInfoCommand
import com.quran.labs.androidquran.common.audio.model.download.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleIn(AppScope::class)
class QariDownloadInfoManager private constructor(
  private val quranFileManager: QuranFileManager,
  private val audioInfoCommand: AudioInfoCommand,
  private val downloadInfoStreams: DownloadInfoStreams,
  private val audioCacheInvalidator: AudioCacheInvalidator,
  private val qariUtil: QariUtil?,
  private val scope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher,
  private val coalesceWindowMs: Long,
) : QariDownloadInfoSource {

  @Inject
  constructor(
    quranFileManager: QuranFileManager,
    audioInfoCommand: AudioInfoCommand,
    downloadInfoStreams: DownloadInfoStreams,
    audioCacheInvalidator: AudioCacheInvalidator,
    qariUtil: QariUtil,
  ) : this(
    quranFileManager = quranFileManager,
    audioInfoCommand = audioInfoCommand,
    downloadInfoStreams = downloadInfoStreams,
    audioCacheInvalidator = audioCacheInvalidator,
    qariUtil = qariUtil,
    scope = CoroutineScope(SupervisorJob()),
    ioDispatcher = Dispatchers.IO,
    coalesceWindowMs = DEFAULT_COALESCE_WINDOW_MS,
  )

  constructor(
    quranFileManager: QuranFileManager,
    audioInfoCommand: AudioInfoCommand,
    downloadInfoStreams: DownloadInfoStreams,
    audioCacheInvalidator: AudioCacheInvalidator,
  ) : this(
    quranFileManager = quranFileManager,
    audioInfoCommand = audioInfoCommand,
    downloadInfoStreams = downloadInfoStreams,
    audioCacheInvalidator = audioCacheInvalidator,
    qariUtil = null,
    scope = CoroutineScope(SupervisorJob()),
    ioDispatcher = Dispatchers.IO,
    coalesceWindowMs = DEFAULT_COALESCE_WINDOW_MS,
  )

  private val storageCache = QariDownloadInfoStorageCache()
  private val pendingMutex = Mutex()
  private val updateMutex = Mutex()
  private val pendingQariIds = LinkedHashSet<Int>()
  private var pendingFlushJob: Job? = null

  init {
    scope.launch(ioDispatcher) {
      populateCache()
      subscribeToChanges()
    }
  }

  fun downloadedQariInfo(): Flow<List<QariDownloadInfo>> {
    return storageCache.flow()
  }

  override fun downloadQariInfoFilteringNonDownloadedGappedQaris(): Flow<List<QariDownloadInfo>> {
    return downloadedQariInfo().map { list ->
      list.filter { qariDownloadInfo ->
        val qari = qariDownloadInfo.qari
        qari.isGapless ||
            // gapped qaris are only shown if they don't have a gapless alternative or if
            // some file for the qari is already downloaded.
            (!qari.hasGaplessAlternative || qariDownloadInfo.surasWithLocalFiles.isNotEmpty())
      }
    }
  }

  private fun populateCache() {
    val audioDirectory = quranFileManager.audioFileDirectory()
    val qariDownloadInfo = if (audioDirectory != null) {
      audioInfoCommand.generateAllQariDownloadInfo(audioDirectory)
    } else {
      val fallbackQaris = qariUtil?.getQariList().orEmpty()
      if (fallbackQaris.isEmpty()) {
        return
      }
      fallbackQaris.map { qari ->
        if (qari.isGapless) {
          QariDownloadInfo.GaplessQariDownloadInfo(
            qari = qari,
            fullyDownloadedSuras = emptyList(),
            partiallyDownloadedSuras = emptyList(),
            surasWithLocalFiles = emptyList()
          )
        } else {
          QariDownloadInfo.GappedQariDownloadInfo(
            qari = qari,
            fullyDownloadedSuras = emptyList(),
            partiallyDownloadedSuras = emptyList(),
            surasWithLocalFiles = emptyList()
          )
        }
      }
    }
    storageCache.writeAll(qariDownloadInfo)
  }

  private suspend fun subscribeToChanges() {
    coroutineScope {
      launch {
        downloadInfoStreams.downloadInfoStream().collect { info ->
          when (info) {
            is DownloadInfo.FileDownloaded -> {
              val qariId = (info.metadata as? AudioDownloadMetadata)?.qariId ?: return@collect
              scheduleCoalescedRefresh(qariId)
            }
            is DownloadInfo.DownloadBatchSuccess,
            is DownloadInfo.DownloadBatchError -> {
              val qariId = (info.metadata as? AudioDownloadMetadata)?.qariId
              flushImmediately(qariId)
            }
            else -> Unit
          }
        }
      }
      launch {
        audioCacheInvalidator.qarisToInvalidate().collect { qariId ->
          flushImmediately(qariId)
        }
      }
    }
  }

  private suspend fun scheduleCoalescedRefresh(qariId: Int) {
    pendingMutex.withLock {
      pendingQariIds.add(qariId)
      if (pendingFlushJob == null) {
        pendingFlushJob = scope.launch(ioDispatcher) {
          delay(coalesceWindowMs)
          flushPending()
        }
      }
    }
  }

  private suspend fun flushImmediately(qariId: Int?) {
    pendingMutex.withLock {
      if (qariId != null) {
        pendingQariIds.add(qariId)
      }
      pendingFlushJob?.cancel()
      pendingFlushJob = null
    }
    flushPending()
  }

  private suspend fun flushPending() {
    val qariIds = pendingMutex.withLock {
      if (pendingQariIds.isEmpty()) {
        pendingFlushJob = null
        return
      }
      val snapshot = pendingQariIds.toList()
      pendingQariIds.clear()
      pendingFlushJob = null
      snapshot
    }

    updateMutex.withLock {
      for (id in qariIds) {
        updateQariInformationForQariId(id)
      }
    }
  }

  private fun updateQariInformationForQariId(qariId: Int) {
    val qariDownloadInfo = getUpdatedQariInformation(qariId)
    if (qariDownloadInfo != null) {
      val updated = storageCache.lastValue()
        .map {
          // latest last value, but replace the qari item with our updated one
          if (it.qari.id == qariDownloadInfo.qari.id) qariDownloadInfo else it
        }
      storageCache.writeAll(updated)
    }
  }

  private fun getUpdatedQariInformation(qariId: Int): QariDownloadInfo? {
    val lastInfo = storageCache.lastValue()
    val updatedQari = lastInfo.firstOrNull { it.qari.id == qariId } ?: return null
    val audioDirectory = quranFileManager.audioFileDirectory()
    return if (audioDirectory != null) {
      audioInfoCommand.generateQariDownloadInfo(updatedQari.qari, audioDirectory)
    } else {
      null
    }
  }

  companion object {
    private const val DEFAULT_COALESCE_WINDOW_MS = 300L

    internal fun forTest(
      quranFileManager: QuranFileManager,
      audioInfoCommand: AudioInfoCommand,
      downloadInfoStreams: DownloadInfoStreams,
      audioCacheInvalidator: AudioCacheInvalidator,
      scope: CoroutineScope,
      ioDispatcher: CoroutineDispatcher,
      coalesceWindowMs: Long = DEFAULT_COALESCE_WINDOW_MS,
      qariUtil: QariUtil? = null,
    ): QariDownloadInfoManager {
      return QariDownloadInfoManager(
        quranFileManager = quranFileManager,
        audioInfoCommand = audioInfoCommand,
        downloadInfoStreams = downloadInfoStreams,
        audioCacheInvalidator = audioCacheInvalidator,
        qariUtil = qariUtil,
        scope = scope,
        ioDispatcher = ioDispatcher,
        coalesceWindowMs = coalesceWindowMs,
      )
    }
  }
}
