package com.quran.labs.androidquran.common

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class QariItem(
  val id: Int,
  val name: String,
  val url: String,
  val path: String,
  var databaseName: String? = null
) : Parcelable {

  init {
    databaseName = if (databaseName.isNullOrEmpty()) null else databaseName
  }

  val isGapless: Boolean
    get() = databaseName != null
}
