package com.quran.labs.androidquran.common;

import com.quran.labs.androidquran.util.QuranSettings;

public class QuranInfo {
	
	public static String[] SURA_NAMES = {
		"Al-Fatiha", "Al-Baqara", "Aal-E-Imran", "An-Nisa", "Al-Maeda",
		"Al-Anaam", "Al-Araf", "Al-Anfal", "At-Tawba", "Yunus", "Hud",
		"Yusuf", "Ar-Rad", "Ibrahim", "Al-Hijr", "An-Nahl", "Al-Isra",
		"Al-Kahf", "Maryam", "Ta-Ha", "Al-Anbiya", "Al-Hajj", "Al-Mumenoon",
		"An-Noor", "Al-Furqan", "Ash-Shu'ara", "An-Naml", "Al-Qasas",
		"Al-Ankaboot", "Ar-Room", "Luqman", "As-Sajda", "Al-Ahzab", "Saba",
		"Fatir", "Ya-Seen", "As-Saaffat", "Sad", "Az-Zumar", "Ghafir",
		"Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiya",
		"Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujraat", "Qaf",
		"Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman",
		"Al-Waqia", "Al-Hadid", "Al-Mujadila", "Al-Hashr", "Al-Mumtahina",
		"As-Saff", "Al-Jumua", "Al-Munafiqoon", "At-Taghabun", "At-Talaq",
		"At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haaqqa", "Al-Maarij", "Nooh",
		"Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyama", "Al-Insan",
		"Al-Mursalat", "An-Naba", "An-Naziat", "Abasa", "At-Takwir",
		"Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Burooj", "At-Tariq",
		"Al-Ala", "Al-Ghashiya", "Al-Fajr", "Al-Balad", "Ash-Shams",
		"Al-Lail", "Ad-Dhuha", "Al-Inshirah", "At-Tin", "Al-Alaq", "Al-Qadr",
		"Al-Bayyina", "Az-Zalzala", "Al-Adiyat", "Al-Qaria", "At-Takathur",
		"Al-Asr", "Al-Humaza", "Al-Fil", "Quraish", "Al-Maun", "Al-Kauther",
		"Al-Kafiroon", "An-Nasr", "Al-Masadd", "Al-Ikhlas", "Al-Falaq",
		"An-Nas"
	};
	
	public static String[] SURA_NAMES_AR = {
		"الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة",
		"اﻷنعام", "اﻷعراف", "اﻷنفال", "التوبة", "يونس", "هود",
		"يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "اﻹسراء",
		"الكهف", "مريم", "طه", "اﻷنبياء", "الحج", "المؤمنون",
		"النور", "الفرقان", "الشعراء", "النمل", "القصص",
		"العنكبوت", "الروم", "لقمان", "السجدة", "اﻷحزاب", "سبأ",
		"فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
		"فصلت", "الشعراء", "الزخرف", "الدخان", "الجاثية",
		"الحاقة", "محمد", "الفتح", "الحجرات", "ق",
		"الذاريات", "الطور", "النجم", "القمر", "الرحمن",
		"الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
		"الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق",
		"التحريم", "الملك", "القلم", "الحاقة", "المعارج", "نوح",
		"الجن", "المزمل", "المدثر", "القيامة", "اﻹنسان",
		"المرسلات", "النبأ", "النازعات", "عبس", "التكوير",
		"الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق",
		"اﻷعلى", "الغاشية", "الفجر", "البلد", "الشمس",
		"الليل", "الضحى", "الشرح", "التين", "العلق", "القدر",
		"البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر",
		"العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر",
		"الكافرون", "النصر", "المسد", "اﻹخلاص", "الفلق",
		"الناس"
	};
	
	public static String getSuraTitle() {
		return QuranSettings.getInstance().isArabicNames() ? "سورة" : "Surat";
	}
	
	public static String getSuraName(int index) {
		return QuranSettings.getInstance().isArabicNames() ? SURA_NAMES_AR[index] : SURA_NAMES[index];
	}
	
	public static String getPageTitle() {
		return QuranSettings.getInstance().isArabicNames() ? "القرآن الكريم، صفحة " : "Quran, page ";
	}
	
	public static int[] SURA_PAGE_START = {
		1, 2, 50, 77, 106, 128, 151, 177, 187, 208, 221, 235, 249, 255, 262,
		267, 282, 293, 305, 312, 322, 332, 342, 350, 359, 367, 377, 385, 396,
		404, 411, 415, 418, 428, 434, 440, 446, 453, 458, 467, 477, 483, 489,
		496, 499, 502, 507, 511, 515, 518, 520, 523, 526, 528, 531, 534, 537,
		542, 545, 549, 551, 553, 554, 556, 558, 560, 562, 564, 566, 568, 570,
		572, 574, 575, 577, 578, 580, 582, 583, 585, 586, 587, 587, 589, 590,
		591, 591, 592, 593, 594, 595, 595, 596, 596, 597, 597, 598, 598, 599,
		599, 600, 600, 601, 601, 601, 602, 602, 602, 603, 603, 603, 604, 604,
		604
	};
	
	public static String getSuraNameFromPage(int page){
		for (int i = 0; i < 114; i++){
			if (SURA_PAGE_START[i] == page)
				return getSuraName(i);
			else if (SURA_PAGE_START[i] > page)
				return getSuraName(i-1);
		}
		return "";
	}
}
