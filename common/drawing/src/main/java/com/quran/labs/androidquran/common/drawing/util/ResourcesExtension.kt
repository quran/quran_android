package com.quran.labs.androidquran.common.drawing.util

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes

private val alpha8Config: BitmapFactory.Options by lazy {
  BitmapFactory.Options().apply {
    inPreferredConfig = Bitmap.Config.ALPHA_8
  }
}

fun Resources.getDrawableAsAlpha8(@DrawableRes drawable: Int): Bitmap {
  val sourceBitmap = BitmapFactory.decodeResource(this, drawable, alpha8Config)

  val bitmap = sourceBitmap.extractAlpha()
  sourceBitmap.recycle()
  return bitmap
}


