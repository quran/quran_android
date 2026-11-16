package com.quran.mobile.feature.memorize.check

import android.content.Context
import android.util.JsonReader
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.data.LexiconRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class CheckTextTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `words are reduced to the letters the model writes`() {
    // hamza seats and alif wasla unified, marks and tatweel dropped, runs collapsed
    assertThat(CheckText.wordSkeleton("الحمد")).isEqualTo("الحمد")
    assertThat(CheckText.wordSkeleton("ٱلْعَٰلَمِينَ")).isEqualTo("العالمين")
    assertThat(CheckText.wordSkeleton("أُو۟لَـٰٓئِكَ")).isEqualTo("اولاءك")
    assertThat(CheckText.wordSkeleton("الرحمن")).isEqualTo("الرحمن")
    assertThat(CheckText.wordSkeleton("مدد")).isEqualTo("مد")
  }

  @Test
  fun `a piece starting a word gets a word boundary`() {
    assertThat(CheckText.pieceLetters("▁ال")).containsExactly("|", "ا", "ل").inOrder()
    assertThat(CheckText.pieceLetters("ة")).containsExactly("ه")
    assertThat(CheckText.pieceLetters("َ")).isEmpty()
  }

  @Test
  fun `a sura's first ayah is compared without the basmala the clean text starts it with`() {
    val skeletons = CheckText.ayahSkeletons(
      sura = 112, ayah = 1,
      lexicon = listOf("قُل", "هُوَ", "للَااهُ", "ءَحَدڇ"),
      clean = "بسم الله الرحمن الرحيم قل هو الله احد",
    )

    // runs collapse: الله is written اله
    assertThat(skeletons).containsExactly("قل", "هو", "اله", "احد").inOrder()
  }

  @Test
  fun `words split differently are aligned by their letters`() {
    // e.g. "ba'dama" is one word in the clean text and two in the lexicon
    assertThat(CheckText.alignWords(listOf("بعد", "ما", "جاءهم"), listOf("بعدما", "جاءهم")))
      .containsExactly("بعد", "ما", "جاءهم").inOrder()
    assertThat(CheckText.alignWords(listOf("يبنوم", "قال"), listOf("يبن", "ام", "قال")))
      .containsExactly("يبنام", "قال").inOrder()
  }

  @Test
  fun `the pieces are the lab's, for every piece of the vocabulary`() {
    // local only: the lab's copy of the model's vocab.json and its export (bench/check_export.py)
    val vocabulary = lab("models/muhdur__tilawi-fastconformer-quran/vocab.json")
    val pieces = lab("cache/check/pieces.tsv")
    assumeTrue(vocabulary.exists() && pieces.exists())
    val vocab = HashMap<Int, String>()
    JsonReader(vocabulary.reader()).use { reader ->
      reader.beginObject()
      while (reader.hasNext()) vocab[reader.nextName().toInt()] = reader.nextString()
      reader.endObject()
    }

    for (line in pieces.readLines()) {
      val id = line.substringBefore('\t').toInt()
      val expected = line.substringAfter('\t').split(',').filter { it.isNotEmpty() }
      assertThat(CheckText.pieceLetters(vocab.getValue(id))).isEqualTo(expected)
    }
  }

  @Test
  fun `every word's skeleton is the lab's`() = runTest {
    // local only: quran.json as published with the model, and the lab's table (bench/check_export.py)
    val quran = lab("models/muhdur__tilawi-fastconformer-quran/quran.json")
    val words = lab("cache/check/words.tsv")
    assumeTrue(quran.exists() && words.exists())
    val clean = HashMap<Int, String>()
    JsonReader(quran.reader()).use { reader ->
      reader.beginArray()
      while (reader.hasNext()) {
        var sura = 0
        var ayah = 0
        var text = ""
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "surah" -> sura = reader.nextInt()
            "ayah" -> ayah = reader.nextInt()
            "text_clean" -> text = reader.nextString()
            else -> reader.skipValue()
          }
        }
        reader.endObject()
        clean[sura * 1000 + ayah] = text
      }
      reader.endArray()
    }
    val expected = words.readLines().associate { line ->
      val (sura, ayah, word, skeleton) = line.split('\t')
      Triple(sura.toInt(), ayah.toInt(), word.toInt()) to skeleton
    }
    val lexicon = LexiconRepository.forTest(context, StandardTestDispatcher(testScheduler))

    var compared = 0
    for (sura in 1..114) {
      val passage = lexicon.passage(SuraAyah(sura, 1))
      for ((ayah, ayahWords) in passage.words.groupBy { it.id.ayah }) {
        val skeletons = CheckText.ayahSkeletons(sura, ayah, ayahWords.map { it.wasl }, clean.getValue(sura * 1000 + ayah))
        skeletons.forEachIndexed { i, skeleton ->
          assertThat(skeleton).isEqualTo(expected[Triple(sura, ayah, i + 1)])
          compared++
        }
      }
    }
    assertThat(compared).isEqualTo(77_433)
  }

  private fun lab(path: String) = File(System.getProperty("user.home"), "quran-asr-lab/$path")
}
