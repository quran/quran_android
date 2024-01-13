package com.quran.labs.androidquran.common.audio.model.download

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioDownloadMetadata(val qariId: Int) : Parcelable
