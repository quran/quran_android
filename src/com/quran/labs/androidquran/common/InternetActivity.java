package com.quran.labs.androidquran.common;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.QuranDataService;

public abstract class InternetActivity extends BaseQuranActivity {
	
	protected ProgressDialog pDialog = null;
	private static boolean hideProgressBar = false;
	protected QuranDataService downloadService;
	protected AsyncTask<?, ?, ?> currentTask = null;
	protected boolean starting = true;
	private boolean bounded = false;
	protected ServiceConnection serviceConnection = new ServiceConnection() {
    	public void onServiceConnected(ComponentName name, IBinder service){
    		downloadService = ((QuranDataService.QuranDownloadBinder)service).getService();
    		starting = false;
    		currentTask = new ProgressBarUpdateTask().execute();
    		bounded = true;
    	}

    	public void onServiceDisconnected(ComponentName className) {
    		downloadService = null;
    		starting = false;
    	}
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startDownloadService(new Intent(this, QuranDataService.class));
	}
	
	public boolean isInternetOn() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm != null && cm.getActiveNetworkInfo() != null) 
			return cm.getActiveNetworkInfo().isConnectedOrConnecting();
		return false;
	}
	
	protected void connect() {
		if (isInternetOn())
        	onConnectionSuccess();
        else
        	onConnectionFailed();
	}
	
	protected void onConnectionSuccess() {
		
	}
	
	protected void onConnectionFailed() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Unable to connect to server, make sure that your Internet connection is active. Retry ?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   dialog.dismiss();
		        	   connect();
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   dialog.dismiss();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	protected void startDownloadService(Intent intent) {
    	starting = true;    	
    	
    	int downloadType = intent.getIntExtra(QuranDataService.DOWNLOAD_TYPE_KEY, -1);
    	if(downloadType != -1){
    		startService(intent);
    		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    	}else if(QuranDataService.isRunning && !bounded){
    		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);    	
    	}
    }
	
	protected void downloadTranslation(String url, String fileName) {
		Intent intent = new Intent(this, QuranDataService.class);
    	intent.putExtra(QuranDataService.DOWNLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_TRANSLATION);
    	intent.putExtra(QuranDataService.URL_KEY, url);
    	intent.putExtra(QuranDataService.FILE_NAME_KEY, fileName);
    	startDownloadService(intent);
	}
	
	protected void downloadQuranImages() {
		Intent intent = new Intent(this, QuranDataService.class);
    	intent.putExtra(QuranDataService.DOWNLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_QURAN_IMAGES);
    	startDownloadService(intent);
	}
	
	protected void downloadSura(int readerId, int sura) {
		downloadSura(readerId, sura, 1);
	}
	
	protected void downloadSura(int readerId, int sura, int ayah) {
		Intent intent = new Intent(this, QuranDataService.class);
		intent.putExtra(QuranDataService.DOWNLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_SURA_AUDIO);
		intent.putExtra(QuranDataService.SOURA_KEY, sura);
		intent.putExtra(QuranDataService.AYAH_KEY, ayah);
		intent.putExtra(QuranDataService.READER_KEY, readerId);
		startDownloadService(intent);
	}
	
	protected void downloadPage(int readerId, Integer[] integers){		
		Intent intent = new Intent(this, QuranDataService.class);
		intent.putExtra(QuranDataService.DOWNLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_SURA_AUDIO);
		intent.putExtra(QuranDataService.SOURA_KEY, integers[0]);
		intent.putExtra(QuranDataService.AYAH_KEY, integers[1]);
		intent.putExtra(QuranDataService.END_SOURA_KEY, integers[2]);
		intent.putExtra(QuranDataService.END_AYAH_KEY, integers[3]);
		intent.putExtra(QuranDataService.READER_KEY, readerId);
		startDownloadService(intent);
		
	}
	
	protected void downloadJuza(int readerId, Integer juza){
			downloadPage(readerId, QuranInfo.getJuzBounds(juza));
	}
	
    class ProgressBarUpdateTask extends AsyncTask<Void, Integer, Void> {
    	
    	private boolean callOnFinish = false;
    	
		@Override
		protected Void doInBackground(Void... params) {
			boolean wasRunning = false;
    		while (starting || QuranDataService.isRunning){
    			wasRunning = QuranDataService.isRunning;
    			try {
    				Thread.sleep(1000);
    				if ((serviceConnection != null) && (downloadService != null)){
    					int progress = downloadService.getProgress();
    					publishProgress(progress);
    				}
    			}
    			catch (InterruptedException ie){}
    		}
    		callOnFinish = true && wasRunning;
    		
    		return null;
    	}
    	
		@Override
    	public void onProgressUpdate(Integer...integers){
			int progress = integers[0];
			if (progress > 0 && !hideProgressBar) {
				starting = false;
				try {					
		    		pDialog.setProgress(progress);
				} catch (Exception e) {
					
				}
			}
    	}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showProgressDialog();
		}
    	
    	@Override
    	public void onPostExecute(Void val){
    		try {
				pDialog.dismiss();
    		} catch (Exception e) {
    			
    		}
    		hideProgressBar = false;
			pDialog = null;
			currentTask = null;
			if (callOnFinish)
				onFinishDownload();
    	}
    }
    
    protected void onFinishDownload() {
    	if (bounded){
    		unbindService(serviceConnection);
    		bounded = false;
    	}
    }
    
    protected void onDownloadCanceled() {
    	if (bounded){
    		unbindService(serviceConnection);
    		bounded = false;
    	}
    }
    
    @Override
    protected void onPause(){
    	if (bounded){
    		if (currentTask != null)
    			currentTask.cancel(true);
    		unbindService(serviceConnection);
    		bounded = false;
    	}
    	super.onPause();
    }
    
	private void showProgressDialog(){
		if (hideProgressBar)
			return;
		
    	pDialog = new ProgressDialog(InternetActivity.this);
    	pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	pDialog.setTitle(R.string.downloading_title);
    	pDialog.setCancelable(false);
    	pDialog.setButton(ProgressDialog.BUTTON1, "Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Log.d("QuranAndroid", "User canceled downloading..");
				onDownloadCanceled();
				if (serviceConnection != null) {
					if (downloadService != null) {
						downloadService.stop();
					}
				}
				stopService(new Intent(getApplicationContext(), QuranDataService.class));
				if (currentTask != null)
					currentTask.cancel(true);
				if (pDialog != null)
					pDialog.dismiss();
			}
		});
    	pDialog.setButton(ProgressDialog.BUTTON2, "Hide", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				hideProgressBar = true;
				if (pDialog != null)
					pDialog.dismiss();
			}
		});
    	
    	pDialog.setMessage(getString(R.string.downloading_message));
    	pDialog.show();
    }

}
