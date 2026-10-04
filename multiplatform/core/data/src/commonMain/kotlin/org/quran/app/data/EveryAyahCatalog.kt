package org.quran.app.data

import org.quran.app.model.Reciter

/** Small explicit whitelist; provider's Warsh and other sets are intentionally excluded. */
object EveryAyahCatalog {
    const val DEFAULT_RECITER_ID = "alafasy"
    val reciters: List<Reciter> = listOf(
        reciter("alafasy", "Mishary Rashid Alafasy", "مشاري راشد العفاسي", "Alafasy_128kbps"),
        reciter("husary", "Mahmoud Khalil Al-Husary", "محمود خليل الحصري", "Husary_128kbps"),
        reciter("sudais", "Abdurrahman As-Sudais", "عبد الرحمن السديس", "Abdurrahmaan_As-Sudais_192kbps"),
    )
    fun requireReciter(id: String): Reciter = reciters.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unsupported reciter identifier")

    private fun reciter(id: String, english: String, arabic: String, folder: String) =
        Reciter(id, english, arabic, folder, "https://everyayah.com/data/$folder/")
}
