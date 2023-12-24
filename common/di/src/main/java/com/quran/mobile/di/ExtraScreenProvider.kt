package com.quran.mobile.di

import android.content.Context

interface ExtraScreenProvider {

  val order: Int
  val id: Int
  val titleResId: Int

  /** Called when the item with this id is selected. Return true if the click was handled */
  fun onClick(context: Context): Boolean

}
