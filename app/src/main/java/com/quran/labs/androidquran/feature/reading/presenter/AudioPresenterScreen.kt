package com.quran.labs.androidquran.feature.reading.presenter

import android.content.Intent
import androidx.annotation.StringRes
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest

interface AudioPresenterScreen {
  fun handlePlayback(request: AudioRequest?)
  fun handleRequiredDownload(downloadIntent: Intent?)
  fun proceedWithDownload(downloadIntent: Intent?)
  fun getString(@StringRes resId: Int): String
}
