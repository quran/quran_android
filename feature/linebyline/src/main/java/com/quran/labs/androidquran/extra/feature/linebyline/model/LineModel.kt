package com.quran.labs.androidquran.extra.feature.linebyline.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap

data class LineModel(
  val lineId: Int,
  val lineImage: ImageBitmap,
  val lineBitmap: Bitmap
)
