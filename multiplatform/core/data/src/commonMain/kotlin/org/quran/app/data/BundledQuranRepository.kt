package org.quran.app.data
import org.quran.app.model.*
import org.quran.app.domain.QuranRepository
import org.quran.app.data.corpus.*
class BundledQuranRepository : QuranRepository {
 override fun chapters(): List<Chapter> = listOf(
Chapter(1,"الفاتحة","Al-Faatiha · The Opening",7),
Chapter(2,"البقرة","Al-Baqara · The Cow",286),
Chapter(3,"آل عمران","Aal-i-Imraan · The Family of Imraan",200),
Chapter(4,"النساء","An-Nisaa · The Women",176),
Chapter(5,"المائدة","Al-Maaida · The Table",120),
Chapter(6,"الأنعام","Al-An'aam · The Cattle",165),
Chapter(7,"الأعراف","Al-A'raaf · The Heights",206),
Chapter(8,"الأنفال","Al-Anfaal · The Spoils of War",75),
Chapter(9,"التوبة","At-Tawba · The Repentance",129),
Chapter(10,"يونس","Yunus · Jonas",109),
Chapter(11,"هود","Hud · Hud",123),
Chapter(12,"يوسف","Yusuf · Joseph",111),
Chapter(13,"الرعد","Ar-Ra'd · The Thunder",43),
Chapter(14,"ابراهيم","Ibrahim · Abraham",52),
Chapter(15,"الحجر","Al-Hijr · The Rock",99),
Chapter(16,"النحل","An-Nahl · The Bee",128),
Chapter(17,"الإسراء","Al-Israa · The Night Journey",111),
Chapter(18,"الكهف","Al-Kahf · The Cave",110),
Chapter(19,"مريم","Maryam · Mary",98),
Chapter(20,"طه","Taa-Haa · Taa-Haa",135),
Chapter(21,"الأنبياء","Al-Anbiyaa · The Prophets",112),
Chapter(22,"الحج","Al-Hajj · The Pilgrimage",78),
Chapter(23,"المؤمنون","Al-Muminoon · The Believers",118),
Chapter(24,"النور","An-Noor · The Light",64),
Chapter(25,"الفرقان","Al-Furqaan · The Criterion",77),
Chapter(26,"الشعراء","Ash-Shu'araa · The Poets",227),
Chapter(27,"النمل","An-Naml · The Ant",93),
Chapter(28,"القصص","Al-Qasas · The Stories",88),
Chapter(29,"العنكبوت","Al-Ankaboot · The Spider",69),
Chapter(30,"الروم","Ar-Room · The Romans",60),
Chapter(31,"لقمان","Luqman · Luqman",34),
Chapter(32,"السجدة","As-Sajda · The Prostration",30),
Chapter(33,"الأحزاب","Al-Ahzaab · The Clans",73),
Chapter(34,"سبإ","Saba · Sheba",54),
Chapter(35,"فاطر","Faatir · The Originator",45),
Chapter(36,"يس","Yaseen · Yaseen",83),
Chapter(37,"الصافات","As-Saaffaat · Those drawn up in Ranks",182),
Chapter(38,"ص","Saad · The letter Saad",88),
Chapter(39,"الزمر","Az-Zumar · The Groups",75),
Chapter(40,"غافر","Al-Ghaafir · The Forgiver",85),
Chapter(41,"فصلت","Fussilat · Explained in detail",54),
Chapter(42,"الشورى","Ash-Shura · Consultation",53),
Chapter(43,"الزخرف","Az-Zukhruf · Ornaments of gold",89),
Chapter(44,"الدخان","Ad-Dukhaan · The Smoke",59),
Chapter(45,"الجاثية","Al-Jaathiya · Crouching",37),
Chapter(46,"الأحقاف","Al-Ahqaf · The Dunes",35),
Chapter(47,"محمد","Muhammad · Muhammad",38),
Chapter(48,"الفتح","Al-Fath · The Victory",29),
Chapter(49,"الحجرات","Al-Hujuraat · The Inner Apartments",18),
Chapter(50,"ق","Qaaf · The letter Qaaf",45),
Chapter(51,"الذاريات","Adh-Dhaariyat · The Winnowing Winds",60),
Chapter(52,"الطور","At-Tur · The Mount",49),
Chapter(53,"النجم","An-Najm · The Star",62),
Chapter(54,"القمر","Al-Qamar · The Moon",55),
Chapter(55,"الرحمن","Ar-Rahmaan · The Beneficent",78),
Chapter(56,"الواقعة","Al-Waaqia · The Inevitable",96),
Chapter(57,"الحديد","Al-Hadid · The Iron",29),
Chapter(58,"المجادلة","Al-Mujaadila · The Pleading Woman",22),
Chapter(59,"الحشر","Al-Hashr · The Exile",24),
Chapter(60,"الممتحنة","Al-Mumtahana · She that is to be examined",13),
Chapter(61,"الصف","As-Saff · The Ranks",14),
Chapter(62,"الجمعة","Al-Jumu'a · Friday",11),
Chapter(63,"المنافقون","Al-Munaafiqoon · The Hypocrites",11),
Chapter(64,"التغابن","At-Taghaabun · Mutual Disillusion",18),
Chapter(65,"الطلاق","At-Talaaq · Divorce",12),
Chapter(66,"التحريم","At-Tahrim · The Prohibition",12),
Chapter(67,"الملك","Al-Mulk · The Sovereignty",30),
Chapter(68,"القلم","Al-Qalam · The Pen",52),
Chapter(69,"الحاقة","Al-Haaqqa · The Reality",52),
Chapter(70,"المعارج","Al-Ma'aarij · The Ascending Stairways",44),
Chapter(71,"نوح","Nooh · Noah",28),
Chapter(72,"الجن","Al-Jinn · The Jinn",28),
Chapter(73,"المزمل","Al-Muzzammil · The Enshrouded One",20),
Chapter(74,"المدثر","Al-Muddaththir · The Cloaked One",56),
Chapter(75,"القيامة","Al-Qiyaama · The Resurrection",40),
Chapter(76,"الانسان","Al-Insaan · Man",31),
Chapter(77,"المرسلات","Al-Mursalaat · The Emissaries",50),
Chapter(78,"النبإ","An-Naba · The Announcement",40),
Chapter(79,"النازعات","An-Naazi'aat · Those who drag forth",46),
Chapter(80,"عبس","Abasa · He frowned",42),
Chapter(81,"التكوير","At-Takwir · The Overthrowing",29),
Chapter(82,"الإنفطار","Al-Infitaar · The Cleaving",19),
Chapter(83,"المطففين","Al-Mutaffifin · Defrauding",36),
Chapter(84,"الإنشقاق","Al-Inshiqaaq · The Splitting Open",25),
Chapter(85,"البروج","Al-Burooj · The Constellations",22),
Chapter(86,"الطارق","At-Taariq · The Morning Star",17),
Chapter(87,"الأعلى","Al-A'laa · The Most High",19),
Chapter(88,"الغاشية","Al-Ghaashiya · The Overwhelming",26),
Chapter(89,"الفجر","Al-Fajr · The Dawn",30),
Chapter(90,"البلد","Al-Balad · The City",20),
Chapter(91,"الشمس","Ash-Shams · The Sun",15),
Chapter(92,"الليل","Al-Lail · The Night",21),
Chapter(93,"الضحى","Ad-Dhuhaa · The Morning Hours",11),
Chapter(94,"الشرح","Ash-Sharh · The Consolation",8),
Chapter(95,"التين","At-Tin · The Fig",8),
Chapter(96,"العلق","Al-Alaq · The Clot",19),
Chapter(97,"القدر","Al-Qadr · The Power, Fate",5),
Chapter(98,"البينة","Al-Bayyina · The Evidence",8),
Chapter(99,"الزلزلة","Az-Zalzala · The Earthquake",8),
Chapter(100,"العاديات","Al-Aadiyaat · The Chargers",11),
Chapter(101,"القارعة","Al-Qaari'a · The Calamity",11),
Chapter(102,"التكاثر","At-Takaathur · Competition",8),
Chapter(103,"العصر","Al-Asr · The Declining Day, Epoch",3),
Chapter(104,"الهمزة","Al-Humaza · The Traducer",9),
Chapter(105,"الفيل","Al-Fil · The Elephant",5),
Chapter(106,"قريش","Quraish · Quraysh",4),
Chapter(107,"الماعون","Al-Maa'un · Almsgiving",7),
Chapter(108,"الكوثر","Al-Kawthar · Abundance",3),
Chapter(109,"الكافرون","Al-Kaafiroon · The Disbelievers",6),
Chapter(110,"النصر","An-Nasr · Divine Support",3),
Chapter(111,"المسد","Al-Masad · The Palm Fibre",5),
Chapter(112,"الإخلاص","Al-Ikhlaas · Sincerity",4),
Chapter(113,"الفلق","Al-Falaq · The Dawn",5),
Chapter(114,"الناس","An-Naas · Mankind",6))
 override fun verses(surah: Int, language: AppLanguage): List<Verse> {
 val text = when(surah) {
1 -> chapter1Text()
2 -> chapter2Text()
3 -> chapter3Text()
4 -> chapter4Text()
5 -> chapter5Text()
6 -> chapter6Text()
7 -> chapter7Text()
8 -> chapter8Text()
9 -> chapter9Text()
10 -> chapter10Text()
11 -> chapter11Text()
12 -> chapter12Text()
13 -> chapter13Text()
14 -> chapter14Text()
15 -> chapter15Text()
16 -> chapter16Text()
17 -> chapter17Text()
18 -> chapter18Text()
19 -> chapter19Text()
20 -> chapter20Text()
21 -> chapter21Text()
22 -> chapter22Text()
23 -> chapter23Text()
24 -> chapter24Text()
25 -> chapter25Text()
26 -> chapter26Text()
27 -> chapter27Text()
28 -> chapter28Text()
29 -> chapter29Text()
30 -> chapter30Text()
31 -> chapter31Text()
32 -> chapter32Text()
33 -> chapter33Text()
34 -> chapter34Text()
35 -> chapter35Text()
36 -> chapter36Text()
37 -> chapter37Text()
38 -> chapter38Text()
39 -> chapter39Text()
40 -> chapter40Text()
41 -> chapter41Text()
42 -> chapter42Text()
43 -> chapter43Text()
44 -> chapter44Text()
45 -> chapter45Text()
46 -> chapter46Text()
47 -> chapter47Text()
48 -> chapter48Text()
49 -> chapter49Text()
50 -> chapter50Text()
51 -> chapter51Text()
52 -> chapter52Text()
53 -> chapter53Text()
54 -> chapter54Text()
55 -> chapter55Text()
56 -> chapter56Text()
57 -> chapter57Text()
58 -> chapter58Text()
59 -> chapter59Text()
60 -> chapter60Text()
61 -> chapter61Text()
62 -> chapter62Text()
63 -> chapter63Text()
64 -> chapter64Text()
65 -> chapter65Text()
66 -> chapter66Text()
67 -> chapter67Text()
68 -> chapter68Text()
69 -> chapter69Text()
70 -> chapter70Text()
71 -> chapter71Text()
72 -> chapter72Text()
73 -> chapter73Text()
74 -> chapter74Text()
75 -> chapter75Text()
76 -> chapter76Text()
77 -> chapter77Text()
78 -> chapter78Text()
79 -> chapter79Text()
80 -> chapter80Text()
81 -> chapter81Text()
82 -> chapter82Text()
83 -> chapter83Text()
84 -> chapter84Text()
85 -> chapter85Text()
86 -> chapter86Text()
87 -> chapter87Text()
88 -> chapter88Text()
89 -> chapter89Text()
90 -> chapter90Text()
91 -> chapter91Text()
92 -> chapter92Text()
93 -> chapter93Text()
94 -> chapter94Text()
95 -> chapter95Text()
96 -> chapter96Text()
97 -> chapter97Text()
98 -> chapter98Text()
99 -> chapter99Text()
100 -> chapter100Text()
101 -> chapter101Text()
102 -> chapter102Text()
103 -> chapter103Text()
104 -> chapter104Text()
105 -> chapter105Text()
106 -> chapter106Text()
107 -> chapter107Text()
108 -> chapter108Text()
109 -> chapter109Text()
110 -> chapter110Text()
111 -> chapter111Text()
112 -> chapter112Text()
113 -> chapter113Text()
114 -> chapter114Text()
 else -> throw IllegalArgumentException("Surah must be 1..114")
 }
 return text.mapIndexed { index, arabic -> Verse(VerseId(surah,index+1), arabic, "", "Tanzil Uthmani 1.1 · https://tanzil.net") }
 }
}
