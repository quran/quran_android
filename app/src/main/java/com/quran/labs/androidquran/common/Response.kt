package com.quran.labs.androidquran.common

import android.graphics.Bitmap

class Response {
  var pageNumber: Int = 0
    private set
  val bitmap: Bitmap?
  val errorCode: Int
  val sourceWidth: Int
  val sourceHeight: Int

  constructor(bitmap: Bitmap?) {
    this.bitmap = bitmap
    this.errorCode = 0
    this.sourceWidth = bitmap?.width ?: 0
    this.sourceHeight = bitmap?.height ?: 0
  }

  constructor(bitmap: Bitmap?, @Suppress("UNUSED_PARAMETER") warningCode: Int) {
    this.bitmap = bitmap
    this.errorCode = 0
    this.sourceWidth = bitmap?.width ?: 0
    this.sourceHeight = bitmap?.height ?: 0
    // we currently ignore warnings
  }

  constructor(
    bitmap: Bitmap?,
    @Suppress("UNUSED_PARAMETER") warningCode: Int,
    sourceWidth: Int,
    sourceHeight: Int
  ) {
    this.bitmap = bitmap
    this.errorCode = 0
    this.sourceWidth = sourceWidth
    this.sourceHeight = sourceHeight
  }

  constructor(errorCode: Int) {
    this.bitmap = null
    this.errorCode = errorCode
    this.sourceWidth = 0
    this.sourceHeight = 0
  }

  fun setPageData(pageNumber: Int) {
    this.pageNumber = pageNumber
  }

  val isSuccessful: Boolean
    get() = errorCode == 0

  companion object {
    const val ERROR_SD_CARD_NOT_FOUND = 1
    const val ERROR_FILE_NOT_FOUND = 2
    const val ERROR_DOWNLOADING_ERROR = 3
    const val ERROR_NO_INTERNET = 4
    const val WARN_SD_CARD_NOT_FOUND = 5
    const val WARN_COULD_NOT_SAVE_FILE = 6
  }
}
