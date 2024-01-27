package com.quran.labs.androidquran.view

import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.observeOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class CurrentQariBridge @Inject constructor(private val currentQariManager: CurrentQariManager) {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob())

  fun listenToQaris(lambda: ((Qari) -> Unit)) {
    currentQariManager
      .flow()
      .onEach { lambda(it) }
      .flowOn(Dispatchers.Main)
      .launchIn(scope)
  }

  fun unsubscribeAll() {
    scope.cancel()
  }
}
