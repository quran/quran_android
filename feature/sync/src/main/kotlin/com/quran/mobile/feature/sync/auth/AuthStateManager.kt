package com.quran.mobile.feature.sync.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.quran.mobile.feature.sync.di.AuthModule
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AuthStateManager @Inject constructor(
  @Named(AuthModule.AUTH_DATASTORE) private val dataStore: DataStore<Preferences>
) {

  val authState = dataStore.data
    .map { preferences -> preferences[AuthConstants.authPreference] }
    .distinctUntilChanged()

  suspend fun setAuthState(authState: String) {
    dataStore.edit { preferences -> preferences[AuthConstants.authPreference] = authState }
  }
}
