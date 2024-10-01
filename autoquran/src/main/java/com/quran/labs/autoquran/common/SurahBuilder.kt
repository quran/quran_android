package com.quran.labs.autoquran.common

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.core.net.toUri
import androidx.media.utils.MediaConstants
import com.quran.data.core.QuranInfo
import com.quran.data.source.PageProvider
import javax.inject.Inject


class SurahBuilder @Inject constructor(
  private val quranInfo: QuranInfo,
  private val pageProvider: PageProvider
) {

  fun create(context: Context): MutableList<MediaItem> {
    val mediaItems = mutableListOf<MediaItem>()
    pageProvider.getQaris().filter { it.isGapless }.forEach { qari ->
      mediaItems.add(
        createBrowsableMediaItem(
          "quran_${qari.id}",
          "Quran(${context.getString(qari.nameResource)})"
        )
      )
      quranInfo.suraPageStart.forEachIndexed { index, _ ->
        val name = getSuraName(context, index + 1, true, false)
        mediaItems.add(
          createMediaItem(
            "${name}_${qari.id}",
            name,
            context.getString(qari.nameResource),
            "${qari.url}/${
              makeThreeDigit(index + 1)
            }.mp3".toUri()
          )
        )
      }
    }
    return mediaItems
  }

  private fun createBrowsableMediaItem(
    mediaId: String,
    folderName: String
  ): MediaItem {
    val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
    mediaDescriptionBuilder.setMediaId(mediaId)
    mediaDescriptionBuilder.setTitle(folderName)
    val extras = Bundle()
    extras.putInt(
      MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
      MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
    )
    extras.putInt(
      MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
      MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
    )
    extras.putInt(
      MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
      MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    )
    extras.putDouble(
      MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE,
      0.0
    )
    mediaDescriptionBuilder.setExtras(extras)
    return MediaItem(
      mediaDescriptionBuilder.build(), MediaItem.FLAG_BROWSABLE
    )
  }

  private fun createMediaItem(mediaId: String, title: String, artist: String, uri: Uri): MediaItem {
    val playbackStateExtras = Bundle()
    playbackStateExtras.putString(
      MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID, mediaId
    )
    val description = MediaDescriptionCompat.Builder()
      .setMediaId(mediaId)
      .setTitle(title)
      .setSubtitle(artist)
      .setMediaUri(uri)
      .setExtras(playbackStateExtras)
      .build()

    return MediaItem(description, MediaItem.FLAG_PLAYABLE)
  }
}

fun List<MediaItem>.filterWithQari(parentMediaId: String): List<MediaItem> {
  return filter { it.mediaId?.endsWith(parentMediaId.split("_")[1]) == true }
}
