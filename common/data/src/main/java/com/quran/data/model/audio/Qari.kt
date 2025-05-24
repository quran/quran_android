package com.quran.data.model.audio

import androidx.annotation.StringRes

data class Qari(
  val id: Int,
  @StringRes val nameResource: Int,
  val url: String,
  val opusUrl: String? = null,
  val path: String,
  val hasGaplessAlternative: Boolean,
  val db: String? = null,
) {
  val databaseName = if (db.isNullOrEmpty()) null else db
  val isGapless: Boolean = databaseName != null
  val hasOpus = opusUrl != null && opusUrl.isNotEmpty()

  fun url(extension: String): String {
    return when (extension) {
        "opus" -> opusUrl ?: throw IllegalArgumentException("Opus is not available for qari $id")
        "mp3" -> url
        else -> throw IllegalArgumentException("Unsupported audio format: $extension")
    }
  }
}
