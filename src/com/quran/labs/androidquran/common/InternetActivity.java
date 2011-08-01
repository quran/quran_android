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

import com.quran.labs.androidquran.QuranViewActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.QuranDataService;

public abstract class InternetActivity extends BaseQuranActivity {
	
	protected ProgressDialog pDialog = null;
	protected QuranDataService downloadService;
	protected AsyncTask<?, ?, ?> currentTask = null;
	protected boolean starting = true;
	protected ServiceConnection serviceConnection;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initServiceConnection();
		if (!(this instanceof QuranViewActivity))
			if (QuranDataService.isRunning)
				showProgressDialog();
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
    	initServiceConnection();
    	if (!QuranDataService.isRunning)
    		startService(intent);
    	
    	bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    	showProgressDialog();
    }
	
	protected void downloadTranslation(String url, String fileName) {
		Intent intent = new Intent(this, QuranDataService.class);
    	intent.putExtra(QuranDataService.DWONLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_TRANSLATION);
    	intent.putExtra(QuranDataService.URL_KEY, url);
    	intent.putExtra(QuranDataService.FILE_NAME_KEY, fileName);
    	startDownloadService(intent);
	}
	
	protected void downloadQuranImages() {
		Intent intent = new Intent(this, QuranDataService.class);
    	intent.putExtra(QuranDataService.DWONLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_QURAN_IMAGES);
    	startDownloadService(intent);
	}
	
	protected void downloadSura(int readerId, int sura) {
		downloadSura(readerId, sura, 1);
	}
	
	protected void downloadSura(int readerId, int sura, int ayah) {
		Intent intent = new Intent(this, QuranDataService.class);
		intent.putExtra(QuranDataService.DWONLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_SURA_AUDIO);
		intent.putExtra(QuranDataService.SOURA_KEY, sura);
		intent.putExtra(QuranDataService.AYAH_KEY, ayah);
		intent.putExtra(QuranDataService.READER_KEY, readerId);
		startDownloadService(intent);
	}
	
	protected void downloadPage(int readerId, Integer[] integers){		
		Intent intent = new Intent(this, QuranDataService.class);
		intent.putExtra(QuranDataService.DWONLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_SURA_AUDIO);
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
	
	private void initServiceConnection() {
	    serviceConnection = new ServiceConnection() {
	    	public void onServiceConnected(ComponentName name, IBinder service){
	    		downloadService = ((QuranDataService.QuranDownloadBinder)service).getService();
	    		starting = false;
	    	}
	
	    	public void onServiceDisconnected(ComponentName className) {
	    		downloadService = null;
	    	}
	    };
	}
	
    class ProgressBarUpdateTask extends AsyncTask<Void, Integer, Void> {	
		@Override
		protected Void doInBackground(Void... params) {
    		while (starting || QuranDataService.isRunning){
    			try {
    				Thread.sleep(1000);
    				if ((serviceConnection != null) && (downloadService != null)){
    					int progress = downloadService.getProgress();
    					publishProgress(progress);
    				}
    			}
    			catch (InterruptedException ie){}
    		}
    		
    		return null;
    	}
    	
		@Override
    	public void onProgressUpdate(Integer...integers){
    		int progress = integers[0];
    		pDialog.setProgress(progress);
    	}
    	
    	@Override
    	public void onPostExecute(Void val){
    		pDialog.dismiss();
			pDialog = null;
			currentTask = null;
			onFinishDownload();
    	}
    }
    
    protected void onFinishDownload() {
    	
    }
    
    protected void onDownloadCanceled() {
    	
    }
    
	private void showProgressDialog(){
    	pDialog = new ProgressDialog(this);
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
					unbindService(serviceConnection);
				}
				stopService(new Intent(getApplicationContext(), QuranDataService.class));
				currentTask = null;
				pDialog.dismiss();
			}
		});
    	pDialog.setButton(ProgressDialog.BUTTON2, "Hide", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				currentTask.cancel(true);
				pDialog.dismiss();
			}
		});
    	
    	pDialog.setMessage(getString(R.string.downloading_message));
    	pDialog.show();
    	
    	currentTask = new ProgressBarUpdateTask().execute();
    }

}
