package com.quran.labs.androidquran.common.audio.cache.command

import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.androidquran.common.audio.util.QariUtil
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import javax.inject.Inject

class AudioInfoCommand @Inject constructor(
  private val fileSystem: FileSystem,
  private val qariUtil: QariUtil,
  private val audioExtensionDecider: AudioExtensionDecider,
  private val gappedAudioInfoCommand: GappedAudioInfoCommand,
  private val gaplessAudioInfoCommand: GaplessAudioInfoCommand
) {

  fun generateAllQariDownloadInfo(audioDirectory: String): List<QariDownloadInfo> {
    val path = audioDirectory.toPath()
    val directories = fileSystem.listOrNull(path) ?: emptyList()

    val folders = directories.filter { it.toFile().isDirectory }
    val qaris = qariUtil.getQariList()
    return qaris.map { qari ->
      val matchingPath = folders.firstOrNull { it.name == qari.path }
      generateQariDownloadInfoWithPath(qari, matchingPath)
    }
  }

  fun generateQariDownloadInfo(qari: Qari, audioDirectory: String): QariDownloadInfo {
    val path = audioDirectory.toPath()
    val directories = fileSystem.listOrNull(path) ?: emptyList()

    val folders = directories.filter { it.toFile().isDirectory }
    val matchingPath = folders.firstOrNull { it.name == qari.path }
    return generateQariDownloadInfoWithPath(qari, matchingPath)
  }

  private fun generateQariDownloadInfoWithPath(qari: Qari, path: Path?): QariDownloadInfo {
    val allowedExtensions = audioExtensionDecider.allowedAudioExtensions(qari)
    return if (qari.isGapless) {
      val (fullDownloads, partialDownloads) =
        if (path == null) {
          emptyList<Int>() to emptyList()
        } else {
          gaplessAudioInfoCommand.gaplessDownloads(path, allowedExtensions)
        }
      QariDownloadInfo.GaplessQariDownloadInfo(qari, fullDownloads, partialDownloads)
    } else {
      val (fullDownloads, partiallyDownloadedSuras) =
        if (path == null) {
          emptyList<Int>() to emptyList()
        } else {
          gappedAudioInfoCommand.gappedDownloads(path, allowedExtensions)
        }
      QariDownloadInfo.GappedQariDownloadInfo(qari, fullDownloads, partiallyDownloadedSuras)
    }
  }
}
