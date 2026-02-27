package com.quran.labs.androidquran.database

import android.util.SparseArray
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.flow.Flow

interface TranslationsDBAdapter {
  fun getTranslations(): Flow<List<LocalTranslation>>
  suspend fun translationsHash(): SparseArray<LocalTranslation>
  suspend fun deleteTranslationByFileName(filename: String)
  suspend fun writeTranslationUpdates(updates: List<TranslationItem>): Boolean
}
