package com.quran.mobile.feature.sync.ui

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.quran.mobile.feature.sync.presenter.LoginEvent
import com.quran.mobile.feature.sync.presenter.LoginState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import timber.log.Timber

@Composable
fun AuthenticatingState(state: LoginState.Authenticating, modifier: Modifier = Modifier) {
  val authorizationLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
      val data: Intent? = result.data
      if (result.resultCode == RESULT_OK && data != null) {
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        state.eventHandler(LoginEvent.OnAuthenticationResult(response, exception))
      } else {
        Timber.d("Authorization request canceled")
        state.eventHandler(LoginEvent.CancelLogin)
      }
    }

  CircularProgressIndicator(modifier)

  val intent = state.intent
  LaunchedEffect(intent) {
    if (intent != null) {
      authorizationLauncher.launch(intent)
    }
  }
}
