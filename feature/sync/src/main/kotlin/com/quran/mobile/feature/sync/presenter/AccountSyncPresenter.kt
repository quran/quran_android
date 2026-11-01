package com.quran.mobile.feature.sync.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.mobile.feature.sync.QuranSyncManager
import com.quran.mobile.feature.sync.state.AccountSettingsEvent
import com.quran.mobile.feature.sync.state.AccountSettingsState
import com.quran.mobile.feature.sync.state.AccountSyncEvent
import com.quran.mobile.feature.sync.state.AccountSyncState
import com.quran.mobile.feature.sync.state.HeldItemLookup
import com.quran.mobile.feature.sync.state.heldItemRows
import com.quran.shared.auth.model.AuthState
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import com.quran.shared.persistence.model.Mushaf
import com.quran.shared.persistence.model.Note
import com.quran.shared.persistence.model.ReadingBookmark
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

internal class AccountSyncPresenter(
  private val authState: StateFlow<AuthState>,
  private val accountPresenter: AccountSettingsPresenter,
  private val notes: Flow<List<Note>>,
  private val collections: Flow<List<CollectionWithAyahBookmarks>>,
  private val readingBookmarks: Flow<List<ReadingBookmark>>,
  private val signOut: suspend () -> Unit
) {
  @Inject
  constructor(syncManager: QuranSyncManager) : this(
    syncManager.authState,
    AccountSettingsPresenter(syncManager),
    syncManager.quranDataService.notes,
    syncManager.quranDataService.collectionsWithBookmarks,
    syncManager.quranDataService.readingBookmarks(Mushaf.MADANI_1405),
    syncManager::logout
  )

  fun launchIn(scope: CoroutineScope, observing: Flow<Boolean>): StateFlow<AccountSyncState> =
    scope.launchMolecule(RecompositionMode.Immediate) { present(observing) }

  @Composable
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun present(observing: Flow<Boolean>): AccountSyncState {
    var sessionEnded by remember { mutableStateOf(authState.value !is AuthState.Success) }
    var isSigningOut by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
      authState.collect { if (it !is AuthState.Success) sessionEnded = true }
    }
    if (sessionEnded) return AccountSyncState.SessionEnded

    val account = accountPresenter.present(observing)
    if (account !is AccountSettingsState.SignedIn) return AccountSyncState.SessionEnded

    val lookup by remember {
      observing.distinctUntilChanged().flatMapLatest { isObserving ->
        if (isObserving) {
          combine(notes, collections, readingBookmarks) { notes, collections, bookmarks ->
            HeldItemLookup(notes, collections.map { it.collection }, bookmarks)
          }
        } else {
          emptyFlow()
        }
      }
    }.collectAsState(initial = HeldItemLookup())

    return AccountSyncState.SignedIn(
      profile = account.profile,
      activity = account.activity,
      heldRows = heldItemRows(account.activity.heldItems, lookup),
      blockingItemCount = account.activity.blockingItems.size,
      recentRuns = account.activity.runs.take(PREVIEW_COUNT).toImmutableList(),
      isSigningOut = isSigningOut,
      signOutError = signOutError,
      eventSink = { event ->
        when (event) {
          AccountSyncEvent.SyncNow -> {
            if (!sessionEnded && !isSigningOut && authState.value is AuthState.Success) {
              account.eventSink(AccountSettingsEvent.SyncNow)
            }
          }
          AccountSyncEvent.ConfirmSignOut -> {
            if (!sessionEnded && !isSigningOut && authState.value is AuthState.Success) {
              isSigningOut = true
              signOutError = null
              scope.launch {
                try {
                  signOut()
                  sessionEnded = true
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Exception) {
                  signOutError = e.message.orEmpty()
                } finally {
                  isSigningOut = false
                }
              }
            }
          }
          AccountSyncEvent.DismissSignOutError -> signOutError = null
        }
      }
    )
  }

  private companion object {
    const val PREVIEW_COUNT = 3
  }
}
