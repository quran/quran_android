package com.quran.mobile.feature.sync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.auth.AuthStateManager
import com.quran.mobile.feature.sync.di.AuthComponentInterface
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import timber.log.Timber
import javax.inject.Inject

class QuranLoginActivity : AppCompatActivity() {
  @Inject
  lateinit var authStateManager: AuthStateManager

  private val scope = MainScope()
  private lateinit var authState: AuthState
  private val authorizationService by lazy { AuthorizationService(applicationContext) }

  private val authorizationLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result: ActivityResult ->
    val data: Intent? = result.data
    if (result.resultCode == RESULT_OK && data != null) {
      val response = AuthorizationResponse.fromIntent(data)
      val exception = AuthorizationException.fromIntent(data)
      if (response != null) {
        onUpdatedAuthState(response)
      } else {
        Timber.e(exception, "Authorization request failed")
      }
    } else {
      Timber.d("Authorization request canceled")
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? AuthComponentInterface
    injector?.authComponentFactory()?.generate()?.inject(this)

    scope.launch {
      authStateManager.authState
        .map { authStateJson ->
          if (authStateJson != null) {
            AuthState.jsonDeserialize(authStateJson)
          } else {
            AuthState()
          }
        }
        .onEach { authState = it }
        .collect { authState ->
          if (authState.isAuthorized) {
            finish()
          } else {
            initializeAppAuth()
          }
        }
    }
  }

  private fun initializeAppAuth() {
    AuthorizationServiceConfiguration.fetchFromIssuer(
      Uri.parse(DISCOVERY_URI),
      object : AuthorizationServiceConfiguration.RetrieveConfigurationCallback {
        override fun onFetchConfigurationCompleted(
          serviceConfiguration: AuthorizationServiceConfiguration?,
          ex: AuthorizationException?
        ) {
          if (serviceConfiguration != null && ex == null) {
            val authorizationRequest =
              AuthorizationRequest.Builder(
                serviceConfiguration,
                CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(REDIRECT_URI)
              )
                .setScope(SCOPES)
                .build()
            val authIntent =
              authorizationService.getAuthorizationRequestIntent(authorizationRequest)
            authorizationLauncher.launch(authIntent)
          }
        }
      })
  }

  private fun onUpdatedAuthState(response: AuthorizationResponse) {
    Timber.d("Authorization response - ${authState.isAuthorized}")
    if (response.authorizationCode != null) {
      Timber.d("Requesting authorization code...")
      authorizationService.performTokenRequest(
        response.createTokenExchangeRequest(),
        object : AuthorizationService.TokenResponseCallback {
          override fun onTokenRequestCompleted(
            response: TokenResponse?,
            ex: AuthorizationException?
          ) {
            val authState = authState
            authState.update(response, ex)

            if (authState.isAuthorized) {
              Timber.d("Authorization code succeeded")
              saveAuthState(authState)
            } else {
              Timber.d("Authorization code exchange failed")
            }
          }
        }
      )
    } else {
      val authState = authState
      authState.update(response, null)

      saveAuthState(authState)
    }
  }

  private fun saveAuthState(authState: AuthState) {
    scope.launch {
      authStateManager.setAuthState(authState.jsonSerializeString())
    }
  }

  companion object {
    private const val CLIENT_ID = BuildConfig.CLIENT_ID
    private const val DISCOVERY_URI = BuildConfig.DISCOVERY_URI
    private const val SCOPES = BuildConfig.SCOPES
    private const val REDIRECT_URI = BuildConfig.REDIRECT_URI
  }
}
