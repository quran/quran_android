package com.quran.labs.feature.autoquran.common

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.google.common.collect.ImmutableList
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.feature.autoquran.R
import com.quran.mobile.common.ui.core.R as UiCoreR
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrowsableSurahBuilder @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val pageProvider: PageProvider,
  private val audioExtensionDecider: AudioExtensionDecider,
  private val qariArtworkProvider: QariArtworkProvider,
  private val recentQariManager: RecentQariManager,
) {

  private val qariMediaItem: MediaItem by lazy {
    MediaItem.Builder()
      .setMediaId(QARI_ID)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(appContext.getString(R.string.autoquran_qaris_title))
          .setIsBrowsable(true)
          .setMediaType(MediaMetadata.MEDIA_TYPE_MIXED)
          .setIsPlayable(false)
          .build()
      )
      .build()
  }

  private val recentMediaItem: MediaItem by lazy {
    MediaItem.Builder()
      .setMediaId(RECENT_ID)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(appContext.getString(R.string.autoquran_recently_played_title))
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
        val qaris = pageProvider.getQaris()
        if (recentChildren(qaris).isNotEmpty()) {
          ImmutableList.of(recentMediaItem, qariMediaItem)
        } else {
          ImmutableList.of(qariMediaItem)
        }
      } else if (parentId == RECENT_ID) {
        recentChildren()
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
      when (mediaId) {
        RECENT_ID -> return@withContext recentMediaItem
        QARI_ID -> return@withContext qariMediaItem
      }

      val qariId = mediaId.substringAfterLast("_").toIntOrNull() ?: -1
      val qari = pageProvider.getQaris().firstOrNull { it.id == qariId }
      when {
        mediaId.startsWith("quran_") && qari != null -> makeMediaItem(qari)
        mediaId.startsWith("sura_") -> {
          val sura = mediaId.substringAfter("_").substringBefore("_").toIntOrNull() ?: -1
          if (qari != null && sura in 1..114) {
            makeSuraMediaItem(qari, sura)
          } else {
            null
          }
        }
        else -> null
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
   * Return playable [MediaItem]s for the most recently played qaris.
   * Each item represents the last sura played for that qari.
   */
  private fun recentChildren(): ImmutableList<MediaItem> {
    val qaris = pageProvider.getQaris()
    return recentChildren(qaris)
  }

  private fun recentChildren(qaris: List<Qari>): ImmutableList<MediaItem> {
    val recents = recentQariManager.getRecentQaris()
    if (recents.isEmpty()) return ImmutableList.of()
    val items = recents.mapNotNull { recent ->
      val qari = qaris.firstOrNull { it.id == recent.qariId }
      if (qari != null && recent.lastSura in 1..114) {
        makeSuraMediaItem(qari, recent.lastSura, showQariSubtitle = true)
      } else {
        null
      }
    }
    return ImmutableList.copyOf(items)
  }

  /**
   * Given the id of a [Qari], return all the [MediaItem]s for that qari.
   * Typically, this is a list of 114 [MediaItem]s, one for each sura.
   */
  private fun suraMediaItemsForQariId(qariId: Int): ImmutableList<MediaItem> {
    val qari = pageProvider.getQaris().firstOrNull { it.id == qariId } ?: return ImmutableList.of()
    val suraNames = appContext.resources.getStringArray(UiCoreR.array.sura_names)
    val qariName = appContext.getString(qari.nameResource)
    val extension = audioExtensionDecider.audioExtensionForQari(qari)
    val (baseUrl, mimeType) = audioUrlAndMimeType(qari, extension)

    return (1..114).map { sura ->
      val suraName = appContext.getString(R.string.quran_sura_title, suraNames[sura - 1])
      buildSuraMediaItem(
        qari = qari,
        sura = sura,
        suraName = suraName,
        qariName = qariName,
        extension = extension,
        baseUrl = baseUrl,
        mimeType = mimeType,
        showQariSubtitle = false,
      )
    }.let { ImmutableList.copyOf(it) }
  }

  /**
   * Make a [MediaItem] representing a [Qari] folder
   */
  private fun makeMediaItem(qari: Qari): MediaItem {
    val mediaId = "quran_${qari.id}"
    val artworkUri = qariArtworkProvider.artworkUriFor(qari)
    return MediaItem.Builder()
      .setMediaId(mediaId)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(appContext.getString(qari.nameResource))
          .setIsBrowsable(true)
          .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
          .apply { setArtworkUri(artworkUri) }
          .setIsPlayable(false)
          .build()
      )
      .build()
  }

  /**
   * Make a [MediaItem] representing a sura for a [Qari]
   */
  private fun makeSuraMediaItem(
    qari: Qari,
    sura: Int,
    showQariSubtitle: Boolean = false
  ): MediaItem {
    val suraName = getSuraName(appContext, sura, wantPrefix = true, wantTranslation = false)
    val extension = audioExtensionDecider.audioExtensionForQari(qari)
    val (baseUrl, mimeType) = audioUrlAndMimeType(qari, extension)
    val qariName = appContext.getString(qari.nameResource)
    return buildSuraMediaItem(
      qari = qari,
      sura = sura,
      suraName = suraName,
      qariName = qariName,
      extension = extension,
      baseUrl = baseUrl,
      mimeType = mimeType,
      showQariSubtitle = showQariSubtitle,
    )
  }

  private fun audioUrlAndMimeType(qari: Qari, extension: String): Pair<String, String> {
    val opusUrl = qari.opusUrl
    return if (extension == "opus" && opusUrl != null) {
      opusUrl to MimeTypes.AUDIO_OPUS
    } else {
      qari.url to MimeTypes.AUDIO_MPEG
    }
  }

  private fun buildSuraMediaItem(
    qari: Qari,
    sura: Int,
    suraName: String,
    qariName: String,
    extension: String,
    baseUrl: String,
    mimeType: String,
    showQariSubtitle: Boolean,
  ): MediaItem {
    val artworkUri = qariArtworkProvider.suraArtworkUriFor(qari, sura)
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
          .setArtist(qariName)
          .apply {
            if (showQariSubtitle) setSubtitle(qariName)
            setArtworkUri(artworkUri)
          }
          .build()
      )
      .setMimeType(mimeType)
      .setUri(baseUrl + makeThreeDigit(sura) + ".$extension")
      .build()
  }

  companion object {
    const val ROOT_ID = "__ROOT__"
    const val QARI_ID = "__QARI__"
    const val RECENT_ID = "__RECENT__"
  }
}
