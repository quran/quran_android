package com.quran.labs.androidquran.dao.audio

import com.quran.labs.androidquran.data.SuraAyah

data class AudioPlaybackInfo(val currentAyah: SuraAyah,
                             val timesPlayed: Int = 1,
                             val rangePlayedTimes: Int = 1)
