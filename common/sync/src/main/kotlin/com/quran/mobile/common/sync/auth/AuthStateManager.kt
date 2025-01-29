package com.quran.mobile.common.sync.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.quran.mobile.common.sync.di.AuthModule
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.openid.appauth.AuthState
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

  val authenticationStateFlow =
    authState
      .map { authStateJson ->
        if (authStateJson != null) {
          AuthState.jsonDeserialize(authStateJson)
        } else {
          null
        }
      }

  suspend fun setAuthState(authState: String) {
    dataStore.edit { preferences -> preferences[AuthConstants.authPreference] = authState }
  }
}
