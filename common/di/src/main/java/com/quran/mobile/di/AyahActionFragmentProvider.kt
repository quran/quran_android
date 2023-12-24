package com.quran.mobile.di

import androidx.fragment.app.Fragment

interface AyahActionFragmentProvider {

  val order: Int
  val iconResId: Int
  fun newAyahActionFragment(): Fragment

}
