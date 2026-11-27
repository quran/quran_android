package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.quran.data.model.SuraAyah
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

/**
 * Loads and indexes the active AI Teacher full-Quran dataset (defaulting to
 * `husary_muallim_full.json` / `tilawi-husary-muallim-v1-reference`, with optional
 * `minshawi_teacher_full.json` / `tilawi-minshawi-teacher-v3-husary-ref`, all 6,236 verses
 * across Surahs `1..114`) with 0ms in-memory lookup by `surah:ayah`.
 *
 * Falls back transparently to bundled `assets/husary_muallim_juz30.json` (or
 * `assets/minshawi_teacher_juz30.json`) before the first-launch offline download completes, and
 * upgrades in-place via [reloadIfFullDatasetAvailable] as soon as the full JSON finishes downloading.
 */
object MinshawiJuz30Repository {

  private const val FALLBACK_ASSET_FILE_NAME = "husary_muallim_juz30.json"
  private const val DEFAULT_STREAM_BASE_URL = "https://everyayah.com/data/Husary_Muallim_128kbps/"

  /** Standard Madani ayah counts for Surahs 1..114 (index 0 = Surah 1). */
  val SURAH_AYAH_COUNTS = intArrayOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
    123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
    34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
    54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
    60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
    14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
    28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
    15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
    11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
    5, 4, 5, 6
  )

  /** Canonical Arabic names for Surahs 1..114 (index 0 = Surah 1). */
  val SURAH_NAMES_AR = arrayOf(
    "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
    "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
    "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
    "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
    "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
    "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
    "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
    "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
    "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
    "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
    "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
    "المسد", "الإخلاص", "الفلق", "الناس"
  )

  @Volatile
  private var activeTeacherQari: KidsTeacherQari = KidsTeacherQari.DEFAULT

  @Volatile
  private var loadedTeacherQari: KidsTeacherQari? = null

  @Volatile
  private var versesByKey: Map<String, TeacherVerseSpec> = emptyMap()

  @Volatile
  private var orderedKeys: List<String> = emptyList()

  @Volatile
  private var loadedFromFullFile: Boolean = false

  @Volatile
  private var currentManifest: MinshawiTeacherManifest? = null

  @JvmStatic
  fun getActiveTeacherQari(): KidsTeacherQari = activeTeacherQari

  @JvmStatic
  fun hasChildrenRepeat(): Boolean = currentManifest?.hasChildrenRepeat ?: activeTeacherQari.hasChildrenRepeat

  @JvmStatic
  fun setActiveTeacherQari(context: Context, qari: KidsTeacherQari) {
    synchronized(this) {
      if (activeTeacherQari == qari && loadedTeacherQari == qari && versesByKey.isNotEmpty()) {
        return
      }
      activeTeacherQari = qari
      loadedTeacherQari = null
      loadedFromFullFile = false
      versesByKey = emptyMap()
      orderedKeys = emptyList()
      currentManifest = null
    }
    ensureLoaded(context)
  }

  @JvmStatic
  fun isFullDatasetLoaded(): Boolean =
    loadedTeacherQari == activeTeacherQari && loadedFromFullFile && versesByKey.size > 1000

  @JvmStatic
  fun getManifest(): MinshawiTeacherManifest? = currentManifest

  @JvmStatic
  fun ensureLoaded(context: Context) {
    val targetQari = activeTeacherQari
    val hasFullFileOnDisk = KidsAssetDownloader.isTeacherFullJsonReady(context, targetQari)
    if (loadedTeacherQari == targetQari && versesByKey.isNotEmpty() && (!hasFullFileOnDisk || loadedFromFullFile)) {
      return
    }
    synchronized(this) {
      val qari = activeTeacherQari
      val fullReadyNow = KidsAssetDownloader.isTeacherFullJsonReady(context, qari)
      if (loadedTeacherQari == qari && versesByKey.isNotEmpty() && (!fullReadyNow || loadedFromFullFile)) {
        return
      }
      if (fullReadyNow) {
        val fullFile = KidsAssetDownloader.getTeacherFullJsonFile(context, qari)
        try {
          FileInputStream(fullFile).use { fis ->
            parseManifestStream(fis, qari)
          }
          if (versesByKey.isNotEmpty()) {
            loadedFromFullFile = true
            loadedTeacherQari = qari
            return
          }
        } catch (e: Exception) {
          Timber.e(e, "Failed to parse ${fullFile.name}; falling back to bundled Juz 30")
        }
      }
      if (loadedTeacherQari != qari || versesByKey.isEmpty()) {
        val assetName = qari.fallbackAssetFileName
        try {
          context.assets.open(assetName).use { input ->
            parseManifestStream(input, qari)
          }
          loadedFromFullFile = false
          loadedTeacherQari = qari
        } catch (e: Exception) {
          Timber.e(e, "Failed to load $assetName from assets")
          if (assetName != FALLBACK_ASSET_FILE_NAME) {
            try {
              context.assets.open(FALLBACK_ASSET_FILE_NAME).use { input ->
                parseManifestStream(input, KidsTeacherQari.DEFAULT)
              }
              loadedFromFullFile = false
              loadedTeacherQari = qari
            } catch (e2: Exception) {
              Timber.e(e2, "Failed to load fallback $FALLBACK_ASSET_FILE_NAME from assets")
            }
          }
        }
      }
    }
  }

  @JvmStatic
  fun reloadIfFullDatasetAvailable(context: Context): Boolean {
    val targetQari = activeTeacherQari
    if (!KidsAssetDownloader.isTeacherFullJsonReady(context, targetQari)) return false
    if (loadedTeacherQari == targetQari && loadedFromFullFile && versesByKey.size > 1000) return true
    synchronized(this) {
      val qari = activeTeacherQari
      if (loadedTeacherQari == qari && loadedFromFullFile && versesByKey.size > 1000) return true
      val fullFile = KidsAssetDownloader.getTeacherFullJsonFile(context, qari)
      if (!fullFile.exists()) return false
      return try {
        FileInputStream(fullFile).use { fis ->
          parseManifestStream(fis, qari)
        }
        loadedFromFullFile = versesByKey.size > 1000
        loadedTeacherQari = qari
        loadedFromFullFile
      } catch (e: Exception) {
        Timber.e(e, "Failed reloading ${qari.fullJsonFileName}")
        false
      }
    }
  }

  internal fun parseJson(jsonText: String): MinshawiTeacherManifest {
    val manifest = JsonReader(StringReader(jsonText)).use { reader ->
      readManifestFromJsonReader(reader, activeTeacherQari)
    }
    applyParsedManifest(manifest)
    return manifest
  }

  internal fun parseManifestOnly(jsonText: String): MinshawiTeacherManifest {
    return JsonReader(StringReader(jsonText)).use { reader ->
      readManifestFromJsonReader(reader, activeTeacherQari)
    }
  }

  private fun parseManifestStream(
    inputStream: InputStream,
    defaultQari: KidsTeacherQari = activeTeacherQari
  ) {
    val reader = JsonReader(BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 64 * 1024))
    val manifest = reader.use { readManifestFromJsonReader(it, defaultQari) }
    applyParsedManifest(manifest)
  }

  private fun applyParsedManifest(manifest: MinshawiTeacherManifest) {
    val sortedKeys = manifest.verses.keys.sortedWith { a, b ->
      val colonA = a.indexOf(':')
      val colonB = b.indexOf(':')
      val sa = if (colonA > 0) a.substring(0, colonA).toIntOrNull() ?: 0 else 0
      val sb = if (colonB > 0) b.substring(0, colonB).toIntOrNull() ?: 0 else 0
      if (sa != sb) {
        sa.compareTo(sb)
      } else {
        val aa = if (colonA > 0) a.substring(colonA + 1).toIntOrNull() ?: 0 else 0
        val ab = if (colonB > 0) b.substring(colonB + 1).toIntOrNull() ?: 0 else 0
        aa.compareTo(ab)
      }
    }
    val orderedMap = LinkedHashMap<String, TeacherVerseSpec>(sortedKeys.size)
    for (k in sortedKeys) {
      manifest.verses[k]?.let { orderedMap[k] = it }
    }
    currentManifest = manifest.copy(verses = orderedMap)
    versesByKey = orderedMap
    orderedKeys = sortedKeys
  }

  private fun readManifestFromJsonReader(
    reader: JsonReader,
    defaultQari: KidsTeacherQari = KidsTeacherQari.DEFAULT
  ): MinshawiTeacherManifest {
    var schema = if (defaultQari == KidsTeacherQari.HUSARY_MUALLIM) {
      "tilawi-husary-muallim-v1-reference"
    } else {
      "tilawi-minshawi-teacher-v3-husary-ref"
    }
    var qariId = defaultQari.qariId
    var qariNameEn = defaultQari.nameEn
    var qariNameAr = defaultQari.nameAr
    var isDefault = defaultQari == KidsTeacherQari.DEFAULT
    var isStopReference = defaultQari == KidsTeacherQari.HUSARY_MUALLIM
    var hasChildrenRepeat = defaultQari.hasChildrenRepeat
    var hasExplicitChildrenRepeat = false
    var streamBaseUrl = defaultQari.streamBaseUrl
    var juzValue: Any? = null
    val surahRange = mutableListOf<Int>()
    var totalAyat = 0
    var maxSpeakerSec: Double? = null
    val versesMap = LinkedHashMap<String, TeacherVerseSpec>(6400)

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "schema" -> {
          schema = reader.nextStringOrEmpty()
          if (!hasExplicitChildrenRepeat && schema.contains("minshawi", ignoreCase = true)) {
            hasChildrenRepeat = true
          } else if (!hasExplicitChildrenRepeat && schema.contains("husary", ignoreCase = true)) {
            hasChildrenRepeat = false
          }
        }
        "qari_id" -> {
          val raw = reader.nextStringOrEmpty()
          if (raw.isNotEmpty()) qariId = raw
        }
        "qari_name_en" -> {
          val raw = reader.nextStringOrEmpty()
          if (raw.isNotEmpty()) qariNameEn = raw
        }
        "qari_name_ar" -> {
          val raw = reader.nextStringOrEmpty()
          if (raw.isNotEmpty()) qariNameAr = raw
        }
        "is_default" -> isDefault = reader.nextBooleanOrDefault(isDefault)
        "is_stop_reference" -> isStopReference = reader.nextBooleanOrDefault(isStopReference)
        "has_children_repeat" -> {
          hasChildrenRepeat = reader.nextBooleanOrDefault(hasChildrenRepeat)
          hasExplicitChildrenRepeat = true
        }
        "stream_base_url" -> {
          val raw = cleanMarkdownUrl(reader.nextStringOrEmpty())
          if (raw.isNotEmpty()) streamBaseUrl = raw
        }
        "juz" -> {
          juzValue = when (reader.peek()) {
            JsonToken.NULL -> {
              reader.nextNull()
              null
            }
            JsonToken.NUMBER -> reader.nextInt()
            JsonToken.STRING -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            else -> {
              reader.skipValue()
              null
            }
          }
        }
        "surah_range" -> {
          if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            while (reader.hasNext()) {
              if (reader.peek() == JsonToken.NUMBER) {
                surahRange.add(reader.nextInt())
              } else {
                reader.skipValue()
              }
            }
            reader.endArray()
          } else {
            reader.skipValue()
          }
        }
        "total_ayat" -> totalAyat = reader.nextIntOrDefault(0)
        "max_speaker_sec" -> maxSpeakerSec = reader.nextNullableDouble()
        "verses" -> {
          if (reader.peek() == JsonToken.BEGIN_OBJECT) {
            reader.beginObject()
            while (reader.hasNext()) {
              val key = reader.nextName()
              if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                val spec = readVerseSpec(reader, key, streamBaseUrl)
                versesMap[key] = spec
              } else {
                reader.skipValue()
              }
            }
            reader.endObject()
          } else {
            reader.skipValue()
          }
        }
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return MinshawiTeacherManifest(
      schema = schema,
      streamBaseUrl = streamBaseUrl,
      juz = juzValue,
      surahRange = if (surahRange.isNotEmpty()) surahRange else listOf(1, 114),
      totalAyat = if (totalAyat > 0) totalAyat else versesMap.size,
      maxSpeakerSec = maxSpeakerSec,
      verses = versesMap,
      qariId = qariId,
      qariNameEn = qariNameEn,
      qariNameAr = qariNameAr,
      isDefault = isDefault,
      isStopReference = isStopReference,
      hasChildrenRepeat = hasChildrenRepeat
    )
  }

  private fun readVerseSpec(
    reader: JsonReader,
    verseKey: String,
    streamBaseUrl: String
  ): TeacherVerseSpec {
    val colonIdx = verseKey.indexOf(':')
    var surah = if (colonIdx > 0) verseKey.substring(0, colonIdx).toIntOrNull() ?: 1 else 1
    var ayah = if (colonIdx > 0) verseKey.substring(colonIdx + 1).toIntOrNull() ?: 1 else 1
    var surahNameAr = ""
    var uthmaniFull = ""
    var audioFile = ""
    var audioUrl = ""
    var fileDurationSec = 0.0
    var totalTurns = 1
    var nextSurah: Int? = null
    var nextAyah: Int? = null
    val rawTurns = ArrayList<TurnSpec>(2)

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "surah" -> surah = reader.nextIntOrDefault(surah)
        "ayah" -> ayah = reader.nextIntOrDefault(ayah)
        "surah_name_ar" -> surahNameAr = stripBom(reader.nextStringOrEmpty())
        "uthmani_full" -> uthmaniFull = stripBom(reader.nextStringOrEmpty())
        "audio_file" -> audioFile = reader.nextStringOrEmpty()
        "audio_url" -> audioUrl = cleanMarkdownUrl(reader.nextStringOrEmpty())
        "file_duration_sec" -> fileDurationSec = reader.nextDoubleOrDefault(0.0)
        "total_turns" -> totalTurns = reader.nextIntOrDefault(1).coerceAtLeast(1)
        "next_surah" -> nextSurah = reader.nextNullableInt()?.takeIf { it > 0 }
        "next_ayah" -> nextAyah = reader.nextNullableInt()?.takeIf { it > 0 }
        "turns" -> {
          if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            var idx = 0
            while (reader.hasNext()) {
              if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                rawTurns.add(readTurnSpec(reader, idx, totalTurns, fileDurationSec))
                idx++
              } else {
                reader.skipValue()
              }
            }
            reader.endArray()
          } else {
            reader.skipValue()
          }
        }
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    if (surahNameAr.isEmpty() && surah in 1..114) {
      surahNameAr = SURAH_NAMES_AR[surah - 1]
    }
    if (audioFile.isEmpty()) {
      audioFile = "%03d%03d.mp3".format(surah, ayah)
    }
    if (audioUrl.isEmpty()) {
      audioUrl = streamBaseUrl + audioFile
    }

    val sanitizedTurns = TurnSpec.sanitizeTurns(rawTurns, fileDurationSec)
    return TeacherVerseSpec(
      surah = surah,
      ayah = ayah,
      surahNameAr = surahNameAr,
      uthmaniFull = uthmaniFull,
      audioFile = audioFile,
      audioUrl = audioUrl,
      fileDurationSec = fileDurationSec,
      totalTurns = sanitizedTurns.size.coerceAtLeast(1),
      nextSurah = nextSurah,
      nextAyah = nextAyah,
      turns = sanitizedTurns
    )
  }

  private fun readTurnSpec(
    reader: JsonReader,
    fallbackIndex: Int,
    verseTotalTurns: Int,
    fileDurationSec: Double
  ): TurnSpec {
    var turnIdx = fallbackIndex
    var tTotal = verseTotalTurns
    var wStart = 0
    var wEnd = 0
    var qStart = 0.0
    var qEnd = 0.0
    var kStart = Double.NaN
    var kEnd = Double.NaN
    var contiguous = true
    var cutReason = ""
    val uthmaniWords = ArrayList<String>(8)
    val normalizedWords = ArrayList<String>(8)
    val qariOnnxWords = ArrayList<String>(8)
    val tokenIds = ArrayList<Int>(16)

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "turn_index" -> turnIdx = reader.nextIntOrDefault(fallbackIndex)
        "total_turns" -> tTotal = reader.nextIntOrDefault(verseTotalTurns).coerceAtLeast(1)
        "word_start_idx" -> wStart = reader.nextIntOrDefault(0)
        "word_end_idx" -> wEnd = reader.nextIntOrDefault(0)
        "qari_start_sec" -> qStart = reader.nextDoubleOrDefault(0.0)
        "qari_end_sec" -> qEnd = reader.nextDoubleOrDefault(0.0)
        "kids_start_sec" -> kStart = reader.nextDoubleOrDefault(Double.NaN)
        "kids_end_sec" -> kEnd = reader.nextDoubleOrDefault(Double.NaN)
        "student_pause_start_sec" -> {
          val v = reader.nextDoubleOrDefault(Double.NaN)
          if (kStart.isNaN()) kStart = v
        }
        "student_pause_end_sec" -> {
          val v = reader.nextDoubleOrDefault(Double.NaN)
          if (kEnd.isNaN()) kEnd = v
        }
        "contiguous_playback" -> contiguous = reader.nextBooleanOrDefault(true)
        "cut_reason" -> cutReason = reader.nextStringOrEmpty()
        "uthmani_words" -> reader.readStringListInto(uthmaniWords)
        "normalized_words" -> reader.readStringListInto(normalizedWords)
        "qari_onnx_words" -> reader.readStringListInto(qariOnnxWords)
        "token_ids" -> reader.readIntListInto(tokenIds)
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    val resolvedKidsStart = if (kStart.isNaN()) qEnd else kStart
    val resolvedKidsEnd = if (kEnd.isNaN()) fileDurationSec else kEnd

    return TurnSpec(
      turnIndex = turnIdx,
      totalTurns = tTotal,
      wordStartIdx = wStart,
      wordEndIdx = wEnd,
      qariStartSec = qStart,
      qariEndSec = qEnd,
      kidsStartSec = resolvedKidsStart,
      kidsEndSec = resolvedKidsEnd,
      contiguousPlayback = contiguous,
      cutReason = cutReason,
      uthmaniWords = uthmaniWords,
      normalizedWords = normalizedWords,
      qariOnnxWords = if (qariOnnxWords.isNotEmpty()) qariOnnxWords else normalizedWords,
      tokenIds = tokenIds
    )
  }

  private fun stripBom(raw: String): String = raw.replace("\ufeff", "").trim()

  private fun cleanMarkdownUrl(raw: String): String {
    val trimmed = raw.trim()
    // Clean accidental markdown link syntax `[https://...](https://...)` if ever present
    if (trimmed.startsWith("[") && trimmed.contains("](") && trimmed.endsWith(")")) {
      val openParen = trimmed.indexOf("](") + 2
      return trimmed.substring(openParen, trimmed.length - 1).trim()
    }
    return trimmed
  }

  private fun JsonReader.nextStringOrEmpty(): String {
    return when (peek()) {
      JsonToken.NULL -> {
        nextNull()
        ""
      }
      JsonToken.STRING, JsonToken.NUMBER -> nextString()
      JsonToken.BOOLEAN -> nextBoolean().toString()
      else -> {
        skipValue()
        ""
      }
    }
  }

  private fun JsonReader.nextIntOrDefault(defaultVal: Int): Int {
    return when (peek()) {
      JsonToken.NUMBER -> nextInt()
      JsonToken.STRING -> nextString().toIntOrNull() ?: defaultVal
      else -> {
        skipValue()
        defaultVal
      }
    }
  }

  private fun JsonReader.nextNullableInt(): Int? {
    return when (peek()) {
      JsonToken.NULL -> {
        nextNull()
        null
      }
      JsonToken.NUMBER -> nextInt()
      JsonToken.STRING -> nextString().toIntOrNull()
      else -> {
        skipValue()
        null
      }
    }
  }

  private fun JsonReader.nextDoubleOrDefault(defaultVal: Double): Double {
    return when (peek()) {
      JsonToken.NUMBER -> nextDouble()
      JsonToken.STRING -> nextString().toDoubleOrNull() ?: defaultVal
      else -> {
        skipValue()
        defaultVal
      }
    }
  }

  private fun JsonReader.nextNullableDouble(): Double? {
    return when (peek()) {
      JsonToken.NULL -> {
        nextNull()
        null
      }
      JsonToken.NUMBER -> nextDouble()
      JsonToken.STRING -> nextString().toDoubleOrNull()
      else -> {
        skipValue()
        null
      }
    }
  }

  private fun JsonReader.nextBooleanOrDefault(defaultVal: Boolean): Boolean {
    return when (peek()) {
      JsonToken.BOOLEAN -> nextBoolean()
      JsonToken.STRING -> nextString().toBooleanStrictOrNull() ?: defaultVal
      else -> {
        skipValue()
        defaultVal
      }
    }
  }

  private fun JsonReader.readStringListInto(out: MutableList<String>) {
    if (peek() != JsonToken.BEGIN_ARRAY) {
      skipValue()
      return
    }
    beginArray()
    while (hasNext()) {
      val s = stripBom(nextStringOrEmpty())
      if (s.isNotEmpty()) {
        out.add(s)
      }
    }
    endArray()
  }

  private fun JsonReader.readIntListInto(out: MutableList<Int>) {
    if (peek() != JsonToken.BEGIN_ARRAY) {
      skipValue()
      return
    }
    beginArray()
    while (hasNext()) {
      if (peek() == JsonToken.NUMBER) {
        out.add(nextInt())
      } else {
        skipValue()
      }
    }
    endArray()
  }

  @JvmStatic
  fun isSupportedAyah(suraAyah: SuraAyah): Boolean {
    return suraAyah.sura in 1..114 &&
      suraAyah.ayah in 1..getAyahCountForSurah(suraAyah.sura)
  }

  @JvmStatic
  fun isJuz30Ayah(suraAyah: SuraAyah): Boolean {
    return suraAyah.sura in 78..114
  }

  @JvmStatic
  fun getAyahCountForSurah(surah: Int): Int {
    if (surah !in 1..114) return 0
    return SURAH_AYAH_COUNTS[surah - 1]
  }

  @JvmStatic
  fun getSurahNameAr(surah: Int): String {
    if (surah !in 1..114) return ""
    return SURAH_NAMES_AR[surah - 1]
  }

  @JvmStatic
  fun getAllSurahs(context: Context): List<KidsSurahInfo> {
    ensureLoaded(context)
    return (1..114).map { surah ->
      KidsSurahInfo(
        surahNumber = surah,
        nameAr = SURAH_NAMES_AR[surah - 1],
        ayahCount = SURAH_AYAH_COUNTS[surah - 1],
        isLoadedOffline = versesByKey.containsKey("$surah:1")
      )
    }
  }

  @JvmStatic
  fun peekVerse(suraAyah: SuraAyah): TeacherVerseSpec? {
    return versesByKey["${suraAyah.sura}:${suraAyah.ayah}"]
  }

  @JvmStatic
  fun getVerse(context: Context, surah: Int, ayah: Int): TeacherVerseSpec? {
    ensureLoaded(context)
    return versesByKey["$surah:$ayah"]
  }

  @JvmStatic
  fun getVerse(context: Context, suraAyah: SuraAyah): TeacherVerseSpec? {
    return getVerse(context, suraAyah.sura, suraAyah.ayah)
  }

  @JvmStatic
  fun getPreviousVerse(context: Context, current: SuraAyah): TeacherVerseSpec? {
    ensureLoaded(context)
    val key = "${current.sura}:${current.ayah}"
    val idx = orderedKeys.indexOf(key)
    if (idx <= 0) return null
    return versesByKey[orderedKeys[idx - 1]]
  }

  @JvmStatic
  fun getNextVerse(context: Context, current: SuraAyah): TeacherVerseSpec? {
    ensureLoaded(context)
    val spec = versesByKey["${current.sura}:${current.ayah}"]
    if (spec?.nextSurah != null && spec.nextAyah != null) {
      versesByKey["${spec.nextSurah}:${spec.nextAyah}"]?.let { return it }
    }
    val idx = orderedKeys.indexOf("${current.sura}:${current.ayah}")
    if (idx in 0 until orderedKeys.lastIndex) {
      return versesByKey[orderedKeys[idx + 1]]
    }
    return null
  }

  @JvmStatic
  fun getAllVersesCount(context: Context): Int {
    ensureLoaded(context)
    return versesByKey.size
  }

  @JvmStatic
  fun getAllVerses(context: Context): Collection<TeacherVerseSpec> {
    ensureLoaded(context)
    return versesByKey.values
  }
}
