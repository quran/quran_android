package com.quran.labs.androidquran.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.util.Log;

public final class CachingManager implements OnPreparedListener {

	
	private HashMap<String, MediaPlayer> cachingURLsToMediaPlayers;
	private HashMap<String, MediaPlayer> cachedURLsToMediaPlayers;
	private Vector<String> toCacheQueue;
	private HashMap<String, OnPreparedListener> cachedURLsToMediaPlayerListerner;
	
	static CachingManager instance;
	
	public CachingManager() {
		cachedURLsToMediaPlayers = new HashMap<String, MediaPlayer>();
		cachingURLsToMediaPlayers = new HashMap<String, MediaPlayer>();
		cachedURLsToMediaPlayerListerner = new HashMap<String, MediaPlayer.OnPreparedListener>();
		toCacheQueue = new Vector<String>();
	}
	
	synchronized public boolean isCachingURL(String url) {
		return toCacheQueue.contains(url) || cachingURLsToMediaPlayers.get(url) != null;
	}
	
	synchronized public MediaPlayer consumeCachedMediaPlayer(String url) {
		MediaPlayer consumedMediaPlayer = cachedURLsToMediaPlayers.get(url);
		cachedURLsToMediaPlayers.remove(url);
		return consumedMediaPlayer;
	}
	
	synchronized public boolean isCachedURL(String url) {
		return cachedURLsToMediaPlayers.get(url) != null;
	}
	
	private void startCaching() {
		String url = toCacheQueue.firstElement();
		MediaPlayer cachingMediaPlayer = new MediaPlayer();
		try {
			cachingMediaPlayer.setDataSource(url);
			cachingMediaPlayer.setOnPreparedListener(this);
			cachingMediaPlayer.prepareAsync();
			Log.d("Caching start", url);
			cachingURLsToMediaPlayers.put(url, cachingMediaPlayer);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	synchronized public void cacheStreamWithURL(String url) {
		if(!isCachingURL(url) && !isCachedURL(url)) {
			toCacheQueue.add(url);
			if (toCacheQueue.size() == 1) {
				startCaching();
			}
		}
	}

	@Override
	synchronized public void onPrepared(MediaPlayer cachedMediaPlayer) {
		String url = toCacheQueue.firstElement();
		Log.d("Caching end", url);
		toCacheQueue.removeElement(url);
		cachedURLsToMediaPlayers.put(url, cachedMediaPlayer);
		cachingURLsToMediaPlayers.remove(url);
		if (toCacheQueue.size() > 0) {
			startCaching();
		}
		
		OnPreparedListener cachingListener = cachedURLsToMediaPlayerListerner.get(url);
		if (cachingListener != null) {
			cachedURLsToMediaPlayerListerner.remove(url);
			cachedMediaPlayer.setOnCompletionListener((OnCompletionListener) cachingListener);
			cachingListener.onPrepared(cachedMediaPlayer);
			consumeCachedMediaPlayer(url);
		}
	}
	
	synchronized public void setStreamCachingListenerForURL(OnPreparedListener listener, String url) {
		cachedURLsToMediaPlayerListerner.put(url, listener);
	}

}
