package com.quran.labs.androidquran.common.audio

import androidx.annotation.ArrayRes

data class AudioConfiguration(
  @ArrayRes val quranReadersName: Int,
  @ArrayRes val quranReadersPath: Int,
  @ArrayRes val quranReadersUrls: Int,
  @ArrayRes val quranReadersDatabaseNames: Int,
  @ArrayRes val quranReadersHaveGaplessEquivalents: Int
)
