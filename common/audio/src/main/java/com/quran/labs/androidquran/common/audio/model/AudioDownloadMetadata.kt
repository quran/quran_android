package com.quran.labs.androidquran.common.audio.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioDownloadMetadata(val qariId: Int) : Parcelable
