package com.quran.labs.androidquran.common.audio.model

import androidx.annotation.ArrayRes

data class AudioConfiguration(
  @ArrayRes val quranReadersName: Int,
  @ArrayRes val quranReadersPath: Int,
  @ArrayRes val quranReadersUrls: Int,
  @ArrayRes val quranReadersDatabaseNames: Int,
  @ArrayRes val quranReadersHaveGaplessEquivalents: Int
)
