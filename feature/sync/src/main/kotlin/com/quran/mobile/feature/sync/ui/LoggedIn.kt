package com.quran.mobile.feature.sync.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.sync.R
import com.quran.mobile.feature.sync.presenter.LoginEvent
import com.quran.mobile.feature.sync.presenter.LoginState

@Composable
fun LoggedIn(state: LoginState.LoggedIn, modifier: Modifier = Modifier) {
  Column(modifier) {
    if (state.name.isNotEmpty()) {
      Text(text = state.name)
    }

    if (state.email.isNotEmpty()) {
      Text(text = state.email)
    }

    TextButton(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(16.dp),
      onClick = { state.eventHandler(LoginEvent.Logout) }
    ) {
      Text(stringResource(R.string.logout))
    }
  }
}
