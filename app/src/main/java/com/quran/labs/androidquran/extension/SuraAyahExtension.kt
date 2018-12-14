package com.quran.labs.androidquran.extension

import com.quran.labs.androidquran.data.SuraAyah

fun SuraAyah.requiresBasmallah(): Boolean {
  return ayah == 1 && sura != 1 && sura != 9
}
