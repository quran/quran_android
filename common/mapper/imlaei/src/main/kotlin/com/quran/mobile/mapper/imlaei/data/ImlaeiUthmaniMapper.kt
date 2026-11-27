package com.quran.mobile.mapper.imlaei.data

import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
class ImlaeiUthmaniMapper private constructor(
  private val databaseProvider: suspend () -> ImlaeiUthmaniMappingDatabase?
) {
  @Inject
  constructor(
    imlaeiUthmaniDatabaseProvider: ImlaeiUthmaniDatabaseProvider
  ) : this({ imlaeiUthmaniDatabaseProvider.provideDatabase() })

  companion object {
    internal fun forTest(
      databaseProvider: suspend () -> ImlaeiUthmaniMappingDatabase?
    ): ImlaeiUthmaniMapper = ImlaeiUthmaniMapper(databaseProvider)
  }

  suspend fun mapWords(start: SuraAyah, end: SuraAyah): List<WordAlignment> {
    return withContext(Dispatchers.IO) {
      val database = databaseProvider()
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
        } else if (start.sura < end.sura) {
          queries.findWordAlignmentsInRange(
            startSura = start.sura.toLong(),
            endSura = end.sura.toLong(),
            startAyah = start.ayah.toLong(),
            endAyah = end.ayah.toLong()
          )
            .executeAsList()
            .map { it.asWordAlignment() }
        } else {
          emptyList()
        }
      }
    }
  }
}
