package com.quran.labs.androidquran.common.audio.model

import android.content.Context
import android.os.Parcelable
import com.quran.data.model.audio.Qari
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class QariItem(
  val id: Int,
  val name: String,
  val url: String,
  val opusUrl: String? = null,
  val path: String,
  val hasGaplessAlternative: Boolean,
  val db: String? = null
) : Parcelable {
  @IgnoredOnParcel
  val databaseName = if (db.isNullOrEmpty()) null else db

  val isGapless: Boolean
    get() = databaseName != null

  fun hasOpus() = opusUrl != null && opusUrl.isNotEmpty()

  fun url(extension: String): String {
    return when (extension) {
      "opus" -> opusUrl ?: throw IllegalArgumentException("Opus is not available for qari $id")
      "mp3" -> url
      else -> throw IllegalArgumentException("Unsupported audio format: $extension")
    }
  }

  companion object {
    fun fromQari(context: Context, qari: Qari): QariItem {
      return QariItem(
        id = qari.id,
        name = context.getString(qari.nameResource),
        url = qari.url,
        opusUrl = qari.opusUrl,
        path = qari.path,
        hasGaplessAlternative = qari.hasGaplessAlternative,
        db = qari.db
      )
    }
  }
}
