package com.quran.labs.androidquran.service

import com.google.common.truth.Truth.assertThat
import okio.Buffer
import okio.appendingSink
import okio.buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuranDownloadServiceBufferTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `copySourceToSink emits complete segments incrementally and bounds sink buffer`() {
    val totalBytes = 1024 * 1024
    val payload = ByteArray(totalBytes) { index -> (index % 251).toByte() }
    val source = Buffer().write(payload)
    val partialFile = tempFolder.newFile("download.zip.part")
    partialFile.delete()

    var maxBufferSize = 0L
    var diskBytesBeforeClose = 0L
    var checkedMidpoint = false

    partialFile.appendingSink().buffer().use { sink ->
      QuranDownloadService.copySourceToSink(
        source = source,
        sink = sink,
        initialDownloadedAmount = 0L,
        isCanceled = { false },
        onProgress = { totalRead ->
          maxBufferSize = maxOf(maxBufferSize, sink.buffer.size)
          if (!checkedMidpoint && totalRead >= totalBytes / 2) {
            checkedMidpoint = true
            diskBytesBeforeClose = partialFile.length()
          }
        }
      )
    }

    assertThat(maxBufferSize).isAtMost(QuranDownloadService.BUFFER_SIZE.toLong())
    assertThat(checkedMidpoint).isTrue()
    assertThat(diskBytesBeforeClose).isGreaterThan(0L)
    assertThat(partialFile.length()).isEqualTo(totalBytes.toLong())
    assertThat(partialFile.readBytes()).isEqualTo(payload)
  }

  @Test
  fun `copySourceToSink stops on cancel while keeping sink buffer bounded`() {
    val totalBytes = 256 * 1024
    val payload = ByteArray(totalBytes) { 0x2A }
    val source = Buffer().write(payload)
    val partialFile = tempFolder.newFile("canceled.zip.part")
    partialFile.delete()

    var canceled = false
    var maxBufferSize = 0L
    var lastTotalRead = 0L

    partialFile.appendingSink().buffer().use { sink ->
      QuranDownloadService.copySourceToSink(
        source = source,
        sink = sink,
        initialDownloadedAmount = 0L,
        isCanceled = { canceled },
        onProgress = { totalRead ->
          maxBufferSize = maxOf(maxBufferSize, sink.buffer.size)
          lastTotalRead = totalRead
          if (totalRead >= 64 * 1024) {
            canceled = true
          }
        }
      )
    }

    assertThat(maxBufferSize).isAtMost(QuranDownloadService.BUFFER_SIZE.toLong())
    assertThat(lastTotalRead).isLessThan(totalBytes.toLong())
    assertThat(partialFile.length()).isEqualTo(lastTotalRead)
  }
}
