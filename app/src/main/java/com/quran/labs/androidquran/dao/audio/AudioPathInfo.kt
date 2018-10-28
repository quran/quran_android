package com.quran.labs.androidquran.dao.audio

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AudioPathInfo(val urlFormat: String,
                         val localDirectory: String,
                         val gaplessDatabase: String?) : Parcelable
