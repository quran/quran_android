package com.quran.mobile.feature.memorize.check

import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class CheckGraphTest {

  @get:Rule val folder = TemporaryFolder()

  private fun resource(name: String): File =
    File(requireNotNull(javaClass.classLoader?.getResource("check/$name")).toURI())

  @Test
  fun `the conversion is the same as the reference one, byte for byte`() {
    // a tiny model with the Tilawi model's inputs; tiny_converted.onnx is what onnx makes of it with
    // the same changes (bench/tilawi_rename.py)
    val target = folder.newFile("converted.onnx")

    CheckGraph.convert(resource("tiny.onnx"), target)

    assertThat(target.readBytes()).isEqualTo(resource("tiny_converted.onnx").readBytes())
  }

  @Test(expected = IOException::class)
  fun `something that isn't a model is refused`() {
    val notModel = folder.newFile("tokens.txt").apply { writeText("p0 0\n<blk> 1\n") }
    CheckGraph.convert(notModel, folder.newFile("out.onnx"))
  }

  @Test
  fun `the real model converts to the pinned file`() {
    // local only: the published model, if it was downloaded to the lab
    val source = File(System.getProperty("user.home"), "quran-asr-lab/models/muhdur__tilawi-fastconformer-quran/fastconformer_full_mixed.onnx")
    assumeTrue(source.exists())
    val target = folder.newFile("tilawi.onnx")

    CheckGraph.convert(source, target)

    val reference = File(System.getProperty("user.home"), "quran-asr-lab/models/tilawi_sherpa/tilawi_renamed.onnx")
    if (reference.exists()) assertThat(sha256(target)).isEqualTo(sha256(reference))
    println("converted sha256 ${sha256(target)} size ${target.length()}")
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(1 shl 16)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}
