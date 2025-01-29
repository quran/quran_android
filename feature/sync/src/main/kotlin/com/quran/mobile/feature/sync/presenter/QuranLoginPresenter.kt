package com.quran.mobile.feature.sync.presenter

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.net.toUri
import com.quran.mobile.common.sync.auth.AuthStateManager
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.sync.BuildConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

class QuranLoginPresenter @Inject constructor(
  private val authStateManager: AuthStateManager,
  @ApplicationContext applicationContext: Context
) {
  private val authorizationService by lazy { AuthorizationService(applicationContext) }

  @Composable
  fun present(): LoginState {
    val authState = authStateManager.authenticationStateFlow.collectAsState(null)
    val shouldLogin = remember { mutableStateOf(false) }
    val isLoggingIn = remember { mutableStateOf(false) }
    val isLoggingOut = remember { mutableStateOf(false) }
    val clearToken = remember { mutableStateOf(false) }
    val intent = remember { mutableStateOf<Intent?>(null) }

    val scope = rememberCoroutineScope()
    val eventHandler = { event: LoginEvent ->
      when (event) {
        LoginEvent.Login -> {
          shouldLogin.value = true
        }

        LoginEvent.CancelLogin -> {
          isLoggingIn.value = false
          intent.value = null
        }

        LoginEvent.Logout -> {
          isLoggingOut.value = true
        }

        LoginEvent.CancelLogout -> {
          isLoggingOut.value = false
          clearToken.value = true
        }

        is LoginEvent.OnAuthenticationResult -> {
          intent.value = null
          val currentAuthState = authState.value
          if (currentAuthState != null && event.response != null) {
            isLoggingIn.value = true
            scope.launch {
              val state = runCatching { onUpdatedAuthState(currentAuthState, event.response) }
              if (state.isSuccess) {
                val currentAuthState = state.getOrThrow()
                saveAuthState(currentAuthState)
                isLoggingIn.value = false
              } else {
                Timber.e(state.exceptionOrNull(), "Failed to update auth state")
                isLoggingIn.value = false
              }
            }
          }
        }

        is LoginEvent.OnLogoutResult -> {
          clearToken.value = true
          isLoggingOut.value = false
        }
      }
    }

    LaunchedEffect(shouldLogin.value) {
      if (shouldLogin.value) {
        val configuration = applicationConfiguration(authState.value)
        if (configuration != null) {
          saveAuthState(AuthState(configuration))
          intent.value = authorizationIntent(configuration)
        }
        shouldLogin.value = false
      }
    }

    LaunchedEffect(clearToken.value) {
      if (clearToken.value) {
        scope.launch {
          val authState = authState.value
          if (authState != null) {
            signOut(authState)
          }
          clearToken.value = false
        }
      }
    }

    val currentIntent = intent.value

    val currentAuthState = authState.value
    val state = if (currentIntent != null || shouldLogin.value) {
      LoginState.Authenticating(currentIntent, eventHandler)
    } else if (isLoggingIn.value) {
      LoginState.LoggingIn
    } else if (isLoggingOut.value && currentAuthState != null) {
      val logoutIntent = logoutIntent(currentAuthState)
      if (logoutIntent != null) {
        LoginState.LoggingOut(logoutIntent, eventHandler)
      } else {
        clearToken.value = true
        isLoggingOut.value = false
        LoginState.LoggedOut(isAuthenticating = false, eventHandler)
      }
    } else if (currentAuthState?.isAuthorized == true && !clearToken.value) {
      val parsedIdToken = currentAuthState.parsedIdToken?.additionalClaims.orEmpty()
      val firstName = parsedIdToken["first_name"]?.toString() ?: ""
      val lastName = parsedIdToken["last_name"]?.toString() ?: ""
      val name = listOf(firstName, lastName)
      val email = parsedIdToken["email"]?.toString() ?: ""
      LoginState.LoggedIn(name.joinToString(" ").trim(), email, eventHandler)
    } else {
      LoginState.LoggedOut(isAuthenticating = false, eventHandler)
    }

    return state
  }

  private suspend fun applicationConfiguration(authState: AuthState?): AuthorizationServiceConfiguration? {
    val configuration = authState?.authorizationServiceConfiguration
    return configuration
      ?: suspendCancellableCoroutine { continuation ->
        val callback = object : AuthorizationServiceConfiguration.RetrieveConfigurationCallback {
          override fun onFetchConfigurationCompleted(
            serviceConfiguration: AuthorizationServiceConfiguration?,
            ex: AuthorizationException?
          ) {
            if (serviceConfiguration != null && ex == null) {
              continuation.resumeWith(Result.success(serviceConfiguration))
            } else {
              continuation.resumeWithException(
                ex ?: IllegalStateException("Failed to fetch configuration")
              )
            }
          }
        }

        AuthorizationServiceConfiguration.fetchFromIssuer(
          DISCOVERY_URI.toUri(),
          callback
        )

        continuation.invokeOnCancellation {
        }
      }
  }

  private fun authorizationIntent(serviceConfiguration: AuthorizationServiceConfiguration): Intent {
    val authorizationRequest =
      AuthorizationRequest.Builder(
        serviceConfiguration,
        CLIENT_ID,
        ResponseTypeValues.CODE,
        REDIRECT_URI.toUri()
      )
        .setScope(SCOPES)
        .build()
    return authorizationService.getAuthorizationRequestIntent(authorizationRequest)
  }

  private fun logoutIntent(authState: AuthState): Intent? {
    val configuration = authState.authorizationServiceConfiguration
    return if (configuration?.endSessionEndpoint != null) {
      authorizationService.getEndSessionRequestIntent(
        EndSessionRequest.Builder(configuration)
          .setIdTokenHint(authState.idToken)
          .setPostLogoutRedirectUri(REDIRECT_URI.toUri())
          .build()
      )
    } else {
      null
    }
  }

  private suspend fun signOut(authState: AuthState) {
    val config = authState.authorizationServiceConfiguration
    val authState = if (config != null) {
      AuthState(config)
    } else {
      AuthState()
    }
    saveAuthState(authState)
  }

  private suspend fun onUpdatedAuthState(
    authState: AuthState,
    response: AuthorizationResponse
  ): AuthState {
    Timber.d("Authorization response - ${authState.isAuthorized}")
    if (response.authorizationCode != null) {
      Timber.d("Requesting authorization code...")

      return suspendCancellableCoroutine { continuation ->
        val callback = object : AuthorizationService.TokenResponseCallback {
          override fun onTokenRequestCompleted(
            response: TokenResponse?,
            ex: AuthorizationException?
          ) {
            authState.update(response, ex)

            if (ex != null) {
              continuation.resumeWith(Result.failure(ex))
            } else {
              continuation.resumeWith(Result.success(authState))
            }
          }
        }

        authorizationService.performTokenRequest(
          response.createTokenExchangeRequest(),
          callback
        )

        continuation.invokeOnCancellation {
        }
      }
    } else {
      val authState = authState
      authState.update(response, null)
      return authState
    }
  }

  private suspend fun saveAuthState(authState: AuthState) {
    authStateManager.setAuthState(authState.jsonSerializeString())
  }

  companion object {
    private const val CLIENT_ID = BuildConfig.CLIENT_ID
    private const val DISCOVERY_URI = BuildConfig.DISCOVERY_URI
    private const val SCOPES = BuildConfig.SCOPES
    private const val REDIRECT_URI = BuildConfig.REDIRECT_URI
  }
}
