package com.quran.labs.androidquran.service;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.quran.labs.androidquran.receivers.CallStateListener;

public class QuranAudioService extends Service {

	private PhoneStateListener psl;
	private AudioServiceBinder mBinder = new AudioServiceBinder(this);
	
	@Override
	public void onCreate() {		
		super.onCreate();
		psl = new CallStateListener(mBinder);
		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE))
			.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);	
	}
	
	@Override
	public void onDestroy() {
		try{
			mBinder.destroy();
		}catch(Exception e){}
		finally{
			super.onDestroy();
			//Toast.makeText(this, "Service destroyed...", Toast.LENGTH_LONG).show();
		}		
	}
	@Override
	public IBinder onBind(Intent arg0) {		
		return mBinder;
	}
	
	// only runs on pre-2.0 sdks
	@Override
	public boolean onUnbind(Intent intent) {
		// TODO Auto-generated method stub
		return true;
	}
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}
	
	// 2.0+ sdks
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	

}
