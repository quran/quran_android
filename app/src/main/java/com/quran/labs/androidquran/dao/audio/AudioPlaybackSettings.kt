package com.quran.labs.androidquran.dao.audio

import android.os.Parcelable
import com.quran.data.model.SuraAyah
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AudioPlaybackSettings(val start: SuraAyah,
                                 val end: SuraAyah,
                                 val verseRepeatCount: Int = 0,
                                 val rangeRepeatCount: Int = 0,
                                 val enforceRange: Boolean = false) : Parcelable
