package com.quran.labs.androidquran.receivers;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.quran.labs.androidquran.common.IAudioPlayer;

public class CallStateListener extends PhoneStateListener {
		
	//private AudioManager audioManager;
		
	private int currentCallState = TelephonyManager.CALL_STATE_IDLE;
	private int prevCallState = TelephonyManager.CALL_STATE_IDLE;
	private IAudioPlayer audioService;
	
	public CallStateListener(IAudioPlayer mediaPlayerService ) {
			this.audioService = mediaPlayerService;		
	}

	public void onCallStateChanged(int state, String incomingNumber) {
		super.onCallStateChanged(state, incomingNumber);
		 prevCallState = currentCallState;
		  currentCallState = state;
		switch (state) {
		case TelephonyManager.CALL_STATE_RINGING:
			if(audioService != null)
				audioService.pause();
			break;
		default:
			break;
		}		
	}
	
	public int getCurrentCallState(){
		return currentCallState;	  
	}
	  
	 public int getPreviousCallState() {
		return prevCallState;
	}
	 
	// detect whether if phone is idle or on call
	public boolean isPhoneIdle() {				
		return (currentCallState == TelephonyManager.CALL_STATE_IDLE) || 
		(currentCallState == TelephonyManager.CALL_STATE_RINGING 
				&& prevCallState == TelephonyManager.CALL_STATE_IDLE);
		
	}
}
