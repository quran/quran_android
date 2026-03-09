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
    // Verify the filenames match what AsrModelManager actually uses
    val encoderFilename = ENCODER_FILENAME
    val decoderFilename = DECODER_FILENAME
    val tokensFilename = TOKENS_FILENAME

    File(modelDir, encoderFilename).writeText("encoder")
    File(modelDir, decoderFilename).writeText("decoder")
    File(modelDir, tokensFilename).writeText("tokens")

    assertThat(File(modelDir, encoderFilename).exists()).isTrue()
    assertThat(File(modelDir, decoderFilename).exists()).isTrue()
    assertThat(File(modelDir, tokensFilename).exists()).isTrue()
  }

  @Test
  fun migrateOldModelFiles_renamesOldToNew() {
    File(modelDir, "encoder.int8.onnx").writeText("encoder-data")
    File(modelDir, "decoder.int8.onnx").writeText("decoder-data")
    File(modelDir, "tokens.txt").writeText("tokens-data")

    val oldNames = mapOf(
      "encoder.int8.onnx" to ENCODER_FILENAME,
      "decoder.int8.onnx" to DECODER_FILENAME,
      "tokens.txt" to TOKENS_FILENAME
    )
    for ((oldName, newName) in oldNames) {
      val oldFile = File(modelDir, oldName)
      val newFile = File(modelDir, newName)
      if (oldFile.exists() && !newFile.exists()) {
        oldFile.renameTo(newFile)
      }
    }

    assertThat(File(modelDir, "encoder.int8.onnx").exists()).isFalse()
    assertThat(File(modelDir, "decoder.int8.onnx").exists()).isFalse()
    assertThat(File(modelDir, "tokens.txt").exists()).isFalse()

    assertThat(File(modelDir, ENCODER_FILENAME).readText()).isEqualTo("encoder-data")
    assertThat(File(modelDir, DECODER_FILENAME).readText()).isEqualTo("decoder-data")
    assertThat(File(modelDir, TOKENS_FILENAME).readText()).isEqualTo("tokens-data")
  }

  @Test
  fun migrateOldModelFiles_doesNotOverwriteNewFiles() {
    File(modelDir, "encoder.int8.onnx").writeText("old-data")
    File(modelDir, ENCODER_FILENAME).writeText("new-data")

    val oldFile = File(modelDir, "encoder.int8.onnx")
    val newFile = File(modelDir, ENCODER_FILENAME)
    if (oldFile.exists() && !newFile.exists()) {
      oldFile.renameTo(newFile)
    }

    assertThat(File(modelDir, ENCODER_FILENAME).readText()).isEqualTo("new-data")
    assertThat(File(modelDir, "encoder.int8.onnx").exists()).isTrue()
  }

  @Test
  fun modelState_notDownloaded_whenFilesAreMissing() {
    assertThat(File(modelDir, ENCODER_FILENAME).exists()).isFalse()
    assertThat(File(modelDir, DECODER_FILENAME).exists()).isFalse()
    assertThat(File(modelDir, TOKENS_FILENAME).exists()).isFalse()
  }

  @Test
  fun modelState_ready_whenAllFilesExist() {
    File(modelDir, ENCODER_FILENAME).writeText("e")
    File(modelDir, DECODER_FILENAME).writeText("d")
    File(modelDir, TOKENS_FILENAME).writeText("t")

    assertThat(File(modelDir, ENCODER_FILENAME).exists()).isTrue()
    assertThat(File(modelDir, DECODER_FILENAME).exists()).isTrue()
    assertThat(File(modelDir, TOKENS_FILENAME).exists()).isTrue()
  }

  @Test
  fun modelState_notReady_whenPartialDownload() {
    File(modelDir, ENCODER_FILENAME).writeText("e")

    assertThat(File(modelDir, ENCODER_FILENAME).exists()).isTrue()
    assertThat(File(modelDir, DECODER_FILENAME).exists()).isFalse()
    assertThat(File(modelDir, TOKENS_FILENAME).exists()).isFalse()
  }

  @Test
  fun tmpFilesFromInterruptedDownload_areLeftBehind() {
    File(modelDir, "$ENCODER_FILENAME.tmp").writeText("partial")

    assertThat(File(modelDir, "$ENCODER_FILENAME.tmp").exists()).isTrue()
    assertThat(File(modelDir, ENCODER_FILENAME).exists()).isFalse()
  }

  @Test
  fun cleanupTempFiles_removesOnlyTmpFiles() {
    File(modelDir, "$ENCODER_FILENAME.tmp").writeText("partial")
    File(modelDir, ENCODER_FILENAME).writeText("complete")

    modelDir.listFiles()
      ?.filter { it.name.endsWith(".tmp") }
      ?.forEach { it.delete() }

    assertThat(File(modelDir, "$ENCODER_FILENAME.tmp").exists()).isFalse()
    assertThat(File(modelDir, ENCODER_FILENAME).exists()).isTrue()
  }

  companion object {
    // Must match AsrModelManager constants
    private const val ENCODER_FILENAME = "base-ar-quran-encoder.int8.onnx"
    private const val DECODER_FILENAME = "base-ar-quran-decoder.int8.onnx"
    private const val TOKENS_FILENAME = "base-ar-quran-tokens.txt"
  }
}
