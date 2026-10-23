package com.quran.mobile.feature.memorize.check

import android.content.Context
import android.util.JsonReader
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorize.model.ModelFile
import com.quran.mobile.feature.memorize.model.SpeechModelSpec
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.io.IOException

/** Creates the double-check's recognizer from its model files; replaced by a fake in tests. */
fun interface PieceRecognizerFactory {
  fun create(model: File, tokens: File): PieceRecognizer
}

/**
 * The double-check's model (optional, see [DoubleCheck]): downloaded on request from where it is
 * published, verified, and prepared for the app's speech engine ([CheckGraph]).
 */
@SingleIn(AppScope::class)
class DoubleCheckModel private constructor(
  val store: SpeechModelStore,
  private val recognizers: PieceRecognizerFactory,
) : DoubleCheckSource {
  @Inject constructor(@ApplicationContext context: Context, recognizers: PieceRecognizerFactory) :
    this(SpeechModelStore.of(context, SPEC), recognizers)

  val isInstalled: Boolean get() = store.isInstalled()

  /** Loads the check (seconds, and ~300 MB of memory): not on the main thread. Null if it can't. */
  override fun open(): DoubleCheck? {
    if (!store.isInstalled()) return null
    return try {
      val directory = store.modelFile.parentFile!!
      val pieces = readVocabulary(File(directory, SPEC.tokens.name))
      val tokens = File(directory, SHERPA_TOKENS)
      // the speech engine names piece i "p<i>" and needs the blank by name (the last piece)
      val expected = pieces.indices.joinToString("") { i -> if (i == pieces.lastIndex) "<blk> $i\n" else "p$i $i\n" }
      if (!tokens.exists() || tokens.readText() != expected) tokens.writeText(expected)
      val clean = readCleanText(File(directory, QURAN.name))
      val recognizer = recognizers.create(store.modelFile, tokens)
      TilawiDoubleCheck(recognizer, pieces.map { CheckText.pieceLetters(it) }) { sura, ayah -> clean[sura * 1000 + ayah] }
    } catch (_: IOException) {
      null
    } catch (_: IllegalStateException) {
      // e.g. a malformed file, or the engine couldn't load the model
      null
    }
  }

  fun delete() = store.delete()

  /** vocab.json: {"0": "<unk>", "1": "ة", ...}, every id from 0. */
  private fun readVocabulary(file: File): List<String> {
    val pieces = HashMap<Int, String>()
    JsonReader(file.reader()).use { reader ->
      reader.beginObject()
      while (reader.hasNext()) pieces[reader.nextName().toInt()] = reader.nextString()
      reader.endObject()
    }
    return List(pieces.size) { pieces[it] ?: throw IOException("vocab.json has no piece $it") }
  }

  /** quran.json: [{"surah": 1, "ayah": 1, "text_clean": "...", ...}, ...]. */
  private fun readCleanText(file: File): Map<Int, String> {
    val text = HashMap<Int, String>(6_300)
    JsonReader(file.reader()).use { reader ->
      reader.beginArray()
      while (reader.hasNext()) {
        var sura = 0
        var ayah = 0
        var clean: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "surah" -> sura = reader.nextInt()
            "ayah" -> ayah = reader.nextInt()
            "text_clean" -> clean = reader.nextString()
            else -> reader.skipValue()
          }
        }
        reader.endObject()
        if (clean != null) text[sura * 1000 + ayah] = clean
      }
      reader.endArray()
    }
    return text
  }

  companion object {
    // The Tilawi FastConformer by muhdur (CC-BY-4.0), as published, pinned to a commit and
    // checksummed. The model is converted for the app's speech engine once downloaded (CheckGraph).
    private const val BASE =
      "https://huggingface.co/muhdur/tilawi-fastconformer-quran/resolve/e9448a0e84f3adb64c28b7a8db501dd8e0e59a84"
    private const val SHERPA_TOKENS = "tokens.txt"

    private val QURAN = ModelFile(
      name = "quran.json",
      url = "$BASE/quran.json",
      sha256 = "6e6f31f642c701b49a1ba090311ca4c7a97c6a5b79a302712dff815a9d7b3d03",
      size = 3_186_385,
    )

    val SPEC = SpeechModelSpec(
      id = "tilawi-fastconformer-e9448a0",
      model = ModelFile(
        name = "tilawi.onnx",
        url = "$BASE/fastconformer_full_mixed.onnx",
        sha256 = "2ff3cb3a6e01d3822fd8cb68bbb14f9dcccb1c98dd67c070d7b76c13914bea79",
        size = 88_307_524,
        source = ModelFile.Source(
          sha256 = "4767182cd92975869f81a7e32700b14ca2b04e8dc97a15ff220a8697f4639488",
          size = 88_307_366,
          converter = CheckGraph,
        ),
      ),
      tokens = ModelFile(
        name = "vocab.json",
        url = "$BASE/vocab.json",
        sha256 = "c55877f3bff8bc3aaefc160e8c2fb88cb349088d092513d40210ccfe535e671b",
        size = 21_062,
      ),
      // the Quran in plain letters (Tanzil Project, CC BY 3.0), only to compare what the model writes
      extras = listOf(QURAN),
      license = "CC-BY-4.0",
      attribution = "muhdur, Tilawi FastConformer (tilawi-fastconformer-quran); Quran text: Tanzil Project",
    )

    internal fun forTest(store: SpeechModelStore, recognizers: PieceRecognizerFactory) = DoubleCheckModel(store, recognizers)
  }
}
