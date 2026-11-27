package com.quran.labs.androidquran.common.audio.cache.command

import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.androidquran.common.audio.util.AudioFileUtil
import com.quran.labs.androidquran.common.audio.util.QariUtil
import dev.zacsweers.metro.Inject
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

@Inject
class AudioInfoCommand(
  private val fileSystem: FileSystem,
  private val qariUtil: QariUtil,
  private val audioExtensionDecider: AudioExtensionDecider,
  private val gappedAudioInfoCommand: GappedAudioInfoCommand,
  private val gaplessAudioInfoCommand: GaplessAudioInfoCommand
) {

  fun generateAllQariDownloadInfo(audioDirectory: String): List<QariDownloadInfo> {
    val path = audioDirectory.toPath()
    val directories = fileSystem.listOrNull(path) ?: emptyList()

    val foldersByName = directories
      .filter { fileSystem.metadataOrNull(it)?.isDirectory == true }
      .associateBy { it.name }
    val qaris = qariUtil.getQariList()
    return qaris.map { qari ->
      generateQariDownloadInfoWithPath(qari, foldersByName[qari.path])
    }
  }

  fun generateQariDownloadInfo(qari: Qari, audioDirectory: String): QariDownloadInfo {
    val qariPath = audioDirectory.toPath() / qari.path
    val matchingPath = if (fileSystem.metadataOrNull(qariPath)?.isDirectory == true) {
      qariPath
    } else {
      null
    }
    return generateQariDownloadInfoWithPath(qari, matchingPath)
  }

  private fun generateQariDownloadInfoWithPath(qari: Qari, path: Path?): QariDownloadInfo {
    val allowedExtensions = audioExtensionDecider.allowedAudioExtensions(qari)
    val sameAsAllAudioExtensions =
      allowedExtensions.size == AudioFileUtil.audioExtensions.size &&
          allowedExtensions.containsAll(AudioFileUtil.audioExtensions)

    return if (qari.isGapless) {
      val (fullDownloads, partialDownloads) =
        if (path == null) {
          emptyList<Int>() to emptyList()
        } else {
          gaplessAudioInfoCommand.gaplessDownloads(path, allowedExtensions)
        }
      val surasWithLocalFiles = when {
        path == null -> emptyList()
        sameAsAllAudioExtensions -> fullDownloads
        else -> gaplessAudioInfoCommand.gaplessDownloads(path, AudioFileUtil.audioExtensions).first
      }
      QariDownloadInfo.GaplessQariDownloadInfo(qari, fullDownloads, partialDownloads, surasWithLocalFiles)
    } else {
      val (fullDownloads, partiallyDownloadedSuras) =
        if (path == null) {
          emptyList<Int>() to emptyList()
        } else {
          gappedAudioInfoCommand.gappedDownloads(path, allowedExtensions)
        }
      val surasWithLocalFiles = when {
        path == null -> emptyList()
        sameAsAllAudioExtensions -> {
          (fullDownloads + partiallyDownloadedSuras.map { it.sura }).distinct()
        }
        else -> {
          val (localFullDownloads, localPartialDownloads) =
            gappedAudioInfoCommand.gappedDownloads(path, AudioFileUtil.audioExtensions)
          (localFullDownloads + localPartialDownloads.map { it.sura }).distinct()
        }
      }
      QariDownloadInfo.GappedQariDownloadInfo(qari, fullDownloads, partiallyDownloadedSuras, surasWithLocalFiles)
    }
  }
}
