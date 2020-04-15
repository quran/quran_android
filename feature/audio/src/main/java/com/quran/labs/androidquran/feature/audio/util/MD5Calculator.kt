package com.quran.labs.androidquran.feature.audio.util

import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.source
import java.io.File

object MD5Calculator : HashCalculator {

  override fun calculateHash(file: File): String {
    return file.source().buffer().use { source ->
      val sink = HashingSink.md5(blackholeSink())
      source.readAll(sink)
      sink.hash.hex()
    }
  }
}
