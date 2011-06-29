package com.quran.labs.androidquran.common;

import java.io.File;

public class AyahItem {

	private String remoteAudioUrl;
	private int soura;
	private int ayah;
	private String remoteImageUrl;
	private String localAudioUrl;
	private String localImageUrl;
	private int quranReaderId;

	public boolean isAudioFoundLocally() {
		File file = new File(localAudioUrl);
		return file.exists();
	}

	public boolean isImageFoundLocally() {
		File file = new File(localImageUrl);
		return file.exists();
	}

	public String getRemoteAudioUrl() {
		return remoteAudioUrl;
	}

	public void setRemoteAudioUrl(String remoteAudioUrl) {
		this.remoteAudioUrl = remoteAudioUrl;
	}

	public String getLocalAudioUrl() {
		return localAudioUrl;
	}

	public void setLocalAudioUrl(String localAudioUrl) {
		this.localAudioUrl = localAudioUrl;
	}

	public String getLocalImageUrl() {
		return localImageUrl;
	}

	public void setLocalImageUrl(String localImageUrl) {
		this.localImageUrl = localImageUrl;
	}

	public int getQuranReaderId() {
		return quranReaderId;
	}

	public void setReader(int readerId) {
		this.quranReaderId = readerId;
	}

	public String getRemoteImageUrl() {
		return remoteImageUrl;
	}

	public void setRemoteImageUrl(String imageUrl) {
		this.remoteImageUrl = imageUrl;
	}

	public void setUrl(String url) {
		this.remoteAudioUrl = url;
	}

	public int getSoura() {
		return soura;
	}

	public void setSoura(int soura) {
		this.soura = soura;
	}

	public int getAyah() {
		return ayah;
	}

	public void setAyah(int ayah) {
		this.ayah = ayah;
	}

	public AyahItem(String remoteUrl, int soura, int ayah) {
		super();
		this.remoteAudioUrl = remoteUrl;
		this.soura = soura;
		this.ayah = ayah;
	}

	public AyahItem(int soura, int ayah, int quranReaderId,
			String remoteAudioUrl, String remoteImageUrl) {
		super();
		this.remoteAudioUrl = remoteAudioUrl;
		this.soura = soura;
		this.ayah = ayah;
		this.remoteImageUrl = remoteImageUrl;
		this.quranReaderId = quranReaderId;
	}

	public AyahItem(int soura, int ayah, int quranReaderId,
			String remoteAudioUrl, String remoteImageUrl, String localAudioUrl,
			String localImageUrl) {
		super();
		this.remoteAudioUrl = remoteAudioUrl;
		this.soura = soura;
		this.ayah = ayah;
		this.remoteImageUrl = remoteImageUrl;
		this.quranReaderId = quranReaderId;
	}

}
