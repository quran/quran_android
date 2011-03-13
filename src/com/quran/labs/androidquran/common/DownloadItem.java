package com.quran.labs.androidquran.common;

public class DownloadItem {
	
	public static final String DOWNLOAD_TYPE_TRANSLATION = "translation";
	public static final String DOWNLOAD_TYPE_SCRIPT = "script";
	
	protected int id;
	protected String displayName;
	protected String fileUrl;
	protected String saveTo;
	protected String downloadType;
	protected String fileName;
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getFileUrl() {
		return fileUrl;
	}

	public void setFileUrl(String fileUrl) {
		this.fileUrl = fileUrl;
	}

	public String getSaveTo() {
		return saveTo;
	}

	public void setSaveTo(String saveTo) {
		this.saveTo = saveTo;
	}

	public String getDownloadType() {
		return downloadType;
	}

	public void setDownloadType(String downloadType) {
		this.downloadType = downloadType;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public boolean isDownloaded() {
		return false;
	}
}