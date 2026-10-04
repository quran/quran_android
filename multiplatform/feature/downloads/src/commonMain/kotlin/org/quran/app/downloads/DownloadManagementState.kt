package org.quran.app.downloads

import org.quran.app.model.RecitationDownload
import org.quran.app.model.RecitationStorageSnapshot

data class DownloadManagementState(
    val snapshot: RecitationStorageSnapshot? = null,
    val isLoading: Boolean = false,
    val removing: RecitationDownload? = null,
    val notice: DownloadManagementNotice? = null,
) {
    val isBusy: Boolean get() = isLoading || removing != null
}
