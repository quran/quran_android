package com.quran.labs.androidquran.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranUtils;

public class QuranDataService extends Service {

	private int progress;
	public static boolean isRunning = false;
	public static int phase = 1;
	public static QuranScreenInfo qsi = null;

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
		new DownloadQuranThread(this).start();
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

	private class DownloadQuranThread extends Thread {
		private QuranDataService service;

		DownloadQuranThread(QuranDataService service){
			this.service = service;
		}

		@Override
		public void run(){
			try {
				String urlStr = QuranUtils.getZipFileUrl();
				if (urlStr == null) return;
				Log.d("quran_srv", "zip url: " + urlStr);

				URL url = new URL(urlStr);
				URLConnection conn = url.openConnection();

				conn.setDoOutput(true);
				conn.connect();

				int total = conn.getContentLength();
				Log.d("quran_srv", "total len: " + total);
				QuranUtils.makeQuranDirectory();
				
				File file = new File(QuranUtils.getQuranBaseDirectory(), "images.zip");
				if (file.exists()){
					if (file.length() != total){
						Log.d("quran_srv", "deleting partial file found of len: " +
							file.length());
						file.delete();
					}
					else unzipFile();
				}

				FileOutputStream f = new FileOutputStream(file);
				InputStream is = conn.getInputStream();

				int readlen = 0;
				int totalRead = 0;
				byte[] buf = new byte[1024];
				while ((readlen = is.read(buf)) > 0){
					f.write(buf, 0, readlen);
					totalRead += readlen;
					double percent = 100.0 * ((1.0 * totalRead)/(1.0 * total));
					service.updateProgress((int)percent);
				}
			}
			catch (IOException ioe){
				return;
			}

			unzipFile();
		}

		protected void unzipFile(){
			try {
				service.updateProgress(0);
				QuranDataService.phase++;

				// success, unzip the file...
				FileInputStream is = new FileInputStream(
						QuranUtils.getQuranBaseDirectory() + "/images.zip");
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
					service.updateProgress((int)percentage);
				}

				zis.close();
				is.close();

				File file = new File(QuranUtils.getQuranBaseDirectory(), "images.zip");
				file.delete();
			}
			catch (IOException ioe){
				Log.d("quran_srv", "io exception: " + ioe.toString());
			}

			service.stopSelf();
			QuranDataService.isRunning = false;
		}
	}
}
