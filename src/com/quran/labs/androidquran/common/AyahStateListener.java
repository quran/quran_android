package com.quran.labs.androidquran.common;

public interface AyahStateListener {
	boolean onAyahComplete(AyahItem ayah, AyahItem nextAyah);
	void onAyahNotFound(AyahItem ayah);
	void onAyahError(AyahItem ayah);
}
