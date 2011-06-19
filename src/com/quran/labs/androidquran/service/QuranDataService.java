package com.quran.labs.androidquran.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.quran.labs.androidquran.QuranActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranUtils;

public class QuranDataService extends Service {

	private int progress;
	public static boolean isRunning = false;
	public static int phase = 1;
	public static QuranScreenInfo qsi = null;
	
	/*
	 * Wait time in seconds..
	 * Time to wait before re-checking for internet connection..
	 */
	private static final int WAIT_TIME = 10;

	public class QuranDownloadBinder extends Binder {
		public QuranDataService getService(){
			return QuranDataService.this;
		}
	}

	private final IBinder binder = new QuranDownloadBinder();

	@Override
	public IBinder onBind(Intent intent){
		return binder;
	}

	@Override
	public void onCreate(){
		super.onCreate();
	}

	// only runs on pre-2.0 sdks
	@Override
	public void onStart(Intent intent, int startId){
		super.onStart(intent, startId);
		handleStart(intent, startId);
	}

	// 2.0+ sdks
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		handleStart(intent, startId);
		return START_STICKY;
	}

	public void handleStart(Intent intent, int startId){
		QuranDataService.isRunning = true;
		new DownloadQuranThread(this, QuranUtils.getZipFileUrl(), "images.zip", QuranUtils.getQuranBaseDirectory()).start();
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		QuranDataService.isRunning = false;
	}

	public void updateProgress(int progress){
		this.progress = progress;
	}

	public int getProgress(){
		return this.progress;
	}
	
	public boolean isInternetOn() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm != null && cm.getActiveNetworkInfo() != null) 
			return cm.getActiveNetworkInfo().isConnectedOrConnecting();
		return false;
	}

	private class DownloadQuranThread extends Thread {
		private QuranDataService service;
		private String fileName;
		private String downloadUrl;
		private String saveToDirectory;

		DownloadQuranThread(QuranDataService service, String downloadUrl, String fileName, String saveToDirectory){
			this.service = service;
			this.downloadUrl = downloadUrl;
			this.fileName = fileName;
			this.saveToDirectory = saveToDirectory;
		}
		
		private boolean resumeDownload() {
			BufferedInputStream in = null;
			FileOutputStream fos = null;
			BufferedOutputStream bout = null;
			
			try {
				int downloaded = 0;
				QuranUtils.makeQuranDirectory();
				File file = new File(saveToDirectory, fileName);
				URL url = new URL(downloadUrl);
				URLConnection conn = url.openConnection();
				int total = conn.getContentLength();
				Log.d("quran_srv", "File to download: " + file.getName() + " - total length: " + total);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				if (file.exists()) {
					downloaded = (int) file.length();
					connection.setRequestProperty("Range", "bytes=" + (file.length()) + "-");
					Log.d("quran_srv", "Resuming from " + downloaded);
				}
				connection.setRequestProperty("Range", "bytes=" + downloaded + "-");
				connection.setDoInput(true);
				in = new BufferedInputStream(connection.getInputStream());
				fos = (downloaded == 0) ? new FileOutputStream(file.getAbsolutePath())
										: new FileOutputStream(file.getAbsolutePath(), true);
				bout = new BufferedOutputStream(fos, 1024);
				byte[] data = new byte[1024];
				int x = 0;
				
				while ((x = in.read(data, 0, 1024)) >= 0) {
					bout.write(data, 0, x);
					downloaded += x;
					double percent = 100.0 * ((1.0 * downloaded)/(1.0 * total));
					updateProgress((int) percent);
				}
				
				Log.d("quran_srv", "Download Completed");
			} catch (IOException e) {
				Log.e("quran_srv", "Download paused: IO Exception", e);
				return false;
			}
			
			return true;
		}
		
		private void updateProgress(int percent) {
			service.updateProgress((int)percent);
		}

		@Override
		public void run(){
			while (true) {
				if (isInternetOn() && resumeDownload()) {
					unzipFile();
					break;
				}
				try {
					Log.d("quran_srv", "Disconnected.. Retring after 10 seconds");
					sleep(WAIT_TIME * 1000);
				} catch (InterruptedException e) {
				}
			}
		}

		protected void unzipFile(){
			try {
				updateProgress(0);
				QuranDataService.phase++;

				// success, unzip the file...
				File file = new File (saveToDirectory, fileName);
				FileInputStream is = new FileInputStream(file);
				ZipInputStream zis = new ZipInputStream(is);
				String base = QuranUtils.getQuranBaseDirectory();

				int ctr = 0;
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null){
					if (entry.isDirectory()){
						zis.closeEntry();
						continue;
					}

					double percentage = 100.0 * ((1.0 * ctr++) / 604.0);

					// ignore files that already exist
					File f = new File(base + entry.getName());
					if (!f.exists()){
						FileOutputStream ostream = new FileOutputStream(base + entry.getName());

						int size;
						byte[] buf = new byte[1024];
						while ((size = zis.read(buf)) > 0)
							ostream.write(buf, 0, size);
						ostream.close();
					}
					zis.closeEntry();
					updateProgress((int)percentage);
				}

				zis.close();
				is.close();

				file.delete();
				onDownloadComplete();
			}
			catch (IOException ioe){
				Log.d("quran_srv", "io exception: " + ioe.toString());
			}

			service.stopSelf();
			QuranDataService.isRunning = false;
		}
		
		private void onDownloadComplete() {
			String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager nm = (NotificationManager) getSystemService(ns);
			
			long when = System.currentTimeMillis();
			Notification notification = new Notification(R.drawable.icon, "Download Completed", when);
			notification.defaults |= Notification.DEFAULT_SOUND | Notification.FLAG_AUTO_CANCEL;
			
			Context context = getApplicationContext();
			CharSequence contentTitle = "Quran Android";
			CharSequence contentText = "Download Completed";
			Intent notificationIntent = new Intent(context, QuranActivity.class);
			PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

			notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
			nm.notify(ApplicationConstants.NOTIFICATION_DOWNLOAD_COMPLETED, notification);
		}
	}
}
