package com.quran.labs.androidquran.fakes

import android.util.SparseArray
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake implementation of TranslationsDBAdapter for testing.
 *
 * Provides in-memory storage for translations and configurable responses.
 * Used by TranslationManagerPresenterTest and BaseTranslationPresenterTest.
 */
class FakeTranslationsDBAdapter {

  private val translations = mutableListOf<LocalTranslation>()

  fun setTranslations(translationList: List<LocalTranslation>) {
    translations.clear()
    translations.addAll(translationList)
  }

  fun addTranslation(translation: LocalTranslation) {
    translations.add(translation)
  }

  fun clearTranslations() {
    translations.clear()
  }

  fun getTranslations(): Flow<List<LocalTranslation>> {
    return flowOf(translations.toList())
  }

  suspend fun translationsHash(): SparseArray<LocalTranslation> {
    val result = SparseArray<LocalTranslation>()
    for (item in translations) {
      result.put(item.id.toInt(), item)
    }
    return result
  }

  suspend fun deleteTranslationByFileName(filename: String) {
    translations.removeAll { it.filename == filename }
  }

  suspend fun writeTranslationUpdates(updates: List<TranslationItem>): Boolean {
    // For testing purposes, just add the translations
    updates.forEach { item ->
      if (item.exists()) {
        addTranslation(item.asLocalTranslation())
      }
    }
    return true
  }
}
