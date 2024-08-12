package com.quran.labs.androidquran.common.mapper

sealed class MadaniToKufiOperator(val suraNumber: Int)

data class RangeOffsetOperator(
  val sura: Int,
  val startAyah: Int,
  val endAyah: Int,
  val offset: Int
) : MadaniToKufiOperator(sura)

data class JoinOperator(
  val sura: Int,
  val startAyah: Int,
  val endAyah: Int,
  val targetAyah: Int
) : MadaniToKufiOperator(sura)

data class SplitOperator(
  val sura: Int,
  val ayah: Int,
  val firstAyah: Int,
  val secondAyah: Int,
  val thirdAyah: Int? = null
) : MadaniToKufiOperator(sura)

fun SplitOperator.lastAyah() = thirdAyah ?: secondAyah

fun MadaniToKufiOperator.appliesToSuraAyah(sura: Int, ayah: Int): Boolean {
  return suraNumber == sura &&
      when (this) {
        is RangeOffsetOperator -> this.startAyah <= ayah && this.endAyah >= ayah
        is JoinOperator -> this.startAyah <= ayah && this.endAyah >= ayah
        is SplitOperator -> this.ayah == ayah
      }
}
