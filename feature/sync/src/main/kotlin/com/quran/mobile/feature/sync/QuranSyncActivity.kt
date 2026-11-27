package com.quran.mobile.feature.sync

import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.quran.data.core.QuranConstants
import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.di.QuranSyncComponentInterface
import com.quran.mobile.feature.sync.presenter.AccountSyncPresenter
import com.quran.mobile.feature.sync.state.AccountSyncEvent
import com.quran.mobile.feature.sync.state.AccountSyncState
import com.quran.mobile.feature.sync.state.HeldItemRowState.Edit
import com.quran.shared.auth.model.AuthState
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class QuranSyncActivity : FragmentActivity() {

  @Inject
  lateinit var syncManager: QuranSyncManager

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var collectionRenameDialogFactory: CollectionRenameDialogFactory

  @Inject
  lateinit var readingBookmarksDao: ReadingBookmarksDao

  @Inject
  lateinit var pageMapper: ReadingBookmarkPageMapper

  private var readingBookmarkToRename by mutableStateOf<Edit.ReadingBookmark?>(null)
  private var editError by mutableStateOf(false)
  private var editJob: Job? = null
  private var refreshProfileOnResume = false
  private var profileRefreshJob: Job? = null
  private var authActivityRegistration: QuranSyncManager.AuthActivityRegistration? = null
  private val accountSyncState = MutableStateFlow<AccountSyncState.SignedIn?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? QuranSyncComponentInterface
    injector?.quranSyncComponentFactory()?.generate()?.inject(this)

    if (!::syncManager.isInitialized || !syncManager.isConfigured) {
      finish()
      return
    }

    refreshProfileOnResume = savedInstanceState?.getBoolean("refreshProfileOnResume") ?: false
    savedInstanceState?.getString("renameBookmarkId")?.let { id ->
      readingBookmarkToRename = Edit.ReadingBookmark(
        id,
        ReadingBookmarkSlot.valueOf(savedInstanceState.getString("renameBookmarkSlot")!!),
        savedInstanceState.getString("renameBookmarkName").orEmpty()
      )
    }
    authActivityRegistration = syncManager.registerAuthActivity(this)
    val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    enableEdgeToEdge(
      statusBarStyle = if (isNight) {
        SystemBarStyle.dark(Color.TRANSPARENT)
      } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
      },
      navigationBarStyle = if (isNight) {
        SystemBarStyle.dark(Color.TRANSPARENT)
      } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
      }
    )

    // the account screen starts with the first sign-in and closes when that session ends
    lifecycleScope.launch {
      syncManager.authState.first { it is AuthState.Success }
      val isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
      AccountSyncPresenter(syncManager, pageMapper).launchIn(lifecycleScope, isStarted).collect { state ->
        when (state) {
          is AccountSyncState.SignedIn -> accountSyncState.value = state
          AccountSyncState.SessionEnded -> finish()
        }
      }
    }

    val verses = QuranVerses((1..QuranConstants.NUMBER_OF_SURAS).map(quranInfo::getNumberOfAyahs))
    setContent {
      QuranTheme {
        var showSyncActivity by rememberSaveable { mutableStateOf(false) }
        val screenState = rememberSaveableStateHolder()
        val state = accountSyncState.collectAsStateWithLifecycle().value
        if (state == null) {
          QuranSyncScreen(
            syncManager = syncManager,
            onBackPressed = { finish() }
          )
        } else {
          BackHandler(enabled = showSyncActivity) { showSyncActivity = false }
          AnimatedContent(
            targetState = showSyncActivity,
            transitionSpec = {
              val direction = if (targetState) SlideDirection.Start else SlideDirection.End
              (slideIntoContainer(direction) + fadeIn()) togetherWith
                (slideOutOfContainer(direction) + fadeOut())
            },
            label = "syncActivityScreen"
          ) { showingSyncActivity ->
            screenState.SaveableStateProvider(if (showingSyncActivity) "activity" else "account") {
              if (showingSyncActivity) {
                SyncActivityScreen(
                  activity = state.activity,
                  heldRows = state.heldRows,
                  verses = verses,
                  onBackPressed = { showSyncActivity = false },
                  onSyncNow = { state.eventSink(AccountSyncEvent.SyncNow) },
                  onEdit = ::editHeldItem
                )
              } else {
                AccountSyncScreen(
                  state = state,
                  verses = verses,
                  onBackPressed = { finish() },
                  onManageAccount = ::openProfile,
                  onSyncActivity = { showSyncActivity = true },
                  onEdit = ::editHeldItem
                )
              }
            }
          }
          readingBookmarkToRename?.let { bookmark ->
            ReadingBookmarkRenameDialog(
              bookmark = bookmark,
              onRename = { name ->
                check(currentReadingBookmarks().any { it.id == bookmark.id })
                readingBookmarksDao.renameReadingBookmark(bookmark.slot.type, name)
              },
              onDismiss = { readingBookmarkToRename = null }
            )
          }
          if (editError) {
            AlertDialog(
              onDismissRequest = { editError = false },
              text = { Text(stringResource(R.string.quran_sync_error_unknown)) },
              confirmButton = {
                TextButton(onClick = { editError = false }) {
                  Text(stringResource(android.R.string.ok))
                }
              }
            )
          }
        }
      }
    }
  }

  private fun editHeldItem(edit: Edit) {
    if (editJob?.isActive == true) return
    editJob = lifecycleScope.launch {
      try {
        when (edit) {
          is Edit.Note -> {}
          is Edit.Collection -> {
            val collection = syncManager.quranDataService.collectionsWithBookmarks.first()
              .first { it.collection.id == edit.id }.collection
            check(!collection.isDefault && !collection.isReadOnly)
            if (!supportFragmentManager.isStateSaved &&
              supportFragmentManager.findFragmentByTag("collectionRename") == null) {
              collectionRenameDialogFactory.create(collection.id, collection.name)
                .show(supportFragmentManager, "collectionRename")
            }
          }
          is Edit.ReadingBookmark -> {
            val bookmark = currentReadingBookmarks().first { it.id == edit.id }
            readingBookmarkToRename = Edit.ReadingBookmark(bookmark.id, bookmark.slot, bookmark.name.orEmpty())
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        editError = true
      }
    }
  }

  private suspend fun currentReadingBookmarks() = syncManager.quranDataService
    .readingBookmarks(pageMapper.mushafFor(pageMapper.currentPageType())).first()

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean("refreshProfileOnResume", refreshProfileOnResume)
    readingBookmarkToRename?.let { bookmark ->
      outState.putString("renameBookmarkId", bookmark.id)
      outState.putString("renameBookmarkSlot", bookmark.slot.name)
      outState.putString("renameBookmarkName", bookmark.name)
    }
    super.onSaveInstanceState(outState)
  }

  override fun onResume() {
    super.onResume()
    // Custom Tabs have no close callback; refresh once when this screen resumes.
    if (refreshProfileOnResume) {
      profileRefreshJob?.cancel()
      profileRefreshJob = lifecycleScope.launch {
        // Activity restoration can finish before the stored sign-in session is restored.
        syncManager.authState.first { it is AuthState.Success }
        syncManager.refreshProfile()
        refreshProfileOnResume = false
      }
    }
  }

  override fun onDestroy() {
    if (::syncManager.isInitialized) {
      syncManager.unregisterAuthActivity(authActivityRegistration)
    }
    authActivityRegistration = null
    super.onDestroy()
  }

  private fun openProfile() {
    try {
      CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(this, Uri.parse(syncManager.profileUrl))
      refreshProfileOnResume = true
    } catch (e: ActivityNotFoundException) {
      // no browser to open the profile in
    }
  }
}
