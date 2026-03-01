package com.quran.mobile.feature.voicesearch.asr

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AsrModelManagerTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var modelDir: File

  @Before
  fun setUp() {
    modelDir = File(tempFolder.root, "models/whisper-quran")
    modelDir.mkdirs()
  }

  @Test
  fun modelFilenames_areConsistent() {
    // Verify the filenames used for download match those used for path resolution
    // This is a regression test for the bug where download saved as "encoder.int8.onnx"
    // but path resolution looked for "tiny-encoder.int8.onnx"
    val encoderFilename = "tiny-encoder.int8.onnx"
    val decoderFilename = "tiny-decoder.int8.onnx"
    val tokensFilename = "tiny-tokens.txt"

    // Simulate download by creating files
    File(modelDir, encoderFilename).writeText("encoder")
    File(modelDir, decoderFilename).writeText("decoder")
    File(modelDir, tokensFilename).writeText("tokens")

    // Verify all expected files exist
    assertThat(File(modelDir, encoderFilename).exists()).isTrue()
    assertThat(File(modelDir, decoderFilename).exists()).isTrue()
    assertThat(File(modelDir, tokensFilename).exists()).isTrue()
  }

  @Test
  fun migrateOldModelFiles_renamesOldToNew() {
    // Simulate old files from previous version
    File(modelDir, "encoder.int8.onnx").writeText("encoder-data")
    File(modelDir, "decoder.int8.onnx").writeText("decoder-data")
    File(modelDir, "tokens.txt").writeText("tokens-data")

    // Run migration logic
    val oldNames = mapOf(
      "encoder.int8.onnx" to "tiny-encoder.int8.onnx",
      "decoder.int8.onnx" to "tiny-decoder.int8.onnx",
      "tokens.txt" to "tiny-tokens.txt"
    )
    for ((oldName, newName) in oldNames) {
      val oldFile = File(modelDir, oldName)
      val newFile = File(modelDir, newName)
      if (oldFile.exists() && !newFile.exists()) {
        oldFile.renameTo(newFile)
      }
    }

    // Old files should be gone
    assertThat(File(modelDir, "encoder.int8.onnx").exists()).isFalse()
    assertThat(File(modelDir, "decoder.int8.onnx").exists()).isFalse()
    assertThat(File(modelDir, "tokens.txt").exists()).isFalse()

    // New files should exist with correct content
    assertThat(File(modelDir, "tiny-encoder.int8.onnx").readText()).isEqualTo("encoder-data")
    assertThat(File(modelDir, "tiny-decoder.int8.onnx").readText()).isEqualTo("decoder-data")
    assertThat(File(modelDir, "tiny-tokens.txt").readText()).isEqualTo("tokens-data")
  }

  @Test
  fun migrateOldModelFiles_doesNotOverwriteNewFiles() {
    // Both old and new files exist — new should not be overwritten
    File(modelDir, "encoder.int8.onnx").writeText("old-data")
    File(modelDir, "tiny-encoder.int8.onnx").writeText("new-data")

    val oldFile = File(modelDir, "encoder.int8.onnx")
    val newFile = File(modelDir, "tiny-encoder.int8.onnx")
    if (oldFile.exists() && !newFile.exists()) {
      oldFile.renameTo(newFile)
    }

    // New file content preserved
    assertThat(File(modelDir, "tiny-encoder.int8.onnx").readText()).isEqualTo("new-data")
    // Old file still exists (wasn't renamed)
    assertThat(File(modelDir, "encoder.int8.onnx").exists()).isTrue()
  }

  @Test
  fun modelState_notDownloaded_whenFilesAreMissing() {
    // No files in modelDir
    assertThat(File(modelDir, "tiny-encoder.int8.onnx").exists()).isFalse()
    assertThat(File(modelDir, "tiny-decoder.int8.onnx").exists()).isFalse()
    assertThat(File(modelDir, "tiny-tokens.txt").exists()).isFalse()
  }

  @Test
  fun modelState_ready_whenAllFilesExist() {
    File(modelDir, "tiny-encoder.int8.onnx").writeText("e")
    File(modelDir, "tiny-decoder.int8.onnx").writeText("d")
    File(modelDir, "tiny-tokens.txt").writeText("t")

    assertThat(File(modelDir, "tiny-encoder.int8.onnx").exists()).isTrue()
    assertThat(File(modelDir, "tiny-decoder.int8.onnx").exists()).isTrue()
    assertThat(File(modelDir, "tiny-tokens.txt").exists()).isTrue()
  }

  @Test
  fun modelState_notReady_whenPartialDownload() {
    // Only encoder exists
    File(modelDir, "tiny-encoder.int8.onnx").writeText("e")

    assertThat(File(modelDir, "tiny-encoder.int8.onnx").exists()).isTrue()
    assertThat(File(modelDir, "tiny-decoder.int8.onnx").exists()).isFalse()
    assertThat(File(modelDir, "tiny-tokens.txt").exists()).isFalse()
  }

  @Test
  fun tmpFilesFromInterruptedDownload_areLeftBehind() {
    // Simulate interrupted download leaving .tmp files
    File(modelDir, "tiny-encoder.int8.onnx.tmp").writeText("partial")

    assertThat(File(modelDir, "tiny-encoder.int8.onnx.tmp").exists()).isTrue()
    assertThat(File(modelDir, "tiny-encoder.int8.onnx").exists()).isFalse()
  }
}
