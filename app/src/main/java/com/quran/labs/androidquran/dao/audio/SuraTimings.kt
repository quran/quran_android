package com.quran.labs.androidquran.dao.audio

import android.util.SparseArray
import android.util.SparseLongArray

data class SuraTimings(
  val ayahTimings: SparseLongArray,
  val wordTimings: SparseArray<List<WordTiming>>
) {

  companion object {
    val EMPTY = SuraTimings(SparseLongArray(0), SparseArray(0))
  }
}

