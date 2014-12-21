package com.quran.labs.androidquran.common;

import android.graphics.RectF;

public class AyahBounds {
	private int mLine;
	private int mPosition;
	private RectF mBounds;
	
	public AyahBounds(Integer line, Integer position,
			int minX, int minY, int maxX, int maxY){
		mLine = line;
		mPosition = position;
		mBounds = new RectF(minX, minY, maxX, maxY);
	}
	
	public void engulf(AyahBounds other){
		mBounds.union(other.getBounds());
	}

	public RectF getBounds() {
		return new RectF(mBounds);
	}

	public void setLine(int line) {
		mLine = line;
	}

	public int getLine() {
		return mLine;
	}

	public void setPosition(int position) {
		mPosition = position;
	}

	public int getPosition() {
		return mPosition;
	}
}
