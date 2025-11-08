package com.quran.mobile.mapper.imlaei.data

import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
class ImlaeiUthmaniMapper @Inject constructor(
  private val imlaeiUthmaniDatabaseProvider: ImlaeiUthmaniDatabaseProvider
) {

  suspend fun mapWords(start: SuraAyah, end: SuraAyah): List<WordAlignment> {
    return withContext(Dispatchers.IO) {
      val database = imlaeiUthmaniDatabaseProvider.provideDatabase()
      if (database == null) {
        emptyList()
      } else {
        val queries = database.wordAlignmentQueries
        if (start.sura == end.sura) {
          queries.findWordAlignmentsInSuraForAyahRange(
            start.sura.toLong(),
            start.ayah.toLong(),
            end.ayah.toLong()
          )
          .executeAsList()
          .map { it.asWordAlignment() }
        } else {
          val beginning = queries.findWordAlignmentsInSuraUntilEnd(start.sura.toLong(), start.ayah.toLong())
          val ending = queries.findWordAlignmentsInSuraForAyahRange(end.sura.toLong(), 1L, end.ayah.toLong())
          val middle = (start.sura + 1 until end.sura).map {
            queries.findWordAlignmentsInSuraUntilEnd(it.toLong(), 1L)
          }
          val queries = listOf(beginning) + middle + ending
          queries.map { it.executeAsList() }
            .flatten()
            .map { it.asWordAlignment() }
        }
      }
    }
  }
}
