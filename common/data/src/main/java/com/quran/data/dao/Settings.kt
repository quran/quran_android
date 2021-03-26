package com.quran.data.dao

interface Settings {
  suspend fun setVersion(version: Int)
  suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean)
}
