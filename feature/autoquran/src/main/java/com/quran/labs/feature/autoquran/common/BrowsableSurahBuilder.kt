package com.quran.labs.feature.autoquran.common

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.google.common.collect.ImmutableList
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import com.quran.mobile.di.qualifier.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BrowsableSurahBuilder @Inject constructor(@ApplicationContext private val appContext: Context,
                                                private val pageProvider: PageProvider) {

  suspend fun children(parentId: String): ImmutableList<MediaItem> {
    return withContext(Dispatchers.IO) {
      if (parentId == "root") {
        val items = pageProvider.getQaris()
          .filter { it.isGapless }
          .take(2)
          .map { qari -> makeMediaItem(qari) }
        ImmutableList.copyOf(items)
      } else {
        val qariId = parentId.substringAfterLast("_").toIntOrNull() ?: -1
        val qari = pageProvider.getQaris().firstOrNull { it.id == qariId }
        if (qari != null) {
          (1..114).map { sura ->
            makeSuraMediaItem(qari, sura)
          }.let { ImmutableList.copyOf(it) }
        } else {
          ImmutableList.of()
        }
      }
    }
  }

  suspend fun child(mediaId: String): MediaItem? {
    return withContext(Dispatchers.IO) {
      val qariId = mediaId.substringAfterLast("_").toIntOrNull() ?: -1
      val qari = pageProvider.getQaris().firstOrNull { it.id == qariId }
      val isSura = mediaId.startsWith("sura_")
      if (isSura) {
        val sura = mediaId.substringAfter("_").substringBefore("_").toIntOrNull() ?: -1
        if (qari != null && sura in 1..114) {
          makeSuraMediaItem(qari, sura)
        } else {
          null
        }
      } else {
        null
      }
    }
  }

  private fun makeMediaItem(qari: Qari): MediaItem {
    val mediaId = "quran_${qari.id}"
    return MediaItem.Builder()
      .setMediaId(mediaId)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(appContext.getString(qari.nameResource))
          .setIsBrowsable(true)
          .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
          .setIsPlayable(false)
          .build()
      )
      .build()
  }

  private fun makeSuraMediaItem(qari: Qari, sura: Int): MediaItem {
    val suraName = getSuraName(appContext, sura, true, false)
    return MediaItem.Builder()
      .setMediaId("sura_${sura}_${qari.id}")
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setIsBrowsable(false)
          .setIsPlayable(true)
          .setTitle(suraName)
          .setDisplayTitle(suraName)
          .setTrackNumber(sura)
          .setTotalTrackCount(114)
          .setArtist(appContext.getString(qari.nameResource))
          .build()
      )
      .setMimeType(MimeTypes.AUDIO_MPEG)
      .setUri(qari.url + makeThreeDigit(sura) + ".mp3")
      .build()
  }
}
