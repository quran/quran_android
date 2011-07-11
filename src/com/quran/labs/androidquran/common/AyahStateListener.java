package com.quran.labs.androidquran.common;

public interface AyahStateListener {
	void onComplete(AyahItem ayah, AyahItem nextAyah);
	void onNotFound(AyahItem ayah);
}
