package com.quran.labs.androidquran.service;

import java.io.IOException;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Binder;

import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.common.AyahStateListener;
import com.quran.labs.androidquran.common.IAudioPlayer;
import com.quran.labs.androidquran.util.QuranAudioLibrary;

public class AudioServiceBinder extends Binder implements  
	OnCompletionListener, IAudioPlayer, OnPreparedListener{
	
	private MediaPlayer mp = null;
	private Context context;
	private boolean paused = false;	
	private boolean remotePlayEnabled = false;
	private AyahItem currentItem;
	private AyahStateListener ayahListener = null;
	
	public void setAyahCompleteListener(AyahStateListener ayahListener) {
		this.ayahListener = ayahListener;
	}



	public AudioServiceBinder(Context context){
		this.context = context;
	}
	

	
	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#stop()
	 */
	public void stop() {
		if(mp != null && mp.isPlaying())
				mp.stop();		
	}

	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#play(org.islam.quran.AyahAudioItem)
	 */
	public void play(AyahItem item) {
		this.currentItem = item;
		if(mp != null){
			mp.stop();
			mp.release();
			mp = null;
		}
		try {
			if(mp == null)
				mp = new MediaPlayer();			
			mp.reset();
			mp.setOnCompletionListener(this);
			String url = null;
			if(item.isAudioFoundLocally())
				url = item.getLocalAudioUrl();
			else if(remotePlayEnabled) {
				url = item.getRemoteAudioUrl();
			}else{
				if(ayahListener != null)
					ayahListener.onNotFound(item);
				else
				{
					// show notification ayah not found, download it or play remotely	
				}
			}	
			if(url != null){				
				mp.setDataSource(url);
				mp.prepare();
				mp.start();
			}
			
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#getMediaPlayer()
	 */
	public MediaPlayer getMediaPlayer() {
		if(mp == null){
			mp = new MediaPlayer();
			mp.setOnCompletionListener(this);
		}
		return mp;
	}


	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#pause()
	 */
	public void pause() {
		if(mp != null && mp.isPlaying()){			
			mp.pause();					
		}
		paused = true;
	}

	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#resume()
	 */
	public void resume() {
		if(mp != null && !mp.isPlaying()){
			paused = false;
			mp.start();
		}
	}

	public boolean isPaused() {
		return paused;
	}

	

	@Override
	public void onCompletion(MediaPlayer mp) {
		AyahItem nextItem = QuranAudioLibrary.getNextAyahAudioItem(context, this.currentItem);
		if(ayahListener != null)
			ayahListener.onComplete(currentItem, nextItem);
		if(nextItem != null){
			this.currentItem = nextItem;
			this.play(currentItem);
		}
	}		
	
	public void destory(){
		if(mp != null){
			mp.stop();
			mp.release();
		}
	}



	@Override
	public void onPrepared(MediaPlayer arg0) {
		if(mp != null)
			mp.start();	
	}
	

	public AyahItem getCurrentAyah(){
		return currentItem;
	}
	
	
	public void enableRemotePlay(boolean remote){
		this.remotePlayEnabled = remote;
	}
	
	public boolean isRemotePlayEnabled(){
		return this.remotePlayEnabled;
	}
	
};
