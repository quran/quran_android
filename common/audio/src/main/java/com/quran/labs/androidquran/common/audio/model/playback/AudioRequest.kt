package com.quran.labs.androidquran.common.audio.model.playback

import android.os.Parcelable
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioRequest(val start: SuraAyah,
                        val end: SuraAyah,
                        val qari: QariItem,
                        val repeatInfo: Int = 0,
                        val rangeRepeatInfo: Int = 0,
                        val enforceBounds: Boolean,
                        val playbackSpeed: Float = 1f,
                        val shouldStream: Boolean,
                        val audioPathInfo: AudioPathInfo
) : Parcelable {
  fun isGapless() = qari.isGapless
  fun needsIsti3athaAudio() =
      !isGapless() || audioPathInfo.gaplessDatabase?.contains("minshawi_murattal") ?: false
}
