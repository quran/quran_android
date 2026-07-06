package com.quran.mobile.feature.sync

import android.content.Context
import androidx.activity.ComponentActivity
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.shared.auth.di.AuthFlowFactoryProvider
import com.quran.shared.auth.model.AuthConfig
import com.quran.shared.auth.model.AuthState
import com.quran.shared.persistence.DriverFactory
import com.quran.shared.pipeline.di.AppGraph
import com.quran.shared.pipeline.di.SharedDependencyGraph
import com.quran.shared.pipeline.storage.createMobileSyncStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.publicvalue.multiplatform.oidc.OpenIdConnectClient
import org.publicvalue.multiplatform.oidc.appsupport.AndroidCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.appsupport.HandleRedirectActivity
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlow
import org.publicvalue.multiplatform.oidc.flows.CodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.flows.EndSessionFlow
import org.publicvalue.multiplatform.oidc.tokenstore.AndroidSettingsTokenStore
import java.util.UUID

/**
 * Owns the app-scoped Quran sync graph and the Android OAuth activity registration.
 *
 * The graph is application-context safe and is created eagerly so local sync-backed data is
 * available in configured, unconfigured, logged-in, and logged-out states. Android activity
 * registration is only required before launching an interactive login flow.
 */
@OptIn(org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect::class)
@SingleIn(AppScope::class)
class QuranSyncManager @Inject constructor(
  @param:ApplicationContext private val context: Context
) {
  private val config = QuranSyncConfig(context)
  private val authActionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val authActionMutex = Mutex()
  private var codeAuthFlowFactory: AndroidCodeAuthFlowFactory? = null
  private var registeredAuthActivityId: Int? = null
  private var nextAuthActivityId = 0
  private var activeAuthActions = 0
  // The pending-logout marker is only recovery state for a later process. Same-process OIDC
  // redirect Activities are expected during a normal logout and must not consume the marker.
  private val logoutRecoveryProcessId = UUID.randomUUID().toString()
  private val syncPreferences = context.getSharedPreferences(
    SYNC_PREFERENCES_NAME,
    Context.MODE_PRIVATE
  )
  private val tokenStore = AndroidSettingsTokenStore(context)
  private val graph: AppGraph = createGraph()

  internal val quranDataService = graph.quranDataService

  val isConfigured: Boolean
    get() = graph.authService.isAuthenticationConfigured

  val authState: StateFlow<AuthState>
    get() = graph.authService.authState

  /**
   * Emits whether UI can offer a manual sync action.
   *
   * The value is `true` only when this build has usable sync configuration and the current auth
   * session is signed in. Callers outside the sync feature can use this without depending on
   * mobile-sync's auth model classes.
   */
  val canTriggerSyncFlow: Flow<Boolean>
    get() = authState
      .map { authState -> isConfigured && authState is AuthState.Success }
      .distinctUntilChanged()

  /**
   * Returns whether a manual sync trigger is currently valid.
   *
   * This is a snapshot of [canTriggerSyncFlow] for event handlers that need to re-check state at
   * the time of a user action.
   */
  val canTriggerSync: Boolean
    get() = isConfigured && authState.value is AuthState.Success

  /**
   * Registers the activity-backed OIDC flow factory required for Android login and pending redirect handling.
   *
   * Graph construction is independent from Activity registration; Android only needs the activity-backed
   * factory before an interactive browser login can be launched. OIDC's internal redirect bridge is ignored
   * because it cannot own future browser flow launches.
   *
   * @param activity activity that owns browser/web auth result launchers for the current foreground lifecycle.
   * @return registration token that must be passed to [unregisterAuthActivity] from the same activity instance.
   */
  fun registerAuthActivity(activity: ComponentActivity): AuthActivityRegistration? {
    if (!isConfigured) return null
    if (activity is HandleRedirectActivity) return null

    val codeAuthFlowFactory = codeAuthFlowFactory ?: AndroidCodeAuthFlowFactory(useWebView = false)
      .also { codeAuthFlowFactory = it }
    val registration = AuthActivityRegistration(++nextAuthActivityId)
    registeredAuthActivityId = registration.id
    codeAuthFlowFactory.registerActivity(activity)
    AuthFlowFactoryProvider.initialize(codeAuthFlowFactory)
    return registration
  }

  /**
   * Releases the activity-backed OIDC factory when the matching foreground sync Activity is destroyed.
   *
   * During an active browser auth action the factory is kept until the action completes so the
   * shared result flow can survive Activity recreation.
   */
  fun unregisterAuthActivity(registration: AuthActivityRegistration?) {
    if (registeredAuthActivityId != registration?.id) return

    registeredAuthActivityId = null
    clearAuthFlowFactoryIfIdle()
  }

  /**
   * Starts the OAuth login flow and triggers an initial sync after a successful token exchange.
   *
   * The browser redirect continuation is owned by this app-scoped manager rather than the calling
   * UI coroutine, so Activity recreation while the browser is open does not strand the pending login.
   */
  suspend fun login() {
    runAuthAction {
      if (!isConfigured || codeAuthFlowFactory == null) return@runAuthAction
      graph.authService.login()
      graph.quranDataService.triggerSync()
    }
  }

  /**
   * Logs out of the shared sync account using mobile-sync's managed reset path.
   *
   * A persisted recovery marker lets a later process clear retained tokens if the current process
   * dies while the browser end-session flow is in progress.
   */
  suspend fun logout() {
    runAuthAction {
      if (!markLogoutPending()) {
        error("Unable to persist sync logout recovery marker")
      }

      try {
        graph.quranDataService.logout()
      } catch (e: Exception) {
        graph.authService.clearError()
        throw e
      } finally {
        clearLogoutPending()
      }
    }
  }

  /**
   * Requests a sync through the shared data service.
   *
   * The call schedules sync work and returns immediately; progress and completion are owned by the
   * underlying sync engine.
   */
  fun triggerSync() {
    graph.quranDataService.triggerSync()
  }

  private suspend fun runAuthAction(action: suspend () -> Unit) {
    // Browser auth flows can outlive the settings Activity that started them. Run the shared
    // operation in the app-scoped manager so Activity recreation does not cancel redirect handling.
    authActionScope.async {
      authActionMutex.withLock {
        activeAuthActions++
        try {
          action()
        } finally {
          activeAuthActions--
          clearAuthFlowFactoryIfIdle()
        }
      }
    }.await()
  }

  private fun clearAuthFlowFactoryIfIdle() {
    if (registeredAuthActivityId != null || activeAuthActions > 0) return
    if (codeAuthFlowFactory == null) return

    codeAuthFlowFactory = null
    AuthFlowFactoryProvider.initialize(InactiveAuthFlowFactory)
  }

  private fun createGraph(): AppGraph {
    clearInterruptedLogoutIfNeeded()

    return if (config.isConfigured) {
      SharedDependencyGraph.init(
        driverFactory = DriverFactory(context),
        storage = createMobileSyncStorage(context),
        environment = config.appEnvironment.synchronizationEnvironment(),
        authConfig = AuthConfig(
          environment = config.appEnvironment.authEnvironment,
          clientId = config.clientId,
          clientSecret = null,
          redirectUri = config.redirectUri,
          postLogoutRedirectUri = config.redirectUri
        )
      )
    } else {
      SharedDependencyGraph.init(
        driverFactory = DriverFactory(context),
        storage = createMobileSyncStorage(context),
        appEnvironment = config.appEnvironment,
        clientId = config.clientId,
        clientSecret = null
      )
    }
  }

  private fun markLogoutPending(): Boolean {
    return syncPreferences.edit()
      .putString(KEY_LOGOUT_PENDING_PROCESS_ID, logoutRecoveryProcessId)
      .commit()
  }

  private fun clearLogoutPending() {
    syncPreferences.edit().remove(KEY_LOGOUT_PENDING_PROCESS_ID).commit()
  }

  private fun clearInterruptedLogoutIfNeeded() {
    val pendingProcessId = syncPreferences.getString(KEY_LOGOUT_PENDING_PROCESS_ID, null) ?: return
    if (pendingProcessId == logoutRecoveryProcessId) return

    // The previous process died while logout was in flight. Clear secure token material before the
    // shared graph reloads tokens and reports the user as signed in again.
    runBlocking(Dispatchers.IO) {
      tokenStore.removeAccessToken()
      tokenStore.removeRefreshToken()
      tokenStore.removeIdToken()
    }
    clearLogoutPending()
  }

  private companion object {
    const val SYNC_PREFERENCES_NAME = "quran_sync"
    const val KEY_LOGOUT_PENDING_PROCESS_ID = "logout_pending_process_id"
  }

  class AuthActivityRegistration internal constructor(
    internal val id: Int
  )
}

private object InactiveAuthFlowFactory : CodeAuthFlowFactory {
  override fun createAuthFlow(client: OpenIdConnectClient): CodeAuthFlow {
    error("Quran sync auth is not registered with a foreground Activity.")
  }

  override fun createEndSessionFlow(client: OpenIdConnectClient): EndSessionFlow {
    return InactiveEndSessionFlow
  }
}

private object InactiveEndSessionFlow : EndSessionFlow {
  override suspend fun startLogout(
    idToken: String?,
    configureEndSessionUrl: (URLBuilder.() -> Unit)?
  ) = Unit

  override suspend fun canContinueLogout(): Boolean = false

  override suspend fun continueLogout() = Unit
}
