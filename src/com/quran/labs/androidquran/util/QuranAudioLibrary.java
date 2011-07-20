package com.quran.labs.androidquran.util;

import android.content.Context;

import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.data.QuranInfo;


public class QuranAudioLibrary {

	//private static String AUDIO_URL = "http://www.everyayah.com/data/Abdul_Basit_Murattal_192kbps/";
	private static String IMAGE_URL = "http://www.everyayah.com/data/QuranText_jpg/";
	public final static String AUDIO_EXTENSION = ".mp3";
	public final static String IMAGE_EXTENSION = ".jpg";
	
	public static AyahItem getAyahItem(Context context, int soura, int ayah, int readerId){
		String strSoura = fitInt(soura, 3);
		String strAyah = fitInt(ayah, 3);
		AyahItem ayahItem = new AyahItem(soura, ayah, readerId,
				getAudioUrl(readerId) + strSoura + strAyah + AUDIO_EXTENSION, 
				IMAGE_URL + soura + "_" + ayah + IMAGE_EXTENSION);
		setAyahItemLocalPaths(context, ayahItem);
		return ayahItem;
	}
	
	private static void setAyahItemLocalPaths(Context context, final AyahItem ayahItem){	
		ayahItem.setLocalAudioUrl(generateAudioFileName(context, ayahItem));
		ayahItem.setLocalImageUrl(generateImageFileName(context, ayahItem));
	}
	
	public static AyahItem getNextAyahItem(Context context, int currentSouraId, int currentAyaId, int quranReaderId){
		try{
			int ayah = currentAyaId;
			int soura = currentSouraId;
			if(currentAyaId >= QuranInfo.SURA_NUM_AYAHS[currentSouraId - 1]){
				soura = (currentSouraId+1)%(QuranInfo.SURA_NUM_AYAHS.length + 1);
				ayah = soura == 9 ? 1 : 0;
				//ayah = 1;
			}else
				ayah++;
			AyahItem nextAyahItem =
				new AyahItem(soura, ayah, quranReaderId,
						getAudioUrl(quranReaderId) + fitInt(soura, 3) + fitInt(ayah, 3) + AUDIO_EXTENSION,
						IMAGE_URL + soura + "_" + ayah + IMAGE_EXTENSION);
			setAyahItemLocalPaths(context, nextAyahItem);
			return nextAyahItem;
		}catch(Exception ex){
			return null;
		}
	}
	
	public static AyahItem getNextAyahAudioItem(Context context, AyahItem currentAyah){
		return getNextAyahItem(context, currentAyah.getSoura(), currentAyah.getAyah(), currentAyah.getQuranReaderId());
	}
	
	public static String generateAudioFileName(Context context, AyahItem ayahItem){
		return QuranUtils.getAyahAudioPath(ayahItem);
	}
	
	public static String generateImageFileName(Context context, AyahItem ayahItem){
		return QuranUtils.getAyahImagePath(ayahItem);
	}

	protected static String fitInt(int x, int digits) {
		int i = 0;
		int acc = 10;
		int y = 0;
		do {
			i++;
			y = x / acc;
			acc = acc * 10;
		} while (y > 0);
		String result = "";
		for (int j = 0; j < digits - i; j++) {
			result = "0" + result;
		}
		result = result + x;
		return result;
	}
	
	private static String[] AUDIO_URLS = {
		"http://www.everyayah.com/data/Abdul_Basit_Murattal_192kbps/",
		"http://www.everyayah.com/data/Abdullah_Basfar_192kbps/"
	};
	
	private static String getAudioUrl(int readerId){
		if(readerId > AUDIO_URLS.length)
			return null;
		return AUDIO_URLS[readerId - 1];
	}
	
	
}
