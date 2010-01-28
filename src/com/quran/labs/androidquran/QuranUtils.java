package com.quran.labs.androidquran;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import android.graphics.drawable.Drawable;
import android.os.Environment;

public class QuranUtils {
	public static boolean wroteNoMedia = false;
	public static boolean failedToWrite = false;
	
	public static Drawable getImageFromSD(String filename){
		String state = Environment.getExternalStorageState();
		if (state.equals(Environment.MEDIA_MOUNTED)){
			File f = new File(Environment.getExternalStorageDirectory() +
				"/" + QuranInfo.QURAN_DIR +
				"/" + filename);
			boolean exists = f.exists();
			if (exists){
				try {
					if (!wroteNoMedia) QuranUtils.writeNoMediaFile();
					FileInputStream is = new FileInputStream(f);
					return Drawable.createFromStream(is, filename);
				}
				catch (Exception e){
					return null;
				}
			}
			else return null;
		}
		else return null;
	}
	
	public static void writeNoMediaFile(){
		File f = new File(Environment.getExternalStorageDirectory() +
				"/" + QuranInfo.QURAN_DIR +
				"/.nomedia");
		if (f.exists()){
			wroteNoMedia = true;
			return;
		}
		
		try {
			wroteNoMedia = f.createNewFile();
		} catch (IOException e) {}
	}
	
	public static boolean makeQuranDirectory(){
		String path = Environment.getExternalStorageDirectory() + "/" + 
			QuranInfo.QURAN_DIR;
		File directory = new File(path);
		if (directory.exists() && directory.isDirectory())
			return true;
		else return directory.mkdirs();
	}
	
	public static Drawable getImageFromWeb(String filename){
		String urlString = QuranInfo.IMG_HOST + filename;
		InputStream is;
		
		try {
			URL url = new URL(urlString);
			is = (InputStream)url.getContent();
		}
		catch (Exception e){
			return null;
		}

		if (failedToWrite)
			return Drawable.createFromStream(is, filename);
		
		String state = Environment.getExternalStorageState();
		if (state.equals(Environment.MEDIA_MOUNTED)){
			String path = Environment.getExternalStorageDirectory() +
			"/" + QuranInfo.QURAN_DIR + "/" + filename;
			if (!QuranUtils.makeQuranDirectory()){
				failedToWrite = true;
				return Drawable.createFromStream(is, filename);
			}
			
			boolean readPhase = false;
			try {
				FileOutputStream output = new FileOutputStream(path);
				int curByte = -1;
				readPhase = true;
				while ((curByte = is.read()) != -1)
					output.write(curByte);
				output.close();
				is.close();
				return QuranUtils.getImageFromSD(filename);
			}
			catch (Exception e){
				if (readPhase == false)
					return Drawable.createFromStream(is, filename);
				failedToWrite = true;
				return QuranUtils.getImageFromWeb(filename);
			}
		}
		else return Drawable.createFromStream(is, filename);
	}
}
