package com.quran.labs.androidquran.common;

public class QuranReader {
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUlr() {
		return ulr;
	}

	public void setUlr(String ulr) {
		this.ulr = ulr;
	}

	private int id;
	private String name;
	private String ulr;

	public QuranReader(int id, String name, String ulr) {
		super();
		this.id = id;
		this.name = name;
		this.ulr = ulr;
	}
}
