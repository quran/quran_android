package com.quran.labs.androidquran.common;

import com.quran.labs.androidquran.util.QuranUtils;

public class DownloadItem {
	
	public final static String TRNASLATION = "translation";
	
	private String display_name;
	private String file_url;
	private String save_to;
	private boolean local_url;
	private String download_type;
	
	public String getDisplay_name() {
		return display_name;
	}
	public void setDisplay_name(String displayName) {
		display_name = displayName;
	}
	public String getFile_url() {
		return file_url;
	}
	public void setFile_url(String fileUrl) {
		file_url = fileUrl;
	}
	public String getSave_to() {
		return save_to;
	}
	public void setSave_to(String saveTo) {
		save_to = saveTo;
	}
	public boolean isLocal_url() {
		return local_url;
	}
	public void setLocal_url(boolean localUrl) {
		local_url = localUrl;
	}
	public String getDownload_type() {
		return download_type;
	}
	public void setDownload_type(String downloadType) {
		download_type = downloadType;
	}
	
	public boolean isDownloaded() {		
		if (download_type == TRNASLATION) {
			return QuranUtils.hasTranslation(file_url);
		}
		return false;
	}
}