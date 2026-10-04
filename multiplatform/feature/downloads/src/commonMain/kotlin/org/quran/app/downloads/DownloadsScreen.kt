package org.quran.app.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.QuranText
import org.quran.app.designsystem.QuranTextButton
import org.quran.app.designsystem.QuranTextVariant
import org.quran.app.designsystem.ScreenTitle
import org.quran.app.designsystem.appString
import org.quran.app.model.AppLanguage
import org.quran.app.model.RecitationDownload

@Composable
fun DownloadsScreen(
    state: DownloadManagementState,
    language: AppLanguage,
    onRefresh: () -> Unit,
    onRemove: (RecitationDownload) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.notice?.let { notice ->
            val message = when (notice) {
                DownloadManagementNotice.REMOVED -> QuranStrings.downloadRemoved
                DownloadManagementNotice.NOT_FOUND -> QuranStrings.downloadAlreadyGone
                DownloadManagementNotice.IN_USE -> QuranStrings.downloadInUse
                DownloadManagementNotice.REMOVE_FAILED -> QuranStrings.downloadRemovalFailed
                DownloadManagementNotice.FAILED -> QuranStrings.downloadManagementFailed
            }
            QuranText(appString(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, variant = QuranTextVariant.Supporting)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("downloads_list"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                ScreenTitle(appString(QuranStrings.downloadsTitle), appString(QuranStrings.downloadsSubtitle))
            }
            item {
                state.snapshot?.let { snapshot ->
                    PaperCard {
                        QuranText(appString(QuranStrings.storageUsage), variant = QuranTextVariant.Title)
                        QuranText(
                            appString(
                                QuranStrings.storageUsageValue,
                                snapshot.totalBytes.toKibibytesRoundedUp(),
                                snapshot.limitBytes.toKibibytesRoundedUp(),
                            ),
                        )
                    }
                }
            }
            if (state.isLoading && state.snapshot == null) {
                item { CircularProgressIndicator() }
            } else if (state.snapshot == null) {
                item { QuranText(appString(QuranStrings.downloadManagementFailed), variant = QuranTextVariant.Supporting) }
            } else if (state.snapshot.entries.isEmpty()) {
                item { QuranText(appString(QuranStrings.noDownloads), variant = QuranTextVariant.Supporting) }
            } else {
                items(
                    items = state.snapshot.entries,
                    key = { "${it.reciterId}-${it.verseId.surah}-${it.verseId.ayah}" },
                ) { download ->
                    DownloadListItem(
                        download = download,
                        language = language,
                        removing = state.removing == download,
                        enabled = !state.isBusy,
                        onRemove = { onRemove(download) },
                    )
                }
            }
            item {
                QuranTextButton(
                    text = appString(QuranStrings.refreshDownloads),
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                    loading = state.isLoading,
                )
            }
        }
    }
}
