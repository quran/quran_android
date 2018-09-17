package com.quran.labs.androidquran.dao.audio

import com.quran.labs.androidquran.common.QariItem
import com.quran.labs.androidquran.data.SuraAyah

data class AudioRequest(val start: SuraAyah,
                        val end: SuraAyah,
                        val qari: QariItem,
                        val repeatInfo: Int = 0,
                        val rangeRepeatInfo: Int = 0,
                        val enforceBounds: Boolean,
                        val shouldStream: Boolean)
