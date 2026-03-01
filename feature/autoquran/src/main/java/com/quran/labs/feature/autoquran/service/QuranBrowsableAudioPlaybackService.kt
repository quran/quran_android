package com.quran.labs.feature.autoquran.service

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.quran.labs.feature.autoquran.common.BrowsableSurahBuilder
import com.quran.labs.feature.autoquran.common.RecentQariManager
import com.quran.labs.feature.autoquran.di.QuranAutoInjector
import com.quran.mobile.di.QuranApplicationComponentProvider
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(UnstableApi::class)
class QuranBrowsableAudioPlaybackService : MediaLibraryService() {
  @Inject
  lateinit var surahBuilder: BrowsableSurahBuilder

  @Inject
  lateinit var recentQariManager: RecentQariManager

  private var mediaSession: MediaLibrarySession? = null

  private val playerListener = PlayerEventListener()
  private val quranAudioAttributes = AudioAttributes.Builder()
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .setUsage(C.USAGE_MEDIA)
    .build()

  private val exoPlayer: Player by lazy {
    ExoPlayer.Builder(this).build().apply {
      setAudioAttributes(quranAudioAttributes, true)
      setHandleAudioBecomingNoisy(true)
      addListener(playerListener)
    }
  }

  private val rootMediaItem: MediaItem by lazy {
    MediaItem.Builder()
      .setMediaId(BrowsableSurahBuilder.ROOT_ID)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setIsBrowsable(true)
          .setMediaType(MediaMetadata.MEDIA_TYPE_MIXED)
          .setIsPlayable(false)
          .build()
      )
      .build()
  }

  private val recentRootMediaItem: MediaItem by lazy {
    MediaItem.Builder()
      .setMediaId(BrowsableSurahBuilder.RECENT_ID)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setIsBrowsable(true)
          .setMediaType(MediaMetadata.MEDIA_TYPE_MIXED)
          .setIsPlayable(false)
          .build()
      )
      .build()
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? QuranAutoInjector
    if (injector == null) {
      Timber.e("Unable to inject QuranBrowsableAudioPlaybackService (component missing or wrong type)")
    } else {
      injector.inject(this)
    }

    mediaSession = MediaLibrarySession.Builder(this, exoPlayer, QuranServiceCallback()).build()
  }

  override fun onDestroy() {
    scope.cancel()
    val mediaSession = mediaSession
    if (mediaSession != null) {
      mediaSession.player.apply {
        removeListener(playerListener)
        release()
      }
      mediaSession.release()
      this.mediaSession = null
    } else {
      exoPlayer.apply {
        removeListener(playerListener)
        release()
      }
    }
    super.onDestroy()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
    mediaSession

  private inner class PlayerEventListener : Player.Listener {
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
      val mediaId = mediaItem?.mediaId ?: return
      if (!mediaId.startsWith("sura_")) return
      val parts = mediaId.split("_")
      if (parts.size != 3) return
      val sura = parts[1].toIntOrNull() ?: return
      val qariId = parts[2].toIntOrNull() ?: return
      if (::recentQariManager.isInitialized) {
        recentQariManager.recordQari(qariId, sura)
        val recentCount = recentQariManager.getRecentQaris().size
        mediaSession?.notifyChildrenChanged(
          BrowsableSurahBuilder.RECENT_ID, recentCount, null
        )
        mediaSession?.notifyChildrenChanged(
          BrowsableSurahBuilder.ROOT_ID, recentCount + 1, null
        )
      }
    }
  }

  private inner class QuranServiceCallback : MediaLibrarySession.Callback {

    override fun onSubscribe(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      parentId: String,
      params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
      return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetLibraryRoot(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
      if (params?.isRecent == true) {
        val recentParams = MediaLibraryService.LibraryParams.Builder()
          .setRecent(true)
          .build()
        return Futures.immediateFuture(
          LibraryResult.ofItem(recentRootMediaItem, recentParams)
        )
      }
      val rootExtras = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
      }
      val libraryParams = MediaLibraryService.LibraryParams.Builder().setExtras(rootExtras).build()
      return Futures.immediateFuture(LibraryResult.ofItem(rootMediaItem, libraryParams))
    }

    override fun onGetItem(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
      val settable = SettableFuture.create<LibraryResult<MediaItem>>()
      scope.launch {
        val result = runCatching {
          val item = surahBuilder.child(mediaId)
          if (item == null) {
            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
          } else {
            LibraryResult.ofItem(item, MediaLibraryService.LibraryParams.Builder().build())
          }
        }.getOrElse { t ->
          Timber.e("onGetItem failed for mediaId=$mediaId", t)
          LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
        }
        settable.set(result)
      }
      return settable
    }

    override fun onGetChildren(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      parentId: String,
      page: Int,
      pageSize: Int,
      params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
      Timber.d("onGetChildren(parentId=$parentId page=$page pageSize=$pageSize)")
      val settable = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
      scope.launch {
        val result = runCatching {
          val children = surahBuilder.children(parentId)
          LibraryResult.ofItemList(children, MediaLibraryService.LibraryParams.Builder().build())
        }.getOrElse { t ->
          Timber.e("onGetChildren failed for parentId=$parentId", t)
          // Important: always respond, otherwise Android Auto can show an infinite spinner.
          LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
        }
        settable.set(result)
      }
      return settable
    }

    override fun onAddMediaItems(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
      val settable = SettableFuture.create<List<MediaItem>>()
      scope.launch {
        val items = runCatching {
          mediaItems.mapNotNull { surahBuilder.child(it.mediaId) }
        }.getOrElse { t ->
          Timber.e("onAddMediaItems failed", t)
          emptyList()
        }
        settable.set(items)
      }
      return settable
    }

    override fun onSetMediaItems(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItems: List<MediaItem>,
      startIndex: Int,
      startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
      return if (mediaItems.size == 1) {
        val settable = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
          val result = runCatching {
            val firstItem = mediaItems.first()
            val items = surahBuilder.expandMediaItem(firstItem.mediaId)
            val index = items.indexOfFirst { it.mediaId == firstItem.mediaId }
            val startPosition = if (index != -1) index else 0
            MediaSession.MediaItemsWithStartPosition(items, startPosition, 0)
          }.getOrElse { t ->
            Timber.e("onSetMediaItems failed", t)
            MediaSession.MediaItemsWithStartPosition(ImmutableList.of(), 0, 0)
          }
          settable.set(result)
        }
        settable
      } else {
        super.onSetMediaItems(
          mediaSession,
          controller,
          mediaItems,
          startIndex,
          startPositionMs
        )
      }
    }

    override fun onSearch(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      query: String,
      params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
      val settable = SettableFuture.create<LibraryResult<Void>>()
      scope.launch {
        val result = runCatching {
          session.notifySearchResultChanged(browser, query, surahBuilder.search(query).size, params)
          LibraryResult.ofVoid(params)
        }.getOrElse { t ->
          Timber.e("onSearch failed for query=$query", t)
          LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
        }
        settable.set(result)
      }
      return settable
    }

    override fun onGetSearchResult(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      query: String,
      page: Int,
      pageSize: Int,
      params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
      val settable = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
      scope.launch {
        val result = runCatching {
          val items = surahBuilder.search(query)
          LibraryResult.ofItemList(items, MediaLibraryService.LibraryParams.Builder().build())
        }.getOrElse { t ->
          Timber.e("onGetSearchResult failed for query=$query", t)
          LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
        }
        settable.set(result)
      }
      return settable
    }
  }

}
