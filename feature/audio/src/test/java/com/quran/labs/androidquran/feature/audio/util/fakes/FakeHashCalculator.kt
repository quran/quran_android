package com.quran.labs.androidquran.feature.audio.util.fakes

import com.quran.labs.androidquran.feature.audio.util.HashCalculator
import java.io.File

class FakeHashCalculator : HashCalculator {
  private val hashes = mutableMapOf<String, String>()

  fun setHash(filePath: String, hash: String) {
    hashes[filePath] = hash
  }

  override fun calculateHash(file: File): String {
    return hashes[file.absolutePath] ?: ""
  }
}
