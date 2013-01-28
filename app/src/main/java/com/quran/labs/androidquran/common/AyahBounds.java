package com.quran.labs.androidquran.common;

public class AyahBounds {
	private int minX;
	private int minY;
	private int maxX;
	private int maxY;
	private int line;
	private int position;
	
	public AyahBounds(Integer line, Integer position,
			int minX, int minY, int maxX, int maxY){
		this.setLine(line);
		this.setPosition(position);
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
	}
	
	public void engulf(AyahBounds other){
		if (this.minX > other.minX){
			this.minX = other.minX;
      }

		if (this.minY > other.minY){
			this.minY = other.minY;
      }

		if (this.maxX < other.maxX){
			this.maxX = other.maxX;
      }

		if (this.maxY < other.maxY){
			this.maxY = other.maxY;
      }
	}
	
	public int getMinX() {
		return minX;
	}

	public void setMinX(int minX) {
		this.minX = minX;
	}

	public int getMinY() {
		return minY;
	}

	public void setMinY(int minY) {
		this.minY = minY;
	}

	public int getMaxX() {
		return maxX;
	}

	public void setMaxX(int maxX) {
		this.maxX = maxX;
	}

	public int getMaxY() {
		return maxY;
	}

	public void setMaxY(int maxY) {
		this.maxY = maxY;
	}

	public void setLine(int line) {
		this.line = line;
	}

	public int getLine() {
		return line;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public int getPosition() {
		return position;
	}
}
