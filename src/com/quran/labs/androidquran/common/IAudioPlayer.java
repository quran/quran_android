package com.quran.labs.androidquran.common;

import android.media.MediaPlayer;

public interface IAudioPlayer {
	
	public abstract void stop();

	public abstract void play(AyahItem item);

	public abstract MediaPlayer getMediaPlayer();

	public abstract void pause();

	public abstract void resume();
	
	public void enableRemotePlay(boolean remote);
	
	public boolean isRemotePlayEnabled();

}
