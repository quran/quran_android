package org.quran.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.downloads.DownloadManagementController
import org.quran.app.downloads.DownloadsScreen
import org.quran.app.model.AppLanguage

@Composable
internal fun DownloadsEntry(storage: RecitationStorageRepository, language: AppLanguage) {
    val scope = rememberCoroutineScope()
    val controller = remember(storage, scope) { DownloadManagementController(storage, scope) }
    val state by controller.state.collectAsState()
    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(controller) { controller.refresh() }
    DownloadsScreen(state, language, controller::refresh, controller::remove)
}
