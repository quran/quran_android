package com.quran.mobile.feature.memorize.model

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class SpeechModelStoreImportTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val modelBytes = ByteArray(5_000) { (it % 251).toByte() }
  private val tokensBytes = "a 0\nb 1\n".toByteArray()
  private val spec = SpeechModelSpec(
    id = "test-${UUID.randomUUID()}",
    model = ModelFile("model.onnx", "https://example.invalid/model.onnx", sha(modelBytes), modelBytes.size.toLong()),
    tokens = ModelFile("tokens.txt", "https://example.invalid/tokens.txt", sha(tokensBytes), tokensBytes.size.toLong()),
    license = "test",
    attribution = "test",
  )
  private val store = SpeechModelStore.forTest(context, spec)

  @Test
  fun `each file can be imported on its own, under any name`() = runTest {
    assertThat(store.importFile(file("renamed.bin", modelBytes))).containsExactly(spec.model)
    assertThat(store.missingFiles()).containsExactly(spec.tokens)
    assertThat(store.isInstalled()).isFalse()

    assertThat(store.importFile(file("tokens (1).txt", tokensBytes))).containsExactly(spec.tokens)
    assertThat(store.isInstalled()).isTrue()
    assertThat(store.state.value).isEqualTo(SpeechModelStore.State.Installed)
  }

  @Test
  fun `both files can be imported from the zip they came in`() = runTest {
    val zip = File(context.cacheDir, "model-${UUID.randomUUID()}.zip")
    ZipOutputStream(zip.outputStream()).use { out ->
      for ((name, bytes) in listOf("folder/" to null, "folder/model.onnx" to modelBytes, "folder/tokens.txt" to tokensBytes, "README.txt" to "hi".toByteArray())) {
        out.putNextEntry(ZipEntry(name))
        bytes?.let { out.write(it) }
        out.closeEntry()
      }
    }

    assertThat(store.importFile(Uri.fromFile(zip))).containsExactly(spec.model, spec.tokens)
    assertThat(store.isInstalled()).isTrue()
  }

  @Test
  fun `a file that isn't part of the model is ignored`() = runTest {
    assertThat(store.importFile(file("photo.jpg", ByteArray(5_000) { 7 }))).isEmpty()
    assertThat(store.importFile(file("big.bin", ByteArray(10_000)))).isEmpty()
    assertThat(store.missingFiles()).containsExactly(spec.model, spec.tokens)
  }

  @Test
  fun `a file as published is converted on import and checked`() = runTest {
    // e.g. the double-check's model: published as one file, used as another
    val published = "published model".toByteArray()
    val converted = "PUBLISHED MODEL".toByteArray()
    val upper = ModelConverter { source, target -> target.writeText(source.readText().uppercase()) }
    val convertedSpec = spec.copy(
      id = "test-${UUID.randomUUID()}",
      model = ModelFile(
        "model.onnx", "https://example.invalid/published.onnx", sha(converted), converted.size.toLong(),
        source = ModelFile.Source(sha(published), published.size.toLong(), upper),
      ),
    )
    val store = SpeechModelStore.forTest(context, convertedSpec)

    assertThat(store.importFile(file("published.onnx", published))).containsExactly(convertedSpec.model)
    assertThat(store.missingFiles()).containsExactly(convertedSpec.tokens)
    assertThat(store.modelFile.readBytes()).isEqualTo(converted)
    assertThat(convertedSpec.downloadSize).isEqualTo(published.size.toLong() + tokensBytes.size)

    // a conversion that doesn't give the expected file installs nothing
    val broken = SpeechModelStore.forTest(
      context,
      convertedSpec.copy(
        id = "test-${UUID.randomUUID()}",
        model = convertedSpec.model.copy(source = convertedSpec.model.source!!.copy(converter = { _, target -> target.writeText("?") })),
      ),
    )
    assertThat(broken.importFile(file("published.onnx", published))).isEmpty()
    assertThat(broken.modelFile.exists()).isFalse()
  }

  private fun file(name: String, bytes: ByteArray): Uri {
    val file = File(context.cacheDir, "${UUID.randomUUID()}-$name")
    file.writeBytes(bytes)
    return Uri.fromFile(file)
  }

  private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
