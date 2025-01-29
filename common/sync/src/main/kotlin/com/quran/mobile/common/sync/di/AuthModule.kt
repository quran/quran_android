package com.quran.mobile.common.sync.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named

@Module
@ContributesTo(AppScope::class)
object AuthModule {
  const val AUTH_DATASTORE = "auth_datastore"
  private const val PREFERENCES_STORE = "auth_prefs"
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_STORE)

  @Named(AUTH_DATASTORE)
  @Provides
  fun provideAuthDataStore(@ApplicationContext appContext: Context): DataStore<Preferences> =
    appContext.dataStore
}
