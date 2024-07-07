package com.quran.mobile.feature.downloadmanager.model.sheikhdownload

sealed class EntryForQari {
  abstract val isDownloaded: Boolean

  data class SuraForQari(val sura: Int, override val isDownloaded: Boolean) : EntryForQari()
  data class DatabaseForQari(override val isDownloaded: Boolean) : EntryForQari()
}
