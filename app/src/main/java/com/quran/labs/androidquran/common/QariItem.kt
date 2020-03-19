package com.quran.labs.androidquran.common

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class QariItem(
  val id: Int,
  val name: String,
  val url: String,
  val path: String,
  val databaseName: String? = null
) : Parcelable {

  val isGapless: Boolean
    get() = !databaseName.isNullOrEmpty()
}
