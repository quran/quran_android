package com.quran.labs.androidquran.common;

import com.quran.labs.androidquran.data.ApplicationConstants;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public abstract class QuranGalleryAdapter extends BaseAdapter {

	protected Context context;
	protected LayoutInflater mInflater;

	public QuranGalleryAdapter(Context context) {
		this.context = context;
		mInflater = LayoutInflater.from(this.context);
	}

	public int getCount() {
		return ApplicationConstants.PAGES_LAST;
	}

	public Object getItem(int position) {
		return ApplicationConstants.PAGES_LAST - 1 - position;
	}

	public long getItemId(int position) {
		return ApplicationConstants.PAGES_LAST - 1 - position;
	}

	public abstract View getView(int position, View convertView, ViewGroup parent);
	public void emptyCache() {};
}
