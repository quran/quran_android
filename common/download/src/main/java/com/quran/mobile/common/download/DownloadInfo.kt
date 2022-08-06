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
  abstract val filename: String
  abstract val metadata: Parcelable?

  data class DownloadComplete(
    override val key: String,
    override val type: Int,
    override val filename: String,
    override val metadata: Parcelable?
  ) : DownloadInfo()

  data class DownloadError(
    override val key: String,
    override val type: Int,
    override val filename: String,
    override val metadata: Parcelable?,
    val errorId: Int,
  ) : DownloadInfo()

  // TODO: In the future, should add DownloadStarted, DownloadProgress
}
