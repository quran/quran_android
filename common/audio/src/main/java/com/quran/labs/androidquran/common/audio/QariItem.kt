package com.quran.labs.androidquran.common.audio

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class QariItem(
  val id: Int,
  val name: String,
  val url: String,
  val path: String,
  val db: String? = null
) : Parcelable {
  @IgnoredOnParcel
  val databaseName = if (db.isNullOrEmpty()) null else db

  val isGapless: Boolean
    get() = databaseName != null
}
