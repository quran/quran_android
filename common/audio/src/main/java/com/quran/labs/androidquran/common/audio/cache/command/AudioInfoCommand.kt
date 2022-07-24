package com.quran.labs.androidquran.common.audio.cache.command

import android.content.Context
import com.quran.labs.androidquran.common.audio.model.PartiallyDownloadedSura
import com.quran.labs.androidquran.common.audio.model.QariDownloadInfo
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.QariUtil
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import javax.inject.Inject

class AudioInfoCommand @Inject constructor(
  private val appContext: Context,
  private val fileSystem: FileSystem,
  private val qariUtil: QariUtil,
  private val gappedAudioInfoCommand: GappedAudioInfoCommand,
  private val gaplessAudioInfoCommand: GaplessAudioInfoCommand
) {

  fun generateAllQariDownloadInfo(audioDirectory: String): List<QariDownloadInfo> {
    val path = audioDirectory.toPath()
    val directories = fileSystem.listOrNull(path) ?: emptyList()

    val folders = directories.filter { it.toFile().isDirectory }
    val qaris = qariUtil.getQariList(appContext)
    return qaris.map { qariItem ->
      val matchingPath = folders.firstOrNull { it.name == qariItem.path }
      generateQariDownloadInfoWithPath(qariItem, matchingPath)
    }
  }

  fun generateQariDownloadInfo(qariItem: QariItem, audioDirectory: String): QariDownloadInfo {
    val path = audioDirectory.toPath()
    val directories = fileSystem.listOrNull(path) ?: emptyList()

    val folders = directories.filter { it.toFile().isDirectory }
    val matchingPath = folders.firstOrNull { it.name == qariItem.path }
    return generateQariDownloadInfoWithPath(qariItem, matchingPath)
  }

  private fun generateQariDownloadInfoWithPath(qariItem: QariItem, path: Path?): QariDownloadInfo {
    return if (qariItem.isGapless) {
      val (fullDownloads, partialDownloads) =
        if (path == null) {
          emptyList<Int>() to emptyList()
        } else {
          gaplessAudioInfoCommand.gaplessDownloads(path)
        }
      QariDownloadInfo.GaplessQariDownloadInfo(qariItem, fullDownloads, partialDownloads)
    } else {
      val (fullDownloads, partiallyDownloadedSuras) =
        if (path == null) {
          emptyList<Int>() to emptyList()
        } else {
          gappedAudioInfoCommand.gappedDownloads(path)
        }
      QariDownloadInfo.GappedQariDownloadInfo(qariItem, fullDownloads, partiallyDownloadedSuras)
    }
  }
}
