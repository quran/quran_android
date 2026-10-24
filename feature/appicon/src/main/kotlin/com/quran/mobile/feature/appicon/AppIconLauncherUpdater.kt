package com.quran.mobile.feature.appicon

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@SingleIn(AppScope::class)
@Inject
class AppIconLauncherUpdater(
  private val appIconManager: AppIconManager,
  private val appCoroutineScope: AppCoroutineScope
) : DefaultLifecycleObserver {

  fun install() {
    ProcessLifecycleOwner.get().lifecycle.addObserver(this)
  }

  override fun onStop(owner: LifecycleOwner) {
    appCoroutineScope.launch(Dispatchers.Main) {
      try {
        appIconManager.applyPendingChange {
          !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Unable to show the chosen app icon in the launcher")
      }
    }
  }
}
