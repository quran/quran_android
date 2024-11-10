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

  private val qariMediaItem: MediaItem by lazy {
    MediaItem.Builder()
      .setMediaId(QARI_ID)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setIsBrowsable(true)
          .setMediaType(MediaMetadata.MEDIA_TYPE_MIXED)
          .setIsPlayable(false)
          .build()
      )
      .build()
  }

  /**
   * Get a list of child [MediaItem]s for a given media id
   */
  suspend fun children(parentId: String): ImmutableList<MediaItem> {
    return withContext(Dispatchers.IO) {
      if (parentId == ROOT_ID) {
        ImmutableList.of(qariMediaItem)
      } else if (parentId == QARI_ID) {
        val items = pageProvider.getQaris()
          .filter { it.isGapless }
          .take(2)
          .map { qari -> makeMediaItem(qari) }
        ImmutableList.copyOf(items)
      } else {
        val qariId = parentId.substringAfterLast("_").toIntOrNull() ?: -1
        suraMediaItemsForQariId(qariId)
      }
    }
  }

  /**
   * Get a single [MediaItem] for a given media id.
   */
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

  /**
   * Given a [MediaItem], return a list of [MediaItem]s to use as a playlist,
   * including this particular item. This is so that if a person selects a
   * sura, they get the entire mushaf in the queue with the current position
   * being set to the sura's position.
   */
  suspend fun expandMediaItem(mediaId: String): ImmutableList<MediaItem> {
    return withContext(Dispatchers.IO) {
      val qariId = mediaId.substringAfterLast("_").toIntOrNull() ?: -1
      suraMediaItemsForQariId(qariId)
    }
  }

  suspend fun search(query: String): List<MediaItem> {
    return withContext(Dispatchers.IO) {
      val matchingQaris = pageProvider.getQaris()
        .filter { it.isGapless && appContext.getString(it.nameResource).contains(query, true) }
        .map { makeMediaItem(it) }

      // TODO: add sura search
      matchingQaris
    }
  }

  /**
   * Given the id of a [Qari], return all the [MediaItem]s for that qari.
   * Typically, this is a list of 114 [MediaItem]s, one for each sura.
   */
  private fun suraMediaItemsForQariId(qariId: Int): ImmutableList<MediaItem> {
    val qari = pageProvider.getQaris().firstOrNull { it.id == qariId }
    return if (qari != null) {
      (1..114).map { sura ->
        makeSuraMediaItem(qari, sura)
      }.let { ImmutableList.copyOf(it) }
    } else {
      ImmutableList.of()
    }
  }

  /**
   * Make a [MediaItem] representing a [Qari] folder
   */
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

  /**
   * Make a [MediaItem] representing a sura for a [Qari]
   */
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

  companion object {
    const val ROOT_ID = "__ROOT__"
    const val QARI_ID = "__QARI__"
  }
}
