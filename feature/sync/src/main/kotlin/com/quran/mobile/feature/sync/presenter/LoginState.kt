package com.quran.mobile.feature.sync.presenter

import android.content.Intent
import net.openid.appauth.AuthorizationResponse

sealed class LoginState {
  data class LoggedIn(
    val name: String,
    val email: String,
    val eventHandler: (LoginEvent) -> Unit
  ) : LoginState()

  data class LoggedOut(
    val isAuthenticating: Boolean,
    val eventHandler: (LoginEvent) -> Unit
  ) : LoginState()

  data object LoggingIn : LoginState()

  data class Authenticating(
    val intent: Intent?,
    val eventHandler: (LoginEvent) -> Unit
  ) : LoginState()

  data class LoggingOut(
    val intent: Intent,
    val eventHandler: (LoginEvent) -> Unit
  ) : LoginState()
}

sealed class LoginEvent {
  data object Login : LoginEvent()
  data object Logout : LoginEvent()
  data object CancelLogin : LoginEvent()
  data object CancelLogout : LoginEvent()
  data class OnAuthenticationResult(
    val response: AuthorizationResponse?,
    val exception: Exception?
  ) : LoginEvent()

  data class OnLogoutResult(
    val response: AuthorizationResponse?,
    val exception: Exception?
  ) : LoginEvent()
}
