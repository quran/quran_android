package com.quran.labs.androidquran.extension

import com.quran.data.model.SuraAyah

fun SuraAyah.requiresBasmallah(): Boolean {
  return ayah == 1 && sura != 1 && sura != 9
}
