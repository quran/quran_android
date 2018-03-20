package com.quran.data.page.provider.common

import com.quran.data.page.provider.madani.MadaniDataSource
import com.quran.data.page.provider.naskh.NaskhDataSource
import com.quran.data.page.provider.qaloon.QaloonDataSource
import com.quran.data.page.provider.shemerly.ShemerlyDataSource
import com.quran.data.page.provider.warsh.WarshDataSource
import com.quran.data.source.QuranDataSource

internal object QuranDataSourceProvider {
  fun provideMadaniDataSource(): QuranDataSource = MadaniDataSource()
  fun provideNaskhDataSource(): QuranDataSource = NaskhDataSource()
  fun provideQaloonDataSource(): QuranDataSource = QaloonDataSource()
  fun provideShemerlyDataSource(): QuranDataSource = ShemerlyDataSource()
  fun provideWarshDataSource(): QuranDataSource = WarshDataSource()
}
