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
import android.widget.RemoteViews;

import com.quran.labs.androidquran.QuranActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranUtils;

public class QuranDataService extends Service {

	public static final String DWONLOAD_TYPE_KEY = "downloadType";
	public static final String SOURA_KEY = "soura";
	public static final String AYAH_KEY = "ayah";
	public static final String READER_KEY = "quranReader";
	public static final String DOWNLOAD_AYAH_IMAGES_KEY = "downloadImage";
	public static final String DISPLAY_MESSAGE_KEY = "displayMsg";
	public static final String URL_KEY = "url";
	public static final String FILE_NAME_KEY = "fileName";
	public static final int DOWNLOAD_QURAN_IMAGES = 1;
	public static final int DOWNLOAD_SURA_AUDIO = 2;
	public static final int DOWNLOAD_TRANSLATION = 3;

	private int progress;
	public static boolean isRunning = false;
	public static int phase = 1;
	public static QuranScreenInfo qsi = null;
	private DownloadThread thread;

	/*
	 * Wait time in seconds.. Time to wait before re-checking for internet
	 * connection..
	 */
	private static final int WAIT_TIME = 10;

	public class QuranDownloadBinder extends Binder {
		public QuranDataService getService() {
			return QuranDataService.this;
		}
	}

	private final IBinder binder = new QuranDownloadBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}

	// only runs on pre-2.0 sdks
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		handleStart(intent, startId);
	}

	// 2.0+ sdks
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleStart(intent, startId);
		return START_STICKY;
	}

	public void handleStart(Intent intent, int startId) {
		int downloadType = intent.getIntExtra(DWONLOAD_TYPE_KEY, -1);
		switch (downloadType) {
			case DOWNLOAD_QURAN_IMAGES:
				thread = new DownloadThread(this, new String[] { QuranUtils.getZipFileUrl() }, 
						new String[] { "images.zip" }, new String[]{QuranUtils.getQuranBaseDirectory()}, true);
				thread.start();
			break;
			case DOWNLOAD_SURA_AUDIO:
				downloadSuraAudio(intent);
			break;
			
			case DOWNLOAD_TRANSLATION:
				downloadTranslation(intent);
			break;
			
			default:
			return;
		}
		QuranDataService.isRunning = true;
	}

	private void downloadTranslation(Intent intent) {
		String url = intent.getStringExtra(URL_KEY);
		String fileName = intent.getStringExtra(FILE_NAME_KEY);
		thread = new DownloadThread(this, new String[] { url }, 
				new String[] { fileName }, new String[]{QuranUtils.getQuranDatabaseDirectory()}, false);
		thread.start();
	}

	private void downloadSuraAudio(Intent intent) {
		// Removed from 1.4
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		QuranDataService.isRunning = false;
	}

	public void updateProgress(int progress) {
		this.progress = progress;
	}

	public int getProgress() {
		return this.progress;
	}

	public boolean isInternetOn() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm != null && cm.getActiveNetworkInfo() != null)
			return cm.getActiveNetworkInfo().isConnectedOrConnecting();
		return false;
	}

	private class DownloadThread extends Thread {
		private QuranDataService service;
		private String[] fileNames;
		private String[] downloadUrls;
		private String[] saveToDirectories;
		private boolean zipped;
		private int downloadIndex;
		private RemoteViews contentView;
		private Notification notification;
		private NotificationManager notificationManager;

		DownloadThread(QuranDataService service, String[] downloadUrls,
				String[] fileNames, String[] saveToDirectories, boolean zipped) {
			this.service = service;
			this.downloadUrls = downloadUrls;
			this.fileNames = fileNames;
			this.saveToDirectories = saveToDirectories;
			this.zipped = zipped;
			downloadIndex = 0;
		}
		
		private void onDowloadStart() {
			String ns = Context.NOTIFICATION_SERVICE;
			notificationManager = (NotificationManager) getSystemService(ns);
			Context context = QuranDataService.this.getApplicationContext();
			notification = new Notification(R.drawable.icon, "Quran Android", System.currentTimeMillis());
			contentView = new RemoteViews(context.getPackageName(), R.layout.notification_progress_bar);
			contentView.setProgressBar(R.id.progressBar, 100, 0, false);        
			contentView.setTextViewText(R.id.text, "Downloading..");       
			notification.contentView = contentView;

			Intent notificationIntent = new Intent(context, QuranActivity.class);
			PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
			notification.contentIntent = contentIntent;
			notificationManager.notify(ApplicationConstants.NOTIFICATION_DOWNLOADING, notification);
		}

		private boolean resumeDownload() {
			BufferedInputStream in = null;
			FileOutputStream fos = null;
			BufferedOutputStream bout = null;

			try {
				for (; downloadIndex < fileNames.length; downloadIndex++) {
					int downloaded = 0;	;
					File f = new File(saveToDirectories[downloadIndex]);
					f.mkdirs();
					File file = new File(saveToDirectories[downloadIndex],
							fileNames[downloadIndex]);
					URL url = new URL(downloadUrls[downloadIndex]);
					URLConnection conn = url.openConnection();
					int total = conn.getContentLength();
					Log.d("quran_srv", "File to download: " + file.getName()
							+ " - total length: " + total);
					HttpURLConnection connection = (HttpURLConnection) url
							.openConnection();
					if (file.exists()) {
						downloaded = (int) file.length();
						connection.setRequestProperty("Range", "bytes="
								+ (file.length()) + "-");
						Log.d("quran_srv", "Resuming from " + downloaded);
						if (downloaded == total)
							continue;
					}
					connection.setRequestProperty("Range", "bytes="
							+ downloaded + "-");
					connection.setDoInput(true);
					in = new BufferedInputStream(connection.getInputStream());
					fos = (downloaded == 0) ? new FileOutputStream(file
							.getAbsolutePath()) : new FileOutputStream(file
							.getAbsolutePath(), true);
					bout = new BufferedOutputStream(fos, 1024);
					byte[] data = new byte[1024];
					int x = 0;

					while ((x = in.read(data, 0, 1024)) >= 0) {
						bout.write(data, 0, x);
						downloaded += x;
						double percent = 100.0 * ((1.0 * downloaded) / (1.0 * total));
						updateProgress((int) percent);
					}

					if (zipped)
						unzipFile(saveToDirectories[downloadIndex], fileNames[downloadIndex]);

					Log.d("quran_srv", "Download Completed [" + downloadUrls[downloadIndex] + "]");
				}
			} catch (IOException e) {
				Log.e("quran_srv", "Download paused: IO Exception", e);
				return false;
			}

			return true;
		}

		private void updateProgress(int percent) {
			service.updateProgress(percent);
			notification.contentView.setTextViewText(R.id.text, "Downloading.. " + percent + "%");
			notification.contentView.setProgressBar(R.id.progressBar, 100, percent, false);

			//notify the notification manager on the update.
			notificationManager.notify(ApplicationConstants.NOTIFICATION_DOWNLOADING, notification);
		}

		@Override
		public void run() {
			onDowloadStart();
			while (true) {
				if (isInternetOn() && resumeDownload()) {
					onDownloadComplete();
					break;
				}
				try {
					Log.d("quran_srv", "Disconnected.. Retring after 10 seconds");
					sleep(WAIT_TIME * 1000);
				} catch (InterruptedException e) {
				}
			}
		}

		protected void unzipFile(String saveToDirectory, String fileName) {
			try {
				updateProgress(0);
				QuranDataService.phase++;

				// success, unzip the file...
				File file = new File(saveToDirectory, fileName);
				FileInputStream is = new FileInputStream(file);
				ZipInputStream zis = new ZipInputStream(is);
				String base = QuranUtils.getQuranBaseDirectory();

				int ctr = 0;
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					if (entry.isDirectory()) {
						zis.closeEntry();
						continue;
					}

					double percentage = 100.0 * ((1.0 * ctr++) / 604.0);

					// ignore files that already exist
					File f = new File(base + entry.getName());
					if (!f.exists()) {
						FileOutputStream ostream = new FileOutputStream(base
								+ entry.getName());

						int size;
						byte[] buf = new byte[1024];
						while ((size = zis.read(buf)) > 0)
							ostream.write(buf, 0, size);
						ostream.close();
					}
					zis.closeEntry();
					updateProgress((int) percentage);
				}

				zis.close();
				is.close();

				file.delete();
			} catch (IOException ioe) {
				Log.d("quran_srv", "io exception: " + ioe.toString());
			}
		}

		private void onDownloadComplete() {
			notificationManager.cancel(ApplicationConstants.NOTIFICATION_DOWNLOADING);

			long when = System.currentTimeMillis();
			Notification notification = new Notification(R.drawable.icon, "Download Completed", when);
			notification.defaults |= Notification.DEFAULT_SOUND | Notification.FLAG_AUTO_CANCEL;

			Context context = getApplicationContext();
			CharSequence contentTitle = "Quran Android";
			CharSequence contentText = "Download Completed";
			Intent notificationIntent = new Intent(context, QuranActivity.class);
			PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
					notificationIntent, 0);

			notification.setLatestEventInfo(context, contentTitle, contentText,
					contentIntent);
			notificationManager.notify(ApplicationConstants.NOTIFICATION_DOWNLOAD_COMPLETED,
					notification);

			service.stopSelf();
			QuranDataService.isRunning = false;
		}
	}
}
