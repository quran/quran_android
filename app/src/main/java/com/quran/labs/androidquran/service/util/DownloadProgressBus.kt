package com.quran.labs.androidquran.service.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class DownloadProgressEvent {
  abstract val downloadType: Int
  abstract val downloadKey: String?
  abstract val title: String?

  data class Downloading(
    override val downloadType: Int,
    override val downloadKey: String? = null,
    override val title: String? = null,
    val progress: Int = -1,
    val downloadedSize: Long = -1L,
    val totalSize: Long = -1L,
    val sura: Int = -1,
    val ayah: Int = -1
  ) : DownloadProgressEvent()

  data class Processing(
    override val downloadType: Int,
    override val downloadKey: String? = null,
    override val title: String? = null,
    val progress: Int = -1,
    val processedFiles: Int = 0,
    val totalFiles: Int = 0
  ) : DownloadProgressEvent()

  data class Success(
    override val downloadType: Int,
    override val downloadKey: String? = null,
    override val title: String? = null
  ) : DownloadProgressEvent()

  data class Error(
    override val downloadType: Int,
    override val downloadKey: String? = null,
    override val title: String? = null,
    val errorCode: Int,
    val isFatal: Boolean
  ) : DownloadProgressEvent()
}

object DownloadProgressBus {
  private val _events = MutableSharedFlow<DownloadProgressEvent>(
    replay = 0,
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val events: SharedFlow<DownloadProgressEvent> = _events.asSharedFlow()

  @JvmStatic
  fun emit(event: DownloadProgressEvent): Boolean {
    return _events.tryEmit(event)
  }
}
