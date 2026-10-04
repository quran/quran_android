package org.quran.app.model

data class RecitationStorageSnapshot(
    val entries: List<RecitationDownload>,
    val totalBytes: Long,
    val limitBytes: Long,
)
