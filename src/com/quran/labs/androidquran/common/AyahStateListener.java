package com.quran.labs.androidquran.common;

public interface AyahStateListener {
	void onAyahComplete(AyahItem ayah, AyahItem nextAyah);
	void onAyahNotFound(AyahItem ayah);
}
