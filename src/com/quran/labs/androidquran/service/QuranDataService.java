package com.quran.labs.androidquran.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.util.Log;

import com.quran.labs.androidquran.QuranActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranAudioLibrary;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranUtils;

public class QuranDataService extends Service {

	public static final String DOWNLOAD_TYPE_KEY = "downloadType";
	public static final String SOURA_KEY = "soura";
	public static final String END_SOURA_KEY = "endSoura";
	public static final String AYAH_KEY = "ayah";
	public static final String END_AYAH_KEY = "endAyah";
	public static final String READER_KEY = "quranReader";
	public static final String DOWNLOAD_AYAH_IMAGES_KEY = "downloadImage";
	public static final String DISPLAY_MESSAGE_KEY = "displayMsg";
	public static final String URL_KEY = "url";
	public static final String FILE_NAME_KEY = "fileName";
	public static final int DOWNLOAD_QURAN_IMAGES = 1;
	public static final int DOWNLOAD_SURA_AUDIO = 2;
	public static final int DOWNLOAD_TRANSLATION = 3;
	private static final String DOWNLOAD_EXT = ".part";

	private int progress;
	public static boolean isRunning = false;
	public static QuranScreenInfo qsi = null;
	private DownloadThread thread;

	/*
	 * Wait time in seconds.. Time to wait before re-checking for internet
	 * connection..
	 */
	private static final int WAIT_TIME = 15;

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

	public void handleStart(Intent intent) {
		handleStart(intent, -1);
	}

	public void handleStart(Intent intent, int startId) {
		if (intent == null)
			return;

		int downloadType = intent.getIntExtra(DOWNLOAD_TYPE_KEY, -1);
		switch (downloadType) {
		case DOWNLOAD_QURAN_IMAGES:
			isRunning = true;
			thread = new DownloadThread(this,
					new String[] { QuranUtils.getZipFileUrl() },
					new String[] { "images.zip" },
					new String[] { QuranUtils.getQuranBaseDirectory() }, true);
			thread.start();
			break;
		case DOWNLOAD_SURA_AUDIO:
			isRunning = true;
			downloadSuraAudio(intent);
			break;
		case DOWNLOAD_TRANSLATION:
			isRunning = true;
			downloadTranslation(intent);
			break;
		}
		if (thread == null)
			stopSelf();
	}

	private void downloadSuraAudio(Intent intent) {
		Log.d("quran_srv", "downloadSuraAudio");
		int soura = intent.getIntExtra(SOURA_KEY, 1);
		int endSoura = intent.getIntExtra(END_SOURA_KEY, soura);
		int startAyah = intent.getIntExtra(AYAH_KEY, 1);
		int quranReader = intent.getIntExtra(READER_KEY, 0);
		// optional end ayah
		int endAyah = intent.getIntExtra(END_AYAH_KEY,
				QuranInfo.SURA_NUM_AYAHS[soura - 1]);
		// if (endAyah > QuranInfo.SURA_NUM_AYAHS[soura - 1])
		// endAyah = QuranInfo.SURA_NUM_AYAHS[soura - 1];
		boolean downloadImage = intent.getBooleanExtra(
				DOWNLOAD_AYAH_IMAGES_KEY, false);

		Log.d("quran_srv", "finish reading params");

		ArrayList<String> fileNames = new ArrayList<String>();
		ArrayList<String> urls = new ArrayList<String>();
		ArrayList<String> directories = new ArrayList<String>();

		// Check Basmallah
		if (!QuranInfo.ayahWithinBounds(1, 1, soura, startAyah, endSoura, endAyah)
				&& !QuranUtils.isBasmallahDownloaded(quranReader)) {
			addAyahForDownload(1, 1, quranReader, false, fileNames, urls, directories);
		}

		int ayatStartIndex = startAyah;
		int ayatEndIndex = endAyah;
		for (int j = soura; j <= endSoura; j++) {
			if (j == soura) {
				ayatStartIndex = startAyah;
			} else {
				ayatStartIndex = 1;
			}
			if (j == endSoura) {
				ayatEndIndex = endAyah;
			} else {
				ayatEndIndex = QuranInfo.getNumAyahs(j);
			}
			for (int i = ayatStartIndex; i <= ayatEndIndex; i++) {
				// get ayah
				addAyahForDownload(j, i, quranReader, downloadImage,
						fileNames, urls, directories);
			}
		}

		// Check aya info db
		if (!QuranUtils.haveAyaPositionFile()){
			urls.add(QuranUtils.getAyaPositionFileUrl());
			fileNames.add("ayahinfo.db.zip");
			directories.add(QuranUtils.getQuranDatabaseDirectory());
		}

		if (urls.size() > 0) {
			thread = new DownloadThread(this, urls.toArray(new String[urls
					.size()]), fileNames.toArray(new String[urls.size()]),
					directories.toArray(new String[urls.size()]), false);
			thread.start();
		} else {
			isRunning = false;
		}
	}

	private void addAyahForDownload(int soura, int ayah, int quranReader,
			boolean downloadImage, ArrayList<String> fileNames,
			ArrayList<String> urls, ArrayList<String> directories) {
		AyahItem ayahItem = QuranAudioLibrary.getAyahItem(
				getApplicationContext(), soura, ayah, quranReader);
		String fileName = ayahItem.getLocalAudioUrl()
				.substring(
						ayahItem.getLocalAudioUrl().lastIndexOf(
								File.separator) + 1);
		String dir = ayahItem.getLocalAudioUrl().substring(0,
				ayahItem.getLocalAudioUrl().lastIndexOf(File.separator));
		File f = new File(dir, fileName);
		if (f.exists())
			return;
		fileNames.add(fileName);
		directories.add(dir);
		urls.add(ayahItem.getRemoteAudioUrl());

		if (downloadImage) {
			fileName = ayahItem.getAyah()
					+ QuranAudioLibrary.IMAGE_EXTENSION;
			dir = QuranUtils.getSuraImagePath(ayahItem.getSoura());

			f = new File(dir, fileName);
			if (f.exists())
				return;

			fileNames.add(fileName);
			directories.add(dir);
			urls.add(ayahItem.getRemoteImageUrl());
		}
	}

	private void downloadTranslation(Intent intent) {
		String url = intent.getStringExtra(URL_KEY);
		String fileName = intent.getStringExtra(FILE_NAME_KEY);
		thread = new DownloadThread(this, new String[] { url },
				new String[] { fileName },
				new String[] { QuranUtils.getQuranDatabaseDirectory() }, false);
		thread.start();
	}

	public void stop() {
		Log.d("quran_srv", "Stop Service called");
		try {
			if (thread != null) {
				thread.interrupt();
			}
		} catch (Exception e) {

		}
		progress = 0;
		isRunning = false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stop();
	}

	public void updateProgress(int progress) {
		this.progress = progress;
	}

	public int getProgress() {
		return this.progress;
	}

	public boolean isInternetOn() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		boolean val = false;
		if (cm != null && cm.getActiveNetworkInfo() != null)
			val = cm.getActiveNetworkInfo().isConnectedOrConnecting();
		return val;
	}

	private class DownloadThread extends Thread {
		private static final int DOWNLOAD_BUFFER_SIZE = 2048;
		private QuranDataService service;
		private String[] fileNames;
		private String[] downloadUrls;
		private String[] saveToDirectories;
		private boolean zipped;
		private int downloadIndex;
		private int fileLength;

		// private RemoteViews contentView;
		// private Notification notification;
		// private NotificationManager notificationManager;

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
			showNotification(ApplicationConstants.NOTIFICATION_DOWNLOADING,
					getString(R.string.notification_downloading), Notification.FLAG_AUTO_CANCEL);
		}

		private void onDownloadPaused() {
			showNotification(ApplicationConstants.NOTIFICATION_DOWNLOADING,
					getString(R.string.notification_download_paused), Notification.FLAG_AUTO_CANCEL);
		}

		private void onDownloadCanceled() {
			showNotification(ApplicationConstants.NOTIFICATION_DOWNLOADING,
					getString(R.string.notification_download_canceled), Notification.FLAG_AUTO_CANCEL);
		}

		private void showNotification(int notificationId, String msg, int flags) {
			String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager notificationManager = (NotificationManager) getSystemService(ns);

			long when = System.currentTimeMillis();
			Notification notification = new Notification(R.drawable.icon, msg,
					when);
			notification.flags |= flags;
			notification.defaults = Notification.DEFAULT_LIGHTS; 

			Context context = getApplicationContext();
			CharSequence contentTitle = "Quran Android";
			CharSequence contentText = msg;
			Intent notificationIntent = new Intent(context, QuranActivity.class);
			notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP);
			PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
					notificationIntent, 0);

			notification.setLatestEventInfo(context, contentTitle, contentText,
					contentIntent);

			notificationManager.notify(notificationId, notification);
		}

		// based on:
		// http://stackoverflow.com/questions/6237079/resume-http-file-download-in-java
		private boolean resumeDownload() {
			onDowloadStart();
			BufferedInputStream in = null;
			FileOutputStream fos = null;
			BufferedOutputStream bout = null;

			try {
				for (; downloadIndex < fileNames.length; downloadIndex++) {
					int downloaded = 0;
					boolean skipDownload = false;
					boolean shouldRename = true;
					File f = new File(saveToDirectories[downloadIndex]);
					f.mkdirs();
					
					// try to open the actual file without .part in case
					// it has already finished downloading.
					File file = new File(saveToDirectories[downloadIndex],
							fileNames[downloadIndex]);
					URL url = new URL(downloadUrls[downloadIndex]);
					URLConnection conn = url.openConnection();
					fileLength = conn.getContentLength();
					if (file.exists() && (fileLength == (int)file.length())){
						skipDownload = true;
						shouldRename = false;
					}		
					else {
						// try to see if the filename with .part exists
						file = new File(saveToDirectories[downloadIndex],
							fileNames[downloadIndex] + DOWNLOAD_EXT);
						if (file.exists()) {
							downloaded = (int) file.length();
							Log.d("quran_srv", "Resuming from " + downloaded);
							if (downloaded == fileLength)
								skipDownload = true;
						}
					}
					
					if (!skipDownload){
						if(!isSpaceAvailable()){
							Log.e("quran_srv", "Not enough space on SD card");
							return false;
						}
						Log.d("quran_srv", "File to download: " + file.getName()
								+ " - total length: " + fileLength);
						
						HttpURLConnection connection = (HttpURLConnection) url
							.openConnection();
						connection.setRequestProperty("Range", "bytes="
								+ downloaded + "-");
						connection.setDoInput(true);
						in = new BufferedInputStream(connection.getInputStream(),
								DOWNLOAD_BUFFER_SIZE);
						fos = (downloaded == 0) ? new FileOutputStream(
								file.getAbsolutePath()) : new FileOutputStream(
								file.getAbsolutePath(), true);
	
						bout = new BufferedOutputStream(fos, DOWNLOAD_BUFFER_SIZE);
						byte[] data = new byte[DOWNLOAD_BUFFER_SIZE];
						int x = 0;
	
						while (isRunning
								&& (x = in.read(data, 0, DOWNLOAD_BUFFER_SIZE)) >= 0) {
							bout.write(data, 0, x);
							downloaded += x;
							double percent = 100.0 * ((1.0 * downloaded) / (1.0 * fileLength));
							updateProgress((int) percent, fileNames.length,
									downloadIndex, false);
						}
						bout.flush();
						bout.close();
						fos.close();
					}

					if (isRunning) {
						updateProgress(100, fileNames.length, downloadIndex, false);
						if (shouldRename){
							file.renameTo(new File(
								saveToDirectories[downloadIndex],
								fileNames[downloadIndex]));
						}

						if (zipped || fileNames[downloadIndex].endsWith(".zip"))
							unzipFile(saveToDirectories[downloadIndex],
									fileNames[downloadIndex],
									fileNames.length, downloadIndex);

						updateProgress(100, fileNames.length, downloadIndex, true);
						Log.d("quran_srv", "Download Completed ["
								+ downloadUrls[downloadIndex] + "]");
					} else
						return false;
				}
			} catch (FileNotFoundException e) {
				Log.e("quran_srv", "File not found: IO Exception", e);
			} catch (IOException e) {
				Log.e("quran_srv", "Download paused: IO Exception", e);
				return false;
			} catch (Exception e) {
				Log.e("quran_srv", "Download paused: Exception", e);
				return false;
			}

			return true;
		}
		
		private boolean isSpaceAvailable(){
			StatFs fsStats = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
			double availableSpace = (double)fsStats.getAvailableBlocks() * (double)fsStats.getBlockSize();
			return availableSpace > fileLength;
		}

		private void updateProgress(int percent, int totalFiles,
				int nDownloadedFiles, boolean zipPortion) {
			//android.util.Log.d("quran_srv", "called updateProgress with " + percent + ", " + totalFiles + ", "
			//		+ nDownloadedFiles + ", " + zipPortion);
			int totalPercent = totalFiles * 100;
			int donePercent = nDownloadedFiles * 100;
			int thisFileDone = (int)(1.0 * percent / 2.0) + (zipPortion? 50 : 0);
			donePercent = donePercent + thisFileDone;
		    percent = (int)((100.0 * donePercent) / (1.0 * totalPercent));
			service.updateProgress(percent);
			// notification.contentView.setTextViewText(R.id.text,
			// "Downloading.. " + percent + "%");
			// notification.contentView.setProgressBar(R.id.progressBar, 100,
			// percent, false);
			//
			// //notify the notification manager on the update.
			// notificationManager.notify(ApplicationConstants.NOTIFICATION_DOWNLOADING,
			// notification);
		}

		@Override
		public void run() {
			isRunning = true;
			try {
				while (isRunning) {
					if (isInternetOn() && resumeDownload()) {
						if (isRunning)
							onDownloadComplete();
						break;
					}
					if (!isRunning) {
						onDownloadCanceled();
						Log.d("quran_srv", "Canceled");
						break;
					}
					try {
						onDownloadPaused();
						Log.d("quran_srv", "Disconnected.. Retring after "
								+ WAIT_TIME + " seconds");
						sleep(WAIT_TIME * 1000);
					} catch (InterruptedException e) {

					}
				}
			} catch (Exception e) {
				Log.e("quran_srv", "Error", e);
			}
			thread = null;
		}

		protected void unzipFile(String saveToDirectory, String fileName,
				int totalFiles, int downloadedFiles) {
			try {
				Log.d("quran_srv", "Unziping file: " + saveToDirectory
						+ fileName);
				// success, unzip the file...
				File file = new File(saveToDirectory, fileName);
				ZipFile zip = new ZipFile(file, ZipFile.OPEN_READ);
				int numberOfFiles = zip.size();
				Enumeration<? extends ZipEntry> entries = zip.entries();
				
				int processedFiles = 0;
				while (entries.hasMoreElements()) {
					processedFiles++;
					ZipEntry entry = entries.nextElement();
					if (entry.isDirectory()) {
						File f = new File(saveToDirectory, entry.getName());
						if (!f.exists()){
							f.mkdirs();
						}
						continue;
					}

					// ignore files that already exist
					File f = new File(saveToDirectory, entry.getName());
					if (!f.exists()) {
						InputStream is = zip.getInputStream(entry);
						FileOutputStream ostream = new FileOutputStream(f);

						int size;
						byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];
						while ((size = is.read(buf)) > 0)
							ostream.write(buf, 0, size);
						is.close();
						ostream.close();
					}
					
					updateProgress((int)((100.0 * processedFiles) / (1.0 * numberOfFiles)),
							totalFiles, downloadedFiles, true);
				}

				zip.close();

				file.delete();
				Log.d("quran_srv", "file unzipped successfully");
			} catch (IOException ioe) {
				Log.e("quran_srv", "Error unzipping file: io exception: ", ioe);
			}
		}

		private void onDownloadComplete() {
			String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager notificationManager = (NotificationManager) getSystemService(ns);
			notificationManager
					.cancel(ApplicationConstants.NOTIFICATION_DOWNLOADING);

			showNotification(
					ApplicationConstants.NOTIFICATION_DOWNLOAD_COMPLETED,
					"Download Completed", Notification.DEFAULT_SOUND
							| Notification.FLAG_AUTO_CANCEL
							| Notification.DEFAULT_LIGHTS);

			service.stopSelf();
			isRunning = false;
		}
	}
}
