package com.quran.labs.androidquran.common.audio.model.playback

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioPathInfo(val urlFormat: String,
                         val localDirectory: String,
                         val gaplessDatabase: String?) : Parcelable
