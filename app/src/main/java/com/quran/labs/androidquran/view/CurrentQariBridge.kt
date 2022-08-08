package com.quran.labs.androidquran.view

import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.observeOn
import kotlinx.coroutines.withContext

class CurrentQariBridge @Inject constructor(private val currentQariManager: CurrentQariManager) {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob())

  fun listenToQaris(lambda: ((QariItem) -> Unit)) {
    scope.launch {
      withContext(Dispatchers.Main) {
        currentQariManager
          .flow()
          .collect { lambda(it) }
      }
    }
  }

  fun unsubscribeAll() {
    scope.cancel()
  }
}
