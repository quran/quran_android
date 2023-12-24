package com.quran.mobile.common.download

import android.os.Parcelable

/**
 * DownloadInfo is metadata for download events
 * These events should be generic (i.e. not audio / translation / pages specific), and should not
 * include non-download information (i.e. processing). They should be focused on download on a
 * file by file basis, ignoring things like grouping or ranged downloads.
 */
sealed class DownloadInfo {
  abstract val key: String
  abstract val type: Int
  abstract val metadata: Parcelable?

  data class DownloadBatchSuccess(
    override val key: String,
    override val type: Int,
    override val metadata: Parcelable?
  ) : DownloadInfo()

  data class DownloadBatchError(
    override val key: String,
    override val type: Int,
    override val metadata: Parcelable?,
    val errorId: Int,
    val errorString: String
  ) : DownloadInfo()

  data class FileDownloaded(
    override val key: String,
    override val type: Int,
    override val metadata: Parcelable?,
    val filename: String,
    val sura: Int?,
    val ayah: Int?
  ) : DownloadInfo()

  data class FileDownloadProgress(
    override val key: String,
    override val type: Int,
    override val metadata: Parcelable?,
    val progress: Int,
    val sura: Int?,
    val ayah: Int?,
    val downloadedSize: Long?,
    val totalSize: Long?
  ) : DownloadInfo()
}
