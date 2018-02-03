package com.quran.data.source

object QuranDataSourceProvider {
  fun provideMadaniDataSource(): QuranDataSource = MadaniDataSource()
  fun provideNaskhDataSource(): QuranDataSource = NaskhDataSource()
  fun provideQaloonDataSource(): QuranDataSource = QaloonDataSource()
  fun provideShemerlyDataSource(): QuranDataSource = ShemerlyDataSource()
  fun provideWarshDataSource(): QuranDataSource = WarshDataSource()
}
