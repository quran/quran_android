package com.quran.labs.androidquran.tv.surah

/**
 * Represents a Surah (chapter) of the Quran
 */
data class SurahModel(
  val number: Int,
  val name: String,
  val englishName: String,
  val englishNameTranslation: String,
  val revelationType: String, // "Meccan" or "Medinan"
  val numberOfAyahs: Int,
  val startingPage: Int
)

/**
 * Helper to get all 114 surahs with their basic information
 * This is a simplified version - the actual data should come from QuranInfo
 */
fun getAllSurahs(): List<SurahModel> {
  // This is a placeholder - in production, this should come from QuranInfo and translations
  val meccanSurahs = listOf(
    96, 68, 73, 74, 1, 111, 81, 87, 92, 89, 93, 94, 103, 100, 108, 102, 107, 109, 105, 113, 114, 112,
    53, 20, 91, 85, 95, 75, 106, 101, 86, 11, 10, 12, 18, 27, 37, 31, 34, 39, 40, 41, 42, 43, 44, 45,
    46, 51, 52, 56, 58, 67, 69, 70, 78, 79, 82, 84, 88, 90, 77, 79, 98
  )

  val surahNames = listOf(
    "Al-Fatihah", "Al-Baqarah", "Ali 'Imran", "An-Nisa'", "Al-Ma'idah", "Al-An'am", "Al-A'raf", "Al-Anfal",
    "At-Tawbah", "Yunus", "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr", "An-Nahl", "Al-Isra'", "Al-Kahf",
    "Maryam", "Ta-Ha", "Al-Anbiya'", "Al-Hajj", "Al-Mu'minun", "An-Nur", "Ash-Shu'ara'", "An-Naml", "Al-Qasas",
    "Al-'Ankabut", "Ar-Rum", "Luqman", "As-Sajdah", "Al-Ahzab", "Saba'", "Fatir", "Ya-Sin", "As-Saffat", "Sad",
    "Az-Zumar", "Ghafir", "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiyah", "Al-Ahqaf",
    "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf", "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman",
    "Al-Waqi'ah", "Al-Hadid", "Al-Mujadilah", "Al-Hashr", "Al-Mumtahanah", "As-Saff", "Al-Jumu'ah", "Al-Munafiqun",
    "At-Taghabun", "At-Talaq", "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Ma'arij", "Nuh", "Al-Jinn",
    "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah", "Al-Insan", "Al-Mursalat", "An-Naba'", "An-Nazi'at",
    "'Abasa", "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj", "At-Tariq", "Al-A'la",
    "Al-Ghashiyah", "Al-Fajr", "Al-Balad", "Ash-Shams", "Al-Layl", "Ad-Duha", "Al-Inshirah", "At-Tin",
    "Al-'Alaq", "Al-Qadr", "Al-Bayyinah", "Az-Zalzalah", "Al-'Adiyat", "Al-Qari'ah", "At-Takathur", "Al-'Asr",
    "Al-Humazah", "Al-Fil", "Quraysh", "Al-Ma'un", "Al-Kawthar", "Al-Kafirun", "An-Nasr", "Al-Masad",
    "Al-Ikhlas", "Al-Falaq", "An-Nas"
  )

  val englishNames = listOf(
    "The Opener", "The Cow", "Family of Imran", "The Women", "The Table Spread", "The Cattle", "The Heights",
    "The Spoils of War", "The Repentance", "Jonah", "Hud", "Joseph", "The Thunder", "Abraham", "The Rocky Tract",
    "The Bee", "The Night Journey", "The Cave", "Mary", "Ta-Ha", "The Prophets", "The Pilgrimage", "The Believers",
    "The Light", "The Poets", "The Ant", "The Stories", "The Spider", "The Romans", "Luqman", "The Prostration",
    "The Combined Forces", "Sheba", "Originator", "Ya-Sin", "Those who set the Ranks", "The Letter Saad",
    "The Troops", "The Forgiver", "Explained in Detail", "The Consultation", "The Ornaments of Gold", "The Smoke",
    "The Crouching", "The Wind-Curved Sandhills", "Muhammad", "The Victory", "The Rooms", "The Letter Qaf",
    "The Winnowing Winds", "The Mount", "The Star", "The Moon", "The Beneficent", "The Inevitable", "The Iron",
    "The Pleading Woman", "The Exile", "She that is to be examined", "The Ranks", "The Congregation", "The Hypocrites",
    "The Mutual Disillusion", "The Divorce", "The Prohibition", "The Sovereignty", "The Pen", "The Reality",
    "The Ascending Stairways", "Noah", "The Jinn", "The Enshrouded One", "The Cloaked One", "The Resurrection",
    "The Man", "The Emissaries", "The Tidings", "Those who drag forth", "He Frowned", "The Overthrowing",
    "The Cleaving", "The Defrauding", "The Sundering", "The Mansions of the Stars", "The Nightcomer", "The Most High",
    "The Overwhelming", "The Dawn", "The City", "The Sun", "The Night", "The Morning Hours", "The Relief",
    "The Fig", "The Clot", "The Power of Night", "The Clear Proof", "The Earthquake", "The Courser", "The Calamity",
    "The Rivalry in World Increase", "The Declining Day", "The Traducer", "The Elephant", "Quraysh", "The Small Kindnesses",
    "The Abundance", "The Disbelievers", "The Divine Support", "The Palm Fiber", "The Sincerity", "The Daybreak",
    "Mankind"
  )

  // Simplified page numbers - in production, use QuranInfo.getPageNumberForSura()
  val startingPages = listOf(
    1, 2, 50, 77, 106, 128, 151, 177, 187, 208, 221, 235, 249, 255, 262, 267, 282, 293, 305, 312, 322,
    332, 342, 350, 357, 367, 377, 385, 396, 404, 411, 415, 418, 428, 434, 440, 446, 453, 458, 467, 477,
    483, 489, 496, 499, 502, 507, 511, 515, 518, 520, 523, 526, 528, 531, 534, 537, 542, 545, 549, 551,
    553, 554, 556, 558, 560, 562, 564, 566, 568, 570, 572, 574, 575, 577, 578, 580, 582, 583, 585, 586,
    587, 587, 589, 590, 591, 591, 592, 593, 594, 595, 595, 596, 596, 597, 597, 598, 598, 599, 599, 600,
    600, 601, 601, 602, 602, 602, 603, 603, 603, 604, 604
  )

  val ayahCounts = listOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78,
    118, 64, 227, 93, 88, 69, 60, 54, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35,
    38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 44, 14, 11,
    11, 18, 12, 12, 30, 52, 44, 14, 11, 11, 18, 12, 12, 29, 52, 44, 14, 11, 11, 18, 12, 12, 28, 52, 44,
    14, 11, 11, 18, 12, 12, 28, 54, 44, 14, 11, 11, 18, 12, 12, 26, 52, 44, 14, 11, 11, 18, 12, 12, 20,
    52, 44, 14, 11, 11, 18, 12, 12, 19, 52, 44, 14, 11, 11, 18, 12, 12, 19
  )

  return (1..114).map { index ->
    SurahModel(
      number = index,
      name = surahNames[index - 1],
      englishName = englishNames[index - 1],
      englishNameTranslation = englishNames[index - 1],
      revelationType = if (index in meccanSurahs) "Meccan" else "Medinan",
      numberOfAyahs = ayahCounts[index - 1],
      startingPage = startingPages[index - 1]
    )
  }
}
