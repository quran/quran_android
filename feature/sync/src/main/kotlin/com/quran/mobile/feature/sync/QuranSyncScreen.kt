package com.quran.mobile.feature.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.shared.auth.model.AuthState
import com.quran.shared.auth.model.UserInfo
import kotlinx.coroutines.launch

@Composable
internal fun QuranSyncScreen(
  syncManager: QuranSyncManager,
  onBackPressed: () -> Unit
) {
  val authState by syncManager.authState.collectAsState()
  val scope = rememberCoroutineScope()
  var isActionRunning by remember { mutableStateOf(false) }
  var actionError by remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.quran_sync_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackPressed) {
            Icon(
              imageVector = QuranIcons.ArrowBack,
              contentDescription = "",
              modifier = Modifier.autoMirror()
            )
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .background(MaterialTheme.colorScheme.surface)
        .fillMaxSize()
        .padding(paddingValues)
        .windowInsetsPadding(
          WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal)
        )
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      when (val state = authState) {
        AuthState.Idle -> SignedOutContent(
          isLoading = isActionRunning,
          actionError = actionError,
          onLogin = {
            scope.launch {
              isActionRunning = true
              actionError = null
              runCatching { syncManager.login() }
                .onFailure { actionError = it.message ?: "" }
              isActionRunning = false
            }
          }
        )
        AuthState.Loading -> LoadingContent()
        is AuthState.Success -> SignedInContent(
          userInfo = state.userInfo,
          isLoading = isActionRunning,
          actionError = actionError,
          onLogout = {
            scope.launch {
              isActionRunning = true
              actionError = null
              runCatching { syncManager.logout() }
                .onFailure { actionError = it.message ?: "" }
              isActionRunning = false
            }
          }
        )
        is AuthState.Error -> SignedOutContent(
          isLoading = isActionRunning,
          actionError = state.message.ifBlank { actionError },
          onLogin = {
            scope.launch {
              isActionRunning = true
              actionError = null
              runCatching { syncManager.login() }
                .onFailure { actionError = it.message ?: "" }
              isActionRunning = false
            }
          }
        )
      }
    }
  }
}

@Composable
private fun SignedOutContent(
  isLoading: Boolean,
  actionError: String?,
  onLogin: () -> Unit
) {
  Text(
    text = stringResource(R.string.quran_sync_not_signed_in),
    style = MaterialTheme.typography.headlineSmall,
    color = MaterialTheme.colorScheme.onSurface
  )
  Button(
    onClick = onLogin,
    enabled = !isLoading,
    modifier = Modifier.fillMaxWidth()
  ) {
    ButtonContent(
      isLoading = isLoading,
      text = stringResource(R.string.quran_sync_login)
    )
  }
  ErrorText(actionError)
}

@Composable
private fun SignedInContent(
  userInfo: UserInfo?,
  isLoading: Boolean,
  actionError: String?,
  onLogout: () -> Unit
) {
  Text(
    text = stringResource(R.string.quran_sync_signed_in),
    style = MaterialTheme.typography.headlineSmall,
    color = MaterialTheme.colorScheme.onSurface
  )
  AccountRow(
    label = stringResource(R.string.quran_sync_name),
    value = userInfo?.displayName
  )
  AccountRow(
    label = stringResource(R.string.quran_sync_email),
    value = userInfo?.email
  )
  AccountRow(
    label = stringResource(R.string.quran_sync_account_id),
    value = userInfo?.id
  )
  Spacer(modifier = Modifier.height(4.dp))
  OutlinedButton(
    onClick = onLogout,
    enabled = !isLoading,
    modifier = Modifier.fillMaxWidth()
  ) {
    ButtonContent(
      isLoading = isLoading,
      text = stringResource(R.string.quran_sync_logout)
    )
  }
  ErrorText(actionError)
}

@Composable
private fun LoadingContent() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    Text(
      text = stringResource(R.string.quran_sync_loading),
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@Composable
private fun AccountRow(label: String, value: String?) {
  if (value.isNullOrBlank()) {
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.Medium
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
  HorizontalDivider()
}

@Composable
private fun ButtonContent(isLoading: Boolean, text: String) {
  if (isLoading) {
    CircularProgressIndicator(
      modifier = Modifier.size(18.dp),
      strokeWidth = 2.dp
    )
  } else {
    Text(text = text)
  }
}

@Composable
private fun ErrorText(message: String?) {
  if (message != null) {
    Text(
      text = message.ifBlank { stringResource(R.string.quran_sync_action_error) },
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}
