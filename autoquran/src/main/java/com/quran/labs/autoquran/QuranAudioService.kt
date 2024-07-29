package com.quran.labs.autoquran

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.text.TextUtils
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import com.quran.data.core.QuranConstants
import com.quran.data.core.QuranInfo
import com.quran.labs.autoquran.di.DaggerServiceComponent
import javax.inject.Inject


/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 *
 *
 * To implement a MediaBrowserService, you need to:
 *
 *
 *
 *  *  Extend [MediaBrowserServiceCompat], implementing the media browsing
 * related methods [MediaBrowserServiceCompat.onGetRoot] and
 * [MediaBrowserServiceCompat.onLoadChildren];
 *  *  In onCreate, start a new [MediaSessionCompat] and notify its parent
 * with the session"s token [MediaBrowserServiceCompat.setSessionToken];
 *
 *  *  Set a callback on the [MediaSessionCompat.setCallback].
 * The callback will receive all the user"s actions, like play, pause, etc;
 *
 *  *  Handle all the actual music playing using any method your app prefers (for example,
 * [android.media.MediaPlayer])
 *
 *  *  Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * [MediaSessionCompat.setPlaybackState]
 * [MediaSessionCompat.setMetadata] and
 * [MediaSessionCompat.setQueue])
 *
 *  *  Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 *
 *
 *
 *
 * To make your app compatible with Android Auto, you also need to:
 *
 *
 *
 *  *  Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 *
 *
 */
class QuranAudioService : MediaBrowserServiceCompat(), MediaPlayer.OnPreparedListener {
  @Inject
  lateinit var quranInfo: QuranInfo
  private lateinit var mSession: MediaSessionCompat
  var mediaPlayer: MediaPlayer? = null
  val mediaItems = mutableListOf(
      createBrowsableMediaItem("quran", "Quran")
  )

  override fun onCreate() {
    super.onCreate()
    mSession = MediaSessionCompat(baseContext, "QuranAudioService")
    setSessionToken(mSession.sessionToken)
    mSession.setCallback(MediaSessionCallback())
    val component = DaggerServiceComponent.create()
    component.inject(this)
    quranInfo.suraPageStart.forEachIndexed { index, page ->
      val name = getSuraName(this, index + 1, true, false)
      mediaItems.add(
          createMediaItem(
              name, name, "Quran", "https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/${makeThreeDigit(index + 1)}.mp3".toUri()
          )
      )
    }
  }

  override fun onDestroy() {
    mSession.release()
    mediaPlayer?.release()
  }

  override fun onGetRoot(clientPackageName: String,
                         clientUid: Int,
                         rootHints: Bundle?): BrowserRoot {
    return BrowserRoot("root", null)
  }

  override fun onLoadChildren(parentMediaId: String,
                              result: Result<List<MediaItem>>) {
    result.sendResult(mediaItems)
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
        MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM)
    extras.putInt(
        MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
        MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    extras.putInt(
        MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
        MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
    mediaDescriptionBuilder.setExtras(extras)
    return MediaItem(
        mediaDescriptionBuilder.build(), MediaItem.FLAG_BROWSABLE)
  }

  private fun createMediaItem(mediaId: String, title: String, artist: String, uri: Uri): MediaItem {
    val playbackStateExtras = Bundle()
    playbackStateExtras.putString(
        MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID, mediaId)
    val description = MediaDescriptionCompat.Builder()
        .setMediaId(mediaId)
        .setTitle(title)
        .setSubtitle(artist)
        .setMediaUri(uri)
        .setExtras(playbackStateExtras)
        .build()

    return MediaItem(description, MediaItem.FLAG_PLAYABLE)
  }

  override fun onPrepared(mp: MediaPlayer) {
    mp.start()
  }

  private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
    override fun onPlay() {
      if (mediaPlayer?.isPlaying == false) {
        mediaPlayer?.start()
      }
    }

    override fun onSkipToQueueItem(queueId: Long) {}
    override fun onSeekTo(position: Long) {}
    override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
      val mediaUrl: MediaItem? = mediaItems.find { mediaId == it.description.mediaId }
      if (mediaUrl != null) {
        mediaPlayer = MediaPlayer().apply {
          setAudioAttributes(
              AudioAttributes.Builder()
                  .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                  .build()
          )
          mSession.setMetadata(
              MediaMetadataCompat.Builder()
                  .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaUrl.description.title.toString())
                  .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaUrl.description.subtitle.toString())
                  .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaUrl.description.mediaId.toString())
                  .build()
          )
          setOnPreparedListener(this@QuranAudioService)
          setDataSource(baseContext, mediaUrl.description.mediaUri ?: Uri.EMPTY)
          prepareAsync()
        }
      }
    }

    override fun onPause() {
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.pause()
      }
    }

    override fun onStop() {
      mediaPlayer?.stop()
    }

    override fun onSkipToNext() {}
    override fun onSkipToPrevious() {}
    override fun onCustomAction(action: String, extras: Bundle) {}
    override fun onPlayFromSearch(query: String, extras: Bundle) {}
  }

  private fun getSuraName(
      context: Context, sura: Int, wantPrefix: Boolean, wantTranslation: Boolean
  ): String {
    if (sura < QuranConstants.FIRST_SURA || sura > QuranConstants.LAST_SURA) return ""

    val builder = StringBuilder()
    val suraNames = context.resources.getStringArray(R.array.sura_names)
    if (wantPrefix) {
      builder.append(context.getString(R.string.quran_sura_title, suraNames[sura - 1]))
    } else {
      builder.append(suraNames[sura - 1])
    }
    if (wantTranslation) {
      val translation = context.resources.getStringArray(R.array.sura_names_translation)[sura - 1]
      if (!TextUtils.isEmpty(translation)) {
        // Some sura names may not have translation
        builder.append(" (")
        builder.append(translation)
        builder.append(")")
      }
    }

    return builder.toString()
  }

  private fun makeThreeDigit(number: Int): String {
    var numStr = number.toString()
    if (numStr.length < 3) {
      numStr = "0".repeat(3 - numStr.length) + numStr
    }
    return numStr
  }
}
