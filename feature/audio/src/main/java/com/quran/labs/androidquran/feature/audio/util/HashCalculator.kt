package com.quran.labs.androidquran.feature.audio.util

import java.io.File

interface HashCalculator {
  fun calculateHash(file: File): String
}
