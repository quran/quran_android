package com.quran.mobile.feature.memorize.check

import com.quran.mobile.feature.memorize.model.ModelConverter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Prepares the double-check model for sherpa-onnx, on the phone, from the file as published.
 *
 * sherpa-onnx runs raw-audio CTC models ("omnilingual ASR") with a single input, scaled to unit
 * variance. The Tilawi model takes the audio and its length, at the level it was trained on. So
 * the graph input `audio_signal` is renamed `audio_raw`, and three nodes are put in front of the
 * model: `audio_signal = 0.1 * audio_raw` and `length = [samples]` (a batch of one). Nothing else
 * changes.
 *
 * This edits the ONNX protobuf directly, without parsing the weights. They are copied through as
 * they are, so the 88 MB file never has to be in memory. The output is deterministic, and the store
 * checks its SHA-256 (see bench/tilawi_rename.py in the lab for the same conversion with onnx).
 */
internal object CheckGraph : ModelConverter {

  override fun convert(source: File, target: File) {
    RandomAccessFile(source, "r").use { input ->
      val top = fields(input, 0, input.length())
      val graph = top.singleOrNull { it.number == MODEL_GRAPH && it.wire == LENGTH_DELIMITED }
        ?: throw IOException("not an ONNX model")
      val parts = ArrayList<Part>()
      parts += Part.Bytes(frontNodes())
      var renamed = false
      for (field in fields(input, graph.contentStart, graph.end)) {
        if (field.number == GRAPH_INPUT && field.wire == LENGTH_DELIMITED) {
          val info = input.bytes(field.contentStart, field.end)
          when (name(info)) {
            LENGTH -> continue
            AUDIO -> {
              parts += Part.Bytes(message(GRAPH_INPUT, renamed(info)))
              renamed = true
              continue
            }
          }
        }
        parts += Part.Range(field.start, field.end)
      }
      if (!renamed) throw IOException("no $AUDIO input")
      val graphSize = parts.sumOf { it.size }
      FileOutputStream(target).use { stream ->
        val out = stream.channel
        for (field in top) {
          if (field === graph) {
            stream.write(key(MODEL_GRAPH, LENGTH_DELIMITED) + varint(graphSize))
            for (part in parts) {
              when (part) {
                is Part.Bytes -> stream.write(part.bytes)
                is Part.Range -> input.channel.copy(part.start, part.end, out)
              }
            }
          } else {
            input.channel.copy(field.start, field.end, out)
          }
        }
      }
    }
  }

  private sealed interface Part {
    val size: Long

    class Bytes(val bytes: ByteArray) : Part {
      override val size: Long get() = bytes.size.toLong()
    }

    class Range(val start: Long, val end: Long) : Part {
      override val size: Long get() = end - start
    }
  }

  private class Field(val number: Int, val wire: Int, val start: Long, val contentStart: Long, val end: Long)

  /** The fields of the message in [start, end) of [input]: where each starts and ends. */
  private fun fields(input: RandomAccessFile, start: Long, end: Long): List<Field> {
    val result = ArrayList<Field>()
    var at = start
    while (at < end) {
      input.seek(at)
      val key = input.varint()
      val number = (key ushr 3).toInt()
      val wire = (key and 7).toInt()
      val contentStart: Long
      val fieldEnd: Long
      when (wire) {
        VARINT -> {
          contentStart = input.filePointer
          input.varint()
          fieldEnd = input.filePointer
        }
        FIXED64 -> {
          contentStart = input.filePointer
          fieldEnd = contentStart + 8
        }
        LENGTH_DELIMITED -> {
          val length = input.varint()
          contentStart = input.filePointer
          fieldEnd = contentStart + length
        }
        FIXED32 -> {
          contentStart = input.filePointer
          fieldEnd = contentStart + 4
        }
        else -> throw IOException("unexpected wire type $wire at $at")
      }
      if (fieldEnd > end) throw IOException("truncated field at $at")
      result += Field(number, wire, at, contentStart, fieldEnd)
      at = fieldEnd
    }
    return result
  }

  /** The name of a ValueInfoProto. */
  private fun name(info: ByteArray): String? {
    var at = 0
    while (at < info.size) {
      val (key, afterKey) = varint(info, at)
      val wire = (key and 7).toInt()
      if (wire != LENGTH_DELIMITED) return null
      val (length, content) = varint(info, afterKey)
      if ((key ushr 3).toInt() == VALUE_INFO_NAME) return String(info, content, length.toInt(), Charsets.UTF_8)
      at = content + length.toInt()
    }
    return null
  }

  /** The ValueInfoProto of the audio input, renamed. */
  private fun renamed(info: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(string(VALUE_INFO_NAME, AUDIO_RAW))
    var at = 0
    while (at < info.size) {
      val (key, afterKey) = varint(info, at)
      val (length, content) = varint(info, afterKey)
      val end = content + length.toInt()
      if ((key ushr 3).toInt() != VALUE_INFO_NAME) out.write(info, at, end - at)
      at = end
    }
    return out.toByteArray()
  }

  private fun frontNodes(): ByteArray {
    val scale = ByteArrayOutputStream().apply {
      write(string(NODE_OUTPUT, SCALE))
      write(string(NODE_NAME, SCALE))
      write(string(NODE_OP_TYPE, "Constant"))
      val tensor = ByteArrayOutputStream().apply {
        write(key(TENSOR_DATA_TYPE, VARINT) + varint(TENSOR_FLOAT))
        write(string(TENSOR_NAME, SCALE))
        val bits = java.lang.Float.floatToIntBits(AUDIO_SCALE)
        write(message(TENSOR_RAW_DATA, ByteArray(4) { (bits ushr (8 * it)).toByte() }))
      }.toByteArray()
      write(
        message(
          NODE_ATTRIBUTE,
          string(ATTRIBUTE_NAME, "value") + message(ATTRIBUTE_TENSOR, tensor) +
            key(ATTRIBUTE_TYPE, VARINT) + varint(ATTRIBUTE_TYPE_TENSOR),
        ),
      )
    }.toByteArray()
    val mul = string(NODE_INPUT, AUDIO_RAW) + string(NODE_INPUT, SCALE) + string(NODE_OUTPUT, AUDIO) +
      string(NODE_NAME, "audio_scaled") + string(NODE_OP_TYPE, "Mul")
    val shape = string(NODE_INPUT, AUDIO_RAW) + string(NODE_OUTPUT, LENGTH) + string(NODE_NAME, "audio_length") +
      string(NODE_OP_TYPE, "Shape") +
      message(
        NODE_ATTRIBUTE,
        string(ATTRIBUTE_NAME, "start") + key(ATTRIBUTE_INT, VARINT) + varint(1) +
          key(ATTRIBUTE_TYPE, VARINT) + varint(ATTRIBUTE_TYPE_INT),
      )
    return message(GRAPH_NODE, scale) + message(GRAPH_NODE, mul) + message(GRAPH_NODE, shape)
  }

  private fun key(number: Int, wire: Int): ByteArray = varint(((number shl 3) or wire).toLong())

  private fun message(number: Int, content: ByteArray): ByteArray =
    key(number, LENGTH_DELIMITED) + varint(content.size.toLong()) + content

  private fun string(number: Int, value: String): ByteArray = message(number, value.toByteArray(Charsets.UTF_8))

  private fun varint(value: Long): ByteArray {
    val out = ByteArrayOutputStream()
    var v = value
    while (true) {
      if (v and 0x7FL.inv() == 0L) {
        out.write(v.toInt())
        return out.toByteArray()
      }
      out.write(((v and 0x7F) or 0x80).toInt())
      v = v ushr 7
    }
  }

  private fun varint(bytes: ByteArray, start: Int): Pair<Long, Int> {
    var result = 0L
    var shift = 0
    var at = start
    while (true) {
      if (at >= bytes.size || shift > 63) throw IOException("bad varint")
      val b = bytes[at++].toInt() and 0xFF
      result = result or ((b and 0x7F).toLong() shl shift)
      if (b and 0x80 == 0) return result to at
      shift += 7
    }
  }

  private fun RandomAccessFile.varint(): Long {
    var result = 0L
    var shift = 0
    while (true) {
      val b = read()
      if (b < 0 || shift > 63) throw IOException("bad varint")
      result = result or ((b and 0x7F).toLong() shl shift)
      if (b and 0x80 == 0) return result
      shift += 7
    }
  }

  private fun RandomAccessFile.bytes(start: Long, end: Long): ByteArray {
    seek(start)
    return ByteArray((end - start).toInt()).also { readFully(it) }
  }

  private fun java.nio.channels.FileChannel.copy(start: Long, end: Long, out: java.nio.channels.FileChannel) {
    var at = start
    while (at < end) at += transferTo(at, end - at, out)
  }

  private const val VARINT = 0
  private const val FIXED64 = 1
  private const val LENGTH_DELIMITED = 2
  private const val FIXED32 = 5

  // onnx.proto field numbers
  private const val MODEL_GRAPH = 7
  private const val GRAPH_NODE = 1
  private const val GRAPH_INPUT = 11
  private const val VALUE_INFO_NAME = 1
  private const val NODE_INPUT = 1
  private const val NODE_OUTPUT = 2
  private const val NODE_NAME = 3
  private const val NODE_OP_TYPE = 4
  private const val NODE_ATTRIBUTE = 5
  private const val ATTRIBUTE_NAME = 1
  private const val ATTRIBUTE_INT = 3
  private const val ATTRIBUTE_TENSOR = 5
  private const val ATTRIBUTE_TYPE = 20
  private const val ATTRIBUTE_TYPE_INT = 2L
  private const val ATTRIBUTE_TYPE_TENSOR = 4L
  private const val TENSOR_DATA_TYPE = 2
  private const val TENSOR_NAME = 8
  private const val TENSOR_RAW_DATA = 9
  private const val TENSOR_FLOAT = 1L

  private const val AUDIO = "audio_signal"
  private const val AUDIO_RAW = "audio_raw"
  private const val LENGTH = "length"
  private const val SCALE = "audio_scale"
  private const val AUDIO_SCALE = 0.1f
}
