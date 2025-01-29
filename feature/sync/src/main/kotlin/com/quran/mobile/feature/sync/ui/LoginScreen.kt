package com.quran.mobile.feature.sync.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.sync.R
import com.quran.mobile.feature.sync.presenter.LoginEvent
import com.quran.mobile.feature.sync.presenter.LoginState

@Composable
fun LoginScreen(loginState: LoginState, modifier: Modifier = Modifier) {
  Column(
    modifier
      .padding(horizontal = 16.dp)
      .padding(bottom = 16.dp)
  ) {
    Text(
      stringResource(R.string.sync_with_quran_com_details),
      style = MaterialTheme.typography.bodyMedium
    )

    val modifier = Modifier
      .padding(16.dp)
      .align(Alignment.CenterHorizontally)

    when (loginState) {
      is LoginState.LoggedIn -> LoggedIn(loginState, modifier)
      is LoginState.LoggingIn -> CircularProgressIndicator(modifier)
      is LoginState.LoggingOut -> LoggingOut(loginState, modifier)
      is LoginState.Authenticating -> AuthenticatingState(loginState, modifier)
      is LoginState.LoggedOut -> {
        TextButton(
          modifier = modifier,
          onClick = { loginState.eventHandler(LoginEvent.Login) }
        ) {
          Text(stringResource(R.string.login))
        }
      }
    }
  }
}

@Preview
@Composable
private fun LoggedInPreview() {
  QuranTheme {
    LoginScreen(
      loginState = LoginState.LoggedIn(
        name = "Altayer ibn Lahad",
        email = "altayer@",
        eventHandler = {}
      )
    )
  }
}

@Preview
@Composable
private fun LoggedOutPreview() {
  QuranTheme {
    LoginScreen(loginState = LoginState.LoggedOut(isAuthenticating = false, eventHandler = {}))
  }
}
