package com.quran.labs.androidquran.common;

public class AyahNotFoundException extends Exception {
		/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
		private AyahItem ayah;
		public AyahItem getAyah() {
			return ayah;
		}
		public AyahNotFoundException(AyahItem ayah) {
			this.ayah = ayah;
		}
}
