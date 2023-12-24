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
  val path: String,
  val hasGaplessAlternative: Boolean,
  val db: String? = null
) : Parcelable {
  @IgnoredOnParcel
  val databaseName = if (db.isNullOrEmpty()) null else db

  val isGapless: Boolean
    get() = databaseName != null

  companion object {
    fun fromQari(context: Context, qari: Qari): QariItem {
      return QariItem(
        id = qari.id,
        name = context.getString(qari.nameResource),
        url = qari.url,
        path = qari.path,
        hasGaplessAlternative = qari.hasGaplessAlternative,
        db = qari.db
      )
    }
  }
}
