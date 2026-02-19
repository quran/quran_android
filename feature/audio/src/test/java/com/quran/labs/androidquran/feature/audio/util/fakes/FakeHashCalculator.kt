package com.quran.labs.androidquran.feature.audio.util.fakes

import com.quran.labs.androidquran.feature.audio.util.HashCalculator
import java.io.File
import java.io.IOException

class FakeHashCalculator : HashCalculator {
  private val hashes = mutableMapOf<String, String>()
  private var shouldThrow = false

  fun setHash(filePath: String, hash: String) {
    hashes[filePath] = hash
  }

  fun setShouldThrow(value: Boolean) {
    shouldThrow = value
  }

  override fun calculateHash(file: File): String {
    if (shouldThrow) throw IOException("Fake hash error")
    return hashes[file.absolutePath] ?: ""
  }
}
