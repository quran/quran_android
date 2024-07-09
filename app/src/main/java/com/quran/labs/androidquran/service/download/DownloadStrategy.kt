package com.quran.labs.androidquran.service.download

interface DownloadStrategy {
  fun fileCount(): Int
  fun downloadFiles(): Boolean
}
