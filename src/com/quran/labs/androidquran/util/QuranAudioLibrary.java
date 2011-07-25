package com.quran.labs.androidquran.util;

import android.content.Context;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.data.QuranInfo;


public class QuranAudioLibrary {

	//private static String AUDIO_URL = "http://www.everyayah.com/data/Abdul_Basit_Murattal_192kbps/";
	private static String IMAGE_URL = "http://www.everyayah.com/data/QuranText_jpg/";
	public final static String AUDIO_EXTENSION = ".mp3";
	public final static String IMAGE_EXTENSION = ".jpg";
	
	public static AyahItem getAyahItem(Context context, int soura, int ayah, int readerId){
//		String strSoura = fitInt(soura, 3);
//		String strAyah = fitInt(ayah, 3);
		
		String remoteUrl;
		if(ayah == 0)
			remoteUrl = getAudioUrl(context, readerId) + fitInt(1, 3) + fitInt(1, 3) + AUDIO_EXTENSION;
		else
			remoteUrl = getAudioUrl(context, readerId) + fitInt(soura, 3) + fitInt(ayah, 3) + AUDIO_EXTENSION;
		
		String remoteImagePath = IMAGE_URL + soura + "_" + ayah + IMAGE_EXTENSION;
		
		AyahItem ayahItem = new AyahItem(soura, ayah, readerId,
				remoteUrl, 
				remoteImagePath);
		setAyahItemLocalPaths(context, ayahItem);
		return ayahItem;
	}
	
	private static void setAyahItemLocalPaths(Context context, final AyahItem ayahItem){	
		ayahItem.setLocalAudioUrl(generateAudioFileName(context, ayahItem));
		ayahItem.setLocalImageUrl(generateImageFileName(context, ayahItem));
	}
	
	public static AyahItem getNextAyahItem(Context context, int currentSouraId, int currentAyaId, 
			int quranReaderId){
		try{
			int ayah = currentAyaId;
			int soura = currentSouraId;
			if(currentAyaId >= QuranInfo.SURA_NUM_AYAHS[currentSouraId - 1]){
				soura = (currentSouraId+1)%(QuranInfo.SURA_NUM_AYAHS.length + 1);
				if(soura == 0) soura = 1;
				ayah = soura == 9 || soura == 1 ? 1 : 0;
				//ayah = 1;
			}else
				ayah++;
			String remoteUrl;
			if(ayah == 0)
				remoteUrl = getAudioUrl(context, quranReaderId) + fitInt(1, 3) + fitInt(1, 3) + AUDIO_EXTENSION;
			else
				remoteUrl = getAudioUrl(context, quranReaderId) + fitInt(soura, 3) + fitInt(ayah, 3) + AUDIO_EXTENSION;
			
			AyahItem nextAyahItem = new AyahItem(soura, ayah, quranReaderId,
										remoteUrl,
										IMAGE_URL + soura + "_" + ayah + IMAGE_EXTENSION);
			setAyahItemLocalPaths(context, nextAyahItem);
			return nextAyahItem;
		}catch(Exception ex){
			return null;
		}
	}
	
	public static AyahItem getPreviousAyahItem(Context context, int currentSouraId, 
			int currentAyaId, int quranReaderId){
		try{
			int ayah = currentAyaId;
			int soura = currentSouraId;
			if(currentAyaId == 1){
				soura--;
				if(soura == 0) soura = 114;
				ayah = QuranInfo.SURA_NUM_AYAHS[soura - 1];
			}else
				ayah--;
			String remoteUrl;
			if(ayah == 0)
				remoteUrl = getAudioUrl(context, quranReaderId) + fitInt(1, 3) + fitInt(1, 3) + AUDIO_EXTENSION;
			else
				remoteUrl = getAudioUrl(context, quranReaderId) + fitInt(soura, 3) + fitInt(ayah, 3) + AUDIO_EXTENSION;
			AyahItem nextAyahItem =
				new AyahItem(soura, ayah, quranReaderId,
						remoteUrl,
						IMAGE_URL + soura + "_" + ayah + IMAGE_EXTENSION);
			setAyahItemLocalPaths(context, nextAyahItem);
			return nextAyahItem;
		}catch(Exception ex){
			return null;
		}
	}

	
	public static AyahItem getNextAyahAudioItem(Context context, AyahItem currentAyah){
		return getNextAyahItem(context, currentAyah.getSoura(), currentAyah.getAyah(),
				currentAyah.getQuranReaderId());
	}
	
	public static AyahItem getPreviousAyahAudioItem(Context context, AyahItem currentAyah){
		return getPreviousAyahItem(context, currentAyah.getSoura(), currentAyah.getAyah(), 
				currentAyah.getQuranReaderId());
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
	
	
	private static String getAudioUrl(Context context, int readerId){
		String[] urls = 
			context.getResources().getStringArray(R.array.quran_readers_urls);
		if(readerId > urls.length)
			return null;
		return urls[readerId];
	}
	
}
