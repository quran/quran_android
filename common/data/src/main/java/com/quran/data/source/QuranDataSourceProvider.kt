package com.quran.data.source

import com.quran.data.source.impl.MadaniDataSource
import com.quran.data.source.impl.NaskhDataSource
import com.quran.data.source.impl.QaloonDataSource
import com.quran.data.source.impl.ShemerlyDataSource
import com.quran.data.source.impl.WarshDataSource

object QuranDataSourceProvider {
  fun provideMadaniDataSource(): QuranDataSource = MadaniDataSource()
  fun provideNaskhDataSource(): QuranDataSource = NaskhDataSource()
  fun provideQaloonDataSource(): QuranDataSource = QaloonDataSource()
  fun provideShemerlyDataSource(): QuranDataSource = ShemerlyDataSource()
  fun provideWarshDataSource(): QuranDataSource = WarshDataSource()
}
