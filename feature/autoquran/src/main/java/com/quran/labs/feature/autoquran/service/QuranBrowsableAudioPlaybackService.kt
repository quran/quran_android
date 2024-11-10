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
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.quran.labs.feature.autoquran.common.BrowsableSurahBuilder
import com.quran.labs.feature.autoquran.di.QuranAutoInjector
import com.quran.mobile.di.QuranApplicationComponentProvider
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class QuranBrowsableAudioPlaybackService : MediaSessionService() {
  @Inject
  lateinit var surahBuilder: BrowsableSurahBuilder

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

  private val scope = MainScope()

  @OptIn(UnstableApi::class)
  override fun onCreate() {
    super.onCreate()
    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? QuranAutoInjector
    injector?.inject(this)

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

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    mediaSession

  private inner class PlayerEventListener : Player.Listener

  private inner class QuranServiceCallback : MediaLibrarySession.Callback {

    @OptIn(UnstableApi::class)
    override fun onGetLibraryRoot(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
      val rootExtras = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
      }
      val libraryParams = MediaLibraryService.LibraryParams.Builder().setExtras(rootExtras).build()
      return Futures.immediateFuture(LibraryResult.ofItem(rootMediaItem, libraryParams))
    }

    @OptIn(UnstableApi::class)
    override fun onGetItem(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
      val settable = SettableFuture.create<LibraryResult<MediaItem>>()
      scope.launch {
        val item = surahBuilder.child(mediaId)
        val result = if (item == null) {
          LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        } else {
          LibraryResult.ofItem(item, MediaLibraryService.LibraryParams.Builder().build())
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
      val settable = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
      scope.launch {
        val children = surahBuilder.children(parentId)
        val result =
          LibraryResult.ofItemList(children, MediaLibraryService.LibraryParams.Builder().build())
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
        val items = mediaItems.mapNotNull { surahBuilder.child(it.mediaId) }
        settable.set(items)
      }
      return settable
    }

    @OptIn(UnstableApi::class)
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
          val firstItem = mediaItems.first()
          val items = surahBuilder.expandMediaItem(firstItem.mediaId)
          val index = items.indexOfFirst { it.mediaId == firstItem.mediaId }
          val startPosition = if (index != -1) index else 0
          val result = MediaSession.MediaItemsWithStartPosition(items, startPosition, 0)
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
        session.notifySearchResultChanged(browser, query, surahBuilder.search(query).size, params)
        settable.set(LibraryResult.ofVoid(params))
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
        val items = surahBuilder.search(query)
        settable.set(
          LibraryResult.ofItemList(items, MediaLibraryService.LibraryParams.Builder().build())
        )
      }
      return settable
    }
  }
}
