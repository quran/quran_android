package com.quran.mobile.feature.downloadmanager.model.sheikhdownload

sealed class EntryForQari {
  abstract val isDownloaded: Boolean
  open val hasLocalFiles: Boolean get() = isDownloaded

  data class SuraForQari(
    val sura: Int,
    override val isDownloaded: Boolean,
    override val hasLocalFiles: Boolean = isDownloaded
  ) : EntryForQari()
  data class DatabaseForQari(override val isDownloaded: Boolean) : EntryForQari()
}
