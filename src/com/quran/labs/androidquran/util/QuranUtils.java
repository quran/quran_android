package com.quran.labs.androidquran.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

public class QuranUtils {

	public static boolean failedToWrite = false;
	public static String IMG_HOST = "http://labs.quran.com/androidquran/";
	private static String QURAN_BASE = "/quran/";
	
	public static boolean debugRmDir(String dir, boolean deleteDirectory){
		File directory = new File(dir);
		if (directory.isDirectory()){
			String[] children = directory.list();
			for (String s : children){
				if (!debugRmDir(dir + "/" + s, true))
					return false;
			}
		}
		
		return deleteDirectory? directory.delete() : true;
	}
	
	public static void debugLsDir(String dir){
		File directory = new File(dir);
		Log.d("quran_dbg", directory.getAbsolutePath());
		
		if (directory.isDirectory()){
			String[] children = directory.list();
			for (String s : children)
				debugLsDir(dir + "/" + s);
		}
	}
	
	public static boolean haveAllImages(){
		String state = Environment.getExternalStorageState();
		if (state.equals(Environment.MEDIA_MOUNTED)){
			File dir = new File(getQuranDirectory() + "/");
			if (dir.isDirectory()){
				int files = dir.list().length;
				if (files == 605) return true;
			}
			else QuranUtils.makeQuranDirectory();
		}
		return false;
	}
	
	public static Bitmap getImageFromSD(String filename){
		String location = getQuranDirectory();
		if (location == null) return null;
		return BitmapFactory.decodeFile(location + "/" + filename);
	}
	
	public static boolean writeNoMediaFile(){
		File f = new File(getQuranDirectory() + "/.nomedia");
		if (f.exists()){
			return true;
		}
		
		try {
			return f.createNewFile();
		}
		catch (IOException e) {
			return false;
		}
	}
	
	public static boolean makeQuranDirectory(){
		String path = getQuranDirectory();
		if (path == null) return false;
		
		File directory = new File(path);
		if (directory.exists() && directory.isDirectory()){
			return writeNoMediaFile();						
		}
		else if (directory.mkdirs()){
			return writeNoMediaFile();
		}
		else return false;
	}
	
	public static Bitmap getImageFromWeb(String filename){
		String urlString = IMG_HOST + "width" +
			QuranScreenInfo.getInstance().getWidthParam() + "/" + filename;
		Log.d("quran_utils", "want to download: " + urlString);
		
		InputStream is;
		try {
			URL url = new URL(urlString);
			is = (InputStream)url.getContent();
		}
		catch (Exception e){
			return null;
		}

		if (failedToWrite)
			return BitmapFactory.decodeStream(is);
		
		String path = getQuranDirectory();
		if (path != null){
			path += "/" + filename;
			
			if (!QuranUtils.makeQuranDirectory()){
				failedToWrite = true;
				return BitmapFactory.decodeStream(is);
			}
			
			boolean readPhase = false;
			try {
				FileOutputStream output = new FileOutputStream(path);
				int readlen;
				readPhase = true;
				
				byte[] buf = new byte[1024];
				while ((readlen = is.read(buf)) > 0)
					output.write(buf, 0, readlen);
				output.close();
				is.close();

				return QuranUtils.getImageFromSD(filename);
			}
			catch (Exception e){
				Log.d("quran_utils", e.toString());
				if (readPhase == false)
					return BitmapFactory.decodeStream(is);
				failedToWrite = true;
				return QuranUtils.getImageFromWeb(filename);
			}
		}
		else return BitmapFactory.decodeStream(is);
	}
	
	public static String getQuranBaseDirectory(){
		String state = Environment.getExternalStorageState();
		if (state.equals(Environment.MEDIA_MOUNTED))
			return Environment.getExternalStorageDirectory() + QURAN_BASE;
		else return null;
	}
	
	public static String getQuranDirectory(){
		String base = getQuranBaseDirectory();
		return (base == null)? null : base + "width" +
			QuranScreenInfo.getInstance().getWidthParam();
	}
	
	public static String getZipFileUrl(){
		String url = IMG_HOST;
		url += "images" + QuranScreenInfo.getInstance().getWidthParam() + ".zip";
		return url;
	}
}
