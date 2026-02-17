package com.quran.labs.androidquran.presenter.translationlist

import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface TranslationListPresenter {
  fun translations(): Flow<List<LocalTranslation>>
  fun registerForTranslations(callback: TranslationListCallback): Job
}
