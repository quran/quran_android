package com.quran.labs.androidquran.common.mapper

object MadaniToKufiMap {
  val translationMap = mapOf(
      1 to listOf(
          // 1:1-1:5 madani are 1:2-1:6 kufi
          RangeOffsetOperator(sura = 1, startAyah = 1, endAyah = 5, offset = 1),
          // 1:6-1:7 madani are both 1:7 kufi
          JoinOperator(sura = 1, startAyah = 6, endAyah = 7, targetAyah = 7)
      ),
      2 to listOf(
          // 2:1 in madani is 2:1-2:2 in kufi
          SplitOperator(sura = 2, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 2:2-2:198 in madani is 2:3-2:199 in kufi
          RangeOffsetOperator(sura = 2, startAyah = 2, endAyah = 198, offset = 1),
          // 2:199 in madani is 2:200 and 2:201 in kufi
          SplitOperator(sura = 2, ayah = 199, firstAyah = 200, secondAyah = 201),
          // 2:200-2:252 in madani are 2:202-254 in kufi
          RangeOffsetOperator(sura = 2, startAyah = 200, endAyah = 252, offset = 2),
          // 2:253-2:254 in madani map to 2:255 in kufi
          JoinOperator(sura = 2, startAyah = 253, endAyah = 254, targetAyah = 255),
          // 2:255-2:285 in madani map to 2:256-2:286 in kufi
          RangeOffsetOperator(sura = 2, startAyah = 255, endAyah = 285, offset = 1)
      ),
      3 to listOf(
          // 3:1 madani is 3:1-3:2 kufi
          SplitOperator(sura = 3, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 3:2 in madani map to 3:3 in kufi
          RangeOffsetOperator(sura = 3, startAyah = 2, endAyah = 2, offset = 1),
          // 3:3-3:4 in madani map to 3:4 in kufi
          JoinOperator(sura = 3, startAyah = 3, endAyah = 4, targetAyah = 4),
          // 3:5-3:47 no difference
          RangeOffsetOperator(sura = 3, startAyah = 5, endAyah = 47, offset = 0),
          // 3:48 madani is 3:48-3:49 kufi
          SplitOperator(sura = 3, ayah = 48, firstAyah = 48, secondAyah = 49),
          // 3:49-3:90 in madani map to 3:50-3:91 in kufi
          RangeOffsetOperator(sura = 3, startAyah = 49, endAyah = 90, offset = 1),
          // 3:91-3:92 in madani map to 3:92 in kufi
          JoinOperator(sura = 3, startAyah = 91, endAyah = 92, targetAyah = 92),
          // 3:93-3:200 no difference
          RangeOffsetOperator(sura = 3, startAyah = 93, endAyah = 200, offset = 0)
      ),
      4 to listOf(
          // 4:1-4:43 no difference
          RangeOffsetOperator(sura = 4, startAyah = 1, endAyah = 43, offset = 0),
          // 4:44 madani is 4:44-4:45 kufi
          SplitOperator(sura = 4, ayah = 44, firstAyah = 44, secondAyah = 45),
          // 4:45-4:175 in madani map to 4:46-4:176 in kufi
          RangeOffsetOperator(sura = 4, startAyah = 45, endAyah = 175, offset = 1)
      ),
      5 to listOf(
          // 5:1-5:2 in madani map to 5:1 in kufi
          JoinOperator(sura = 5, startAyah = 1, endAyah = 2, targetAyah = 1),
          // 5:3-5:15 in madani map to 5:2-5:14 in kufi
          RangeOffsetOperator(sura = 5, startAyah = 3, endAyah = 15, offset = -1),
          // 5:16-5:17 in madani map to 5:15 in kufi
          JoinOperator(sura = 5, startAyah = 16, endAyah = 17, targetAyah = 15),
          // 5:18-5:122 in madani map to 5:16-5:120 in kufi
          RangeOffsetOperator(sura = 5, startAyah = 18, endAyah = 122, offset = -2)
      ),
      6 to listOf(
          // 6:1-6:2 in madani map to 6:1 in kufi
          JoinOperator(sura = 6, startAyah = 1, endAyah = 2, targetAyah = 1),
          // 6:3-6:66 in madani map to 6:2-6:65 in kufi
          RangeOffsetOperator(sura = 6, startAyah = 3, endAyah = 66, offset = -1),
          // 6:67 madani is 6:66-6:67 kufi
          SplitOperator(sura = 6, ayah = 67, firstAyah = 66, secondAyah = 67),
          // 6:68-6:72 no difference
          RangeOffsetOperator(sura = 6, startAyah = 68, endAyah = 72, offset = 0),
          // 6:73-6:74 in madani map to 6:73 in kufi
          JoinOperator(sura = 6, startAyah = 73, endAyah = 74, targetAyah = 73),
          // 6:75-6:161 in madani map to 6:74-6:160 in kufi
          RangeOffsetOperator(sura = 6, startAyah = 75, endAyah = 161, offset = -1),
          // 6:162-6:163 in madani map to 6:161 in kufi
          JoinOperator(sura = 6, startAyah = 162, endAyah = 163, targetAyah = 161),
          // 6:164-6:167 in madani map to 6:162-6:165 in kufi
          RangeOffsetOperator(sura = 6, startAyah = 164, endAyah = 167, offset = -2)
      ),
      7 to listOf(
          // 7:1 madani is 7:1-7:2 kufi
          SplitOperator(sura = 7, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 7:2-7:27 in madani map to 7:3-7:28 in kufi
          RangeOffsetOperator(sura = 7, startAyah = 2, endAyah = 27, offset = 1),
          // 7:28 madani is 7:29-7:30 kufi
          SplitOperator(sura = 7, ayah = 28, firstAyah = 29, secondAyah = 30),
          // 7:29-7:35 in madani map to 7:31-7:37 in kufi
          RangeOffsetOperator(sura = 7, startAyah = 29, endAyah = 35, offset = 2),
          // 7:36-7:37 in madani map to 7:38 in kufi
          JoinOperator(sura = 7, startAyah = 36, endAyah = 37, targetAyah = 38),
          // 7:38-7:135 in madani map to 7:39-7:136 in kufi
          RangeOffsetOperator(sura = 7, startAyah = 38, endAyah = 135, offset = 1),
          // 7:136-7:137 in madani map to 7:137 in kufi
          JoinOperator(sura = 7, startAyah = 136, endAyah = 137, targetAyah = 137),
          // 7:138-7:206 no difference
          RangeOffsetOperator(sura = 7, startAyah = 138, endAyah = 206, offset = 0)
      ),
      8 to listOf(
          // 8:1-8:41 no difference
          RangeOffsetOperator(sura = 8, startAyah = 1, endAyah = 41, offset = 0),
          // 8:42-8:43 in madani map to 8:42 in kufi
          JoinOperator(sura = 8, startAyah = 42, endAyah = 43, targetAyah = 42),
          // 8:44-8:76 in madani map to 8:43-8:75 in kufi
          RangeOffsetOperator(sura = 8, startAyah = 44, endAyah = 76, offset = -1)
      ),
      9 to listOf(
          // 9:1-9:69 no difference
          RangeOffsetOperator(sura = 9, startAyah = 1, endAyah = 69, offset = 0),
          // 9:70-9:71 in madani map to 9:70 in kufi
          JoinOperator(sura = 9, startAyah = 70, endAyah = 71, targetAyah = 70),
          // 9:72-9:130 in madani map to 9:71-9:129 in kufi
          RangeOffsetOperator(sura = 9, startAyah = 72, endAyah = 130, offset = -1)
      ),
      10 to listOf(
          // 10:1-10:109 no difference
          RangeOffsetOperator(sura = 10, startAyah = 1, endAyah = 109, offset = 0)
      ),
      11 to listOf(
          // 11:1-11:53 no difference
          RangeOffsetOperator(sura = 11, startAyah = 1, endAyah = 53, offset = 0),
          // 11:54 madani is 11:54-11:55 kufi
          SplitOperator(sura = 11, ayah = 54, firstAyah = 54, secondAyah = 55),
          // 11:55-11:84 in madani map to 11:56-11:85 in kufi
          RangeOffsetOperator(sura = 11, startAyah = 55, endAyah = 84, offset = 1),
          // 11:85-11:86 in madani map to 11:86 in kufi
          JoinOperator(sura = 11, startAyah = 85, endAyah = 86, targetAyah = 86),
          // 11:87-11:117 no difference
          RangeOffsetOperator(sura = 11, startAyah = 87, endAyah = 117, offset = 0),
          // 11:118 madani is 11:118-11:119 kufi
          SplitOperator(sura = 11, ayah = 118, firstAyah = 118, secondAyah = 119),
          // 11:119 in madani map to 11:120 in kufi
          RangeOffsetOperator(sura = 11, startAyah = 119, endAyah = 119, offset = 1),
          // 11:120 madani is 11:121-11:122 kufi
          SplitOperator(sura = 11, ayah = 120, firstAyah = 121, secondAyah = 122),
          // 11:121 in madani map to 11:123 in kufi
          RangeOffsetOperator(sura = 11, startAyah = 121, endAyah = 121, offset = 2)
      ),
      12 to listOf(
          // 12:1-12:111 no difference
          RangeOffsetOperator(sura = 12, startAyah = 1, endAyah = 111, offset = 0)
      ),
      13 to listOf(
          // 13:1-13:4 no difference
          RangeOffsetOperator(sura = 13, startAyah = 1, endAyah = 4, offset = 0),
          // 13:5-13:6 in madani map to 13:5 in kufi
          JoinOperator(sura = 13, startAyah = 5, endAyah = 6, targetAyah = 5),
          // 13:7-13:16 in madani map to 13:6-13:15 in kufi
          RangeOffsetOperator(sura = 13, startAyah = 7, endAyah = 16, offset = -1),
          // 13:17-13:18 in madani map to 13:16 in kufi
          JoinOperator(sura = 13, startAyah = 17, endAyah = 18, targetAyah = 16),
          // 13:19-13:24 in madani map to 13:17-13:22 in kufi
          RangeOffsetOperator(sura = 13, startAyah = 19, endAyah = 24, offset = -2),
          // 13:25 madani is 13:23-13:24 kufi
          SplitOperator(sura = 13, ayah = 25, firstAyah = 23, secondAyah = 24),
          // 13:26-13:44 in madani map to 13:25-13:43 in kufi
          RangeOffsetOperator(sura = 13, startAyah = 26, endAyah = 44, offset = -1)
      ),
      14 to listOf(
          // 14:1-14:2 in madani map to 14:1 in kufi
          JoinOperator(sura = 14, startAyah = 1, endAyah = 2, targetAyah = 1),
          // 14:3-14:5 in madani map to 14:2-14:4 in kufi
          RangeOffsetOperator(sura = 14, startAyah = 3, endAyah = 5, offset = -1),
          // 14:6-14:7 in madani map to 14:5 in kufi
          JoinOperator(sura = 14, startAyah = 6, endAyah = 7, targetAyah = 5),
          // 14:8-14:10 in madani map to 14:6-14:8 in kufi
          RangeOffsetOperator(sura = 14, startAyah = 8, endAyah = 10, offset = -2),
          // 14:11-14:12 in madani map to 14:9 in kufi
          JoinOperator(sura = 14, startAyah = 11, endAyah = 12, targetAyah = 9),
          // 14:13-14:21 in madani map to 14:10-14:18 in kufi
          RangeOffsetOperator(sura = 14, startAyah = 13, endAyah = 21, offset = -3),
          // 14:22 madani is 14:19-14:20 kufi
          SplitOperator(sura = 14, ayah = 22, firstAyah = 19, secondAyah = 20),
          // 14:23-14:54 in madani map to 14:21-14:52 in kufi
          RangeOffsetOperator(sura = 14, startAyah = 23, endAyah = 54, offset = -2)
      ),
      15 to listOf(
          // 15:1-15:99 no difference
          RangeOffsetOperator(sura = 15, startAyah = 1, endAyah = 99, offset = 0)
      ),
      16 to listOf(
          // 16:1-16:128 no difference
          RangeOffsetOperator(sura = 16, startAyah = 1, endAyah = 128, offset = 0)
      ),
      17 to listOf(
          // 17:1-17:106 no difference
          RangeOffsetOperator(sura = 17, startAyah = 1, endAyah = 106, offset = 0),
          // 17:107 madani is 17:107-17:108 kufi
          SplitOperator(sura = 17, ayah = 107, firstAyah = 107, secondAyah = 108),
          // 17:108-17:110 in madani map to 17:109-17:111 in kufi
          RangeOffsetOperator(sura = 17, startAyah = 108, endAyah = 110, offset = 1)
      ),
      18 to listOf(
          // 18:1-18:34 no difference
          RangeOffsetOperator(sura = 18, startAyah = 1, endAyah = 34, offset = 0),
          // 18:35 madani is 18:35-18:36 kufi
          SplitOperator(sura = 18, ayah = 35, firstAyah = 35, secondAyah = 36),
          // 18:36-18:83 in madani map to 18:37-18:84 in kufi
          RangeOffsetOperator(sura = 18, startAyah = 36, endAyah = 83, offset = 1),
          // 18:84 madani is 18:85-18:86 kufi
          SplitOperator(sura = 18, ayah = 84, firstAyah = 85, secondAyah = 86),
          // 18:85-18:86 in madani map to 18:87-18:88 in kufi
          RangeOffsetOperator(sura = 18, startAyah = 85, endAyah = 86, offset = 2),
          // 18:87 madani is 18:89-18:90 kufi
          SplitOperator(sura = 18, ayah = 87, firstAyah = 89, secondAyah = 90),
          // 18:88 in madani map to 18:91 in kufi
          RangeOffsetOperator(sura = 18, startAyah = 88, endAyah = 88, offset = 3),
          // 18:89 madani is 18:92-18:93 kufi
          SplitOperator(sura = 18, ayah = 89, firstAyah = 92, secondAyah = 93),
          // 18:90-18:98 in madani map to 18:94-18:102 in kufi
          RangeOffsetOperator(sura = 18, startAyah = 90, endAyah = 98, offset = 4),
          // 18:99 madani is 18:103-18:104 kufi
          SplitOperator(sura = 18, ayah = 99, firstAyah = 103, secondAyah = 104),
          // 18:100-18:105 in madani map to 18:105-18:110 in kufi
          RangeOffsetOperator(sura = 18, startAyah = 100, endAyah = 105, offset = 5)
      ),
      19 to listOf(
          // 19:1 madani is 19:1-19:2 kufi
          SplitOperator(sura = 19, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 19:2-19:39 in madani map to 19:3-19:40 in kufi
          RangeOffsetOperator(sura = 19, startAyah = 2, endAyah = 39, offset = 1),
          // 19:40-19:41 in madani map to 19:41 in kufi
          JoinOperator(sura = 19, startAyah = 40, endAyah = 41, targetAyah = 41),
          // 19:42-19:74 no difference
          RangeOffsetOperator(sura = 19, startAyah = 42, endAyah = 74, offset = 0),
          // 19:75-19:76 in madani map to 19:75 in kufi
          JoinOperator(sura = 19, startAyah = 75, endAyah = 76, targetAyah = 75),
          // 19:77-19:99 in madani map to 19:76-19:98 in kufi
          RangeOffsetOperator(sura = 19, startAyah = 77, endAyah = 99, offset = -1)
      ),
      20 to listOf(
          // 20:1 madani is 20:1-20:2 kufi
          SplitOperator(sura = 20, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 20:2-20:134 in madani map to 20:3-20:135 in kufi
          RangeOffsetOperator(sura = 20, startAyah = 2, endAyah = 134, offset = 1)
      ),
      21 to listOf(
          // 21:1-21:65 no difference
          RangeOffsetOperator(sura = 21, startAyah = 1, endAyah = 65, offset = 0),
          // 21:66 madani is 21:66-21:67 kufi
          SplitOperator(sura = 21, ayah = 66, firstAyah = 66, secondAyah = 67),
          // 21:67-21:111 in madani map to 21:68-21:112 in kufi
          RangeOffsetOperator(sura = 21, startAyah = 67, endAyah = 111, offset = 1)
      ),
      22 to listOf(
          // 22:1-22:18 no difference
          RangeOffsetOperator(sura = 22, startAyah = 1, endAyah = 18, offset = 0),
          // 22:19 madani is 22:19-22:21 kufi
          SplitOperator(sura = 22, ayah = 19, firstAyah = 19, secondAyah = 20, thirdAyah = 21),
          // 22:20-22:76 in madani map to 22:22-22:78 in kufi
          RangeOffsetOperator(sura = 22, startAyah = 20, endAyah = 76, offset = 2)
      ),
      23 to listOf(
          // 23:1-23:44 no difference
          RangeOffsetOperator(sura = 23, startAyah = 1, endAyah = 44, offset = 0),
          // 23:45-23:46 in madani map to 23:45 in kufi
          JoinOperator(sura = 23, startAyah = 45, endAyah = 46, targetAyah = 45),
          // 23:47-23:119 in madani map to 23:46-23:118 in kufi
          RangeOffsetOperator(sura = 23, startAyah = 47, endAyah = 119, offset = -1)
      ),
      24 to listOf(
          // 24:1-24:35 no difference
          RangeOffsetOperator(sura = 24, startAyah = 1, endAyah = 35, offset = 0),
          // 24:36 madani is 24:36-24:37 kufi
          SplitOperator(sura = 24, ayah = 36, firstAyah = 36, secondAyah = 37),
          // 24:37-24:41 in madani map to 24:38-24:42 in kufi
          RangeOffsetOperator(sura = 24, startAyah = 37, endAyah = 41, offset = 1),
          // 24:42 madani is 24:43-24:44 kufi
          SplitOperator(sura = 24, ayah = 42, firstAyah = 43, secondAyah = 44),
          // 24:43-24:62 in madani map to 24:45-24:64 in kufi
          RangeOffsetOperator(sura = 24, startAyah = 43, endAyah = 62, offset = 2)
      ),
      25 to listOf(
          // 25:1-25:77 no difference
          RangeOffsetOperator(sura = 25, startAyah = 1, endAyah = 77, offset = 0)
      ),
      26 to listOf(
          // 26:1 madani is 26:1-26:2 kufi
          SplitOperator(sura = 26, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 26:2-26:47 in madani map to 26:3-26:48 in kufi
          RangeOffsetOperator(sura = 26, startAyah = 2, endAyah = 47, offset = 1),
          // 26:48-26:49 in madani map to 26:49 in kufi
          JoinOperator(sura = 26, startAyah = 48, endAyah = 49, targetAyah = 49),
          // 26:50-26:209 no difference
          RangeOffsetOperator(sura = 26, startAyah = 50, endAyah = 209, offset = 0),
          // 26:210 madani is 26:210-26:211 kufi
          SplitOperator(sura = 26, ayah = 210, firstAyah = 210, secondAyah = 211),
          // 26:211-26:226 in madani map to 26:212-26:227 in kufi
          RangeOffsetOperator(sura = 26, startAyah = 211, endAyah = 226, offset = 1)
      ),
      27 to listOf(
          // 27:1-27:32 no difference
          RangeOffsetOperator(sura = 27, startAyah = 1, endAyah = 32, offset = 0),
          // 27:33-27:34 in madani map to 27:33 in kufi
          JoinOperator(sura = 27, startAyah = 33, endAyah = 34, targetAyah = 33),
          // 27:35-27:44 in madani map to 27:34-27:43 in kufi
          RangeOffsetOperator(sura = 27, startAyah = 35, endAyah = 44, offset = -1),
          // 27:45-27:46 in madani map to 27:44 in kufi
          JoinOperator(sura = 27, startAyah = 45, endAyah = 46, targetAyah = 44),
          // 27:47-27:95 in madani map to 27:45-27:93 in kufi
          RangeOffsetOperator(sura = 27, startAyah = 47, endAyah = 95, offset = -2)
      ),
      28 to listOf(
          // 28:1 madani is 28:1-28:2 kufi
          SplitOperator(sura = 28, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 28:2-28:21 in madani map to 28:3-28:22 in kufi
          RangeOffsetOperator(sura = 28, startAyah = 2, endAyah = 21, offset = 1),
          // 28:22-28:23 in madani map to 28:23 in kufi
          JoinOperator(sura = 28, startAyah = 22, endAyah = 23, targetAyah = 23),
          // 28:24-28:88 no difference
          RangeOffsetOperator(sura = 28, startAyah = 24, endAyah = 88, offset = 0)
      ),
      29 to listOf(
          // 29:1 madani is 29:1-29:2 kufi
          SplitOperator(sura = 29, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 29:2-29:27 in madani map to 29:3-29:28 in kufi
          RangeOffsetOperator(sura = 29, startAyah = 2, endAyah = 27, offset = 1),
          // 29:28-29:29 in madani map to 29:28 in kufi
          JoinOperator(sura = 29, startAyah = 28, endAyah = 29, targetAyah = 28),
          // 29:30-29:69 no difference
          RangeOffsetOperator(sura = 29, startAyah = 30, endAyah = 69, offset = 0)
      ),
      30 to listOf(
          // 30:1 madani is 30:1-30:3 kufi
          SplitOperator(sura = 30, ayah = 1, firstAyah = 1, secondAyah = 2, thirdAyah = 3),
          // 30:2-30:3 in madani map to 30:4 in kufi
          JoinOperator(sura = 30, startAyah = 2, endAyah = 3, targetAyah = 4),
          // 30:4-30:59 in madani map to 30:5-30:60 in kufi
          RangeOffsetOperator(sura = 30, startAyah = 4, endAyah = 59, offset = 1),
      ),
      31 to listOf(
          // 31:1 madani is 31:1-31:2 kufi
          SplitOperator(sura = 31, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 31:2-31:33 in madani map to 31:3-31:34 in kufi
          RangeOffsetOperator(sura = 31, startAyah = 2, endAyah = 33, offset = 1)
      ),
      32 to listOf(
          // 32:1 madani is 32:1-32:2 kufi
          SplitOperator(sura = 32, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 32:2-32:8 in madani map to 32:3-32:9 in kufi
          RangeOffsetOperator(sura = 32, startAyah = 2, endAyah = 8, offset = 1),
          // 32:9-32:10 in madani map to 32:10 in kufi
          JoinOperator(sura = 32, startAyah = 9, endAyah = 10, targetAyah = 10),
          // 32:11-32:30 no difference
          RangeOffsetOperator(sura = 32, startAyah = 11, endAyah = 30, offset = 0)
      ),
      33 to listOf(
          // 33:1-33:73 no difference
          RangeOffsetOperator(sura = 33, startAyah = 1, endAyah = 73, offset = 0)
      ),
      34 to listOf(
          // 34:1-34:54 no difference
          RangeOffsetOperator(sura = 34, startAyah = 1, endAyah = 54, offset = 0)
      ),
      35 to listOf(
          // 35:1-35:42 no difference
          RangeOffsetOperator(sura = 35, startAyah = 1, endAyah = 42, offset = 0),
          // 35:43-35:44 in madani map to 35:43 in kufi
          JoinOperator(sura = 35, startAyah = 43, endAyah = 44, targetAyah = 43),
          // 35:45-35:46 in madani map to 35:44-35:45 in kufi
          RangeOffsetOperator(sura = 35, startAyah = 45, endAyah = 46, offset = -1),
      ),
      36 to listOf(
          // 36:1 madani is 36:1-36:2 kufi
          SplitOperator(sura = 36, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 36:2-36:82 in madani map to 36:3-36:83 in kufi
          RangeOffsetOperator(sura = 36, startAyah = 2, endAyah = 82, offset = 1)
      ),
      37 to listOf(
          // 37:1-37:182 no difference
          RangeOffsetOperator(sura = 37, startAyah = 1, endAyah = 182, offset = 0)
      ),
      38 to listOf(
          // 38:1 madani is 38:1-38:2 kufi
          SplitOperator(sura = 38, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 38:2-38:82 in madani map to 38:3-38:83 in kufi
          RangeOffsetOperator(sura = 38, startAyah = 2, endAyah = 82, offset = 1),
          // 38:83 madani is 38:84-38:85 kufi
          SplitOperator(sura = 38, ayah = 83, firstAyah = 84, secondAyah = 85),
          // 38:84-38:86 in madani map to 38:86-38:88 in kufi
          RangeOffsetOperator(sura = 38, startAyah = 84, endAyah = 86, offset = 2)
      ),
      39 to listOf(
          // 39:1-39:2 no difference
          RangeOffsetOperator(sura = 39, startAyah = 1, endAyah = 2, offset = 0),
          // 39:3-39:4 in madani map to 39:3 in kufi
          JoinOperator(sura = 39, startAyah = 3, endAyah = 4, targetAyah = 3),
          // 39:5-39:11 in madani map to 39:4-39:10 in kufi
          RangeOffsetOperator(sura = 39, startAyah = 5, endAyah = 11, offset = -1),
          // 39:12 madani is 39:11-39:12 kufi
          SplitOperator(sura = 39, ayah = 12, firstAyah = 11, secondAyah = 12),
          // 39:13 no difference
          RangeOffsetOperator(sura = 39, startAyah = 13, endAyah = 13, offset = 0),
          // 39:14 madani is 39:14-39:15 kufi
          SplitOperator(sura = 39, ayah = 14, firstAyah = 14, secondAyah = 15),
          // 39:15-39:34 in madani map to 39:16-39:35 in kufi
          RangeOffsetOperator(sura = 39, startAyah = 15, endAyah = 34, offset = 1),
          // 39:35 madani is 39:36-39:37 kufi
          SplitOperator(sura = 39, ayah = 35, firstAyah = 36, secondAyah = 37),
          // 39:36 in madani map to 39:38 in kufi
          RangeOffsetOperator(sura = 39, startAyah = 36, endAyah = 36, offset = 2),
          // 39:37 madani is 39:39-39:40 kufi
          SplitOperator(sura = 39, ayah = 37, firstAyah = 39, secondAyah = 40),
          // 39:38-39:72 in madani map to 39:41-39:75 in kufi
          RangeOffsetOperator(sura = 39, startAyah = 38, endAyah = 72, offset = 3)
      ),
      40 to listOf(
          // 40:1 madani is 40:1-40:2 kufi
          SplitOperator(sura = 40, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 40:2-40:16 in madani map to 40:3-40:17 in kufi
          RangeOffsetOperator(sura = 40, startAyah = 2, endAyah = 16, offset = 1),
          // 40:17-40:18 in madani map to 40:18 in kufi
          JoinOperator(sura = 40, startAyah = 17, endAyah = 18, targetAyah = 18),
          // 40:19-40:52 no difference
          RangeOffsetOperator(sura = 40, startAyah = 19, endAyah = 52, offset = 0),
          // 40:53 madani is 40:53-40:54 kufi
          SplitOperator(sura = 40, ayah = 53, firstAyah = 53, secondAyah = 54),
          // 40:54-40:56 in madani map to 40:55-40:57 in kufi
          RangeOffsetOperator(sura = 40, startAyah = 54, endAyah = 56, offset = 1),
          // 40:57-40:58 in madani map to 40:58 in kufi
          JoinOperator(sura = 40, startAyah = 57, endAyah = 58, targetAyah = 58),
          // 40:59-40:72 no difference
          RangeOffsetOperator(sura = 40, startAyah = 59, endAyah = 72, offset = 0),
          // 40:73 madani is 40:73-40:74 kufi
          SplitOperator(sura = 40, ayah = 73, firstAyah = 73, secondAyah = 74),
          // 40:74-40:84 in madani map to 40:75-40:85 in kufi
          RangeOffsetOperator(sura = 40, startAyah = 74, endAyah = 84, offset = 1)
      ),
      41 to listOf(
          // 41:1 madani is 41:1-41:2 kufi
          SplitOperator(sura = 41, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 41:2-41:53 in madani map to 41:3-41:54 in kufi
          RangeOffsetOperator(sura = 41, startAyah = 2, endAyah = 53, offset = 1)
      ),
      42 to listOf(
          // 42:1 madani is 42:1-42:3 kufi
          SplitOperator(sura = 42, ayah = 1, firstAyah = 1, secondAyah = 2, thirdAyah = 3),
          // 42:2-42:29 in madani map to 42:4-42:31 in kufi
          RangeOffsetOperator(sura = 42, startAyah = 2, endAyah = 29, offset = 2),
          // 41:30 madani is 41:32-41:33 kufi
          SplitOperator(sura = 42, ayah = 30, firstAyah = 32, secondAyah = 33),
          // 41:31-41:50 in madani map to 41:34-41:53 in kufi
          RangeOffsetOperator(sura = 42, startAyah = 31, endAyah = 50, offset = 3)
      ),
      43 to listOf(
          // 43:1 madani is 43:1-43:2 kufi
          SplitOperator(sura = 43, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 43:2-43:50 in madani map to 43:3-43:51 in kufi
          RangeOffsetOperator(sura = 43, startAyah = 2, endAyah = 50, offset = 1),
          // 43:51-43:52 in madani map to 43:52 in kufi
          JoinOperator(sura = 43, startAyah = 51, endAyah = 52, targetAyah = 52),
          // 43:53-43:89 no difference
          RangeOffsetOperator(sura = 43, startAyah = 53, endAyah = 89, offset = 0)
      ),
      44 to listOf(
          // 44:1 madani is 44:1-44:2 kufi
          SplitOperator(sura = 44, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 44:2-44:32 in madani map to 44:3-44:33 in kufi
          RangeOffsetOperator(sura = 44, startAyah = 2, endAyah = 32, offset = 1),
          // 44:33 madani is 44:34-44:35 kufi
          SplitOperator(sura = 44, ayah = 33, firstAyah = 34, secondAyah = 35),
          // 44:34-44:40 in madani map to 44:36-44:42 in kufi
          RangeOffsetOperator(sura = 44, startAyah = 34, endAyah = 40, offset = 2),
          // 44:41 madani is 44:43-44:44 kufi
          SplitOperator(sura = 44, ayah = 41, firstAyah = 43, secondAyah = 44),
          // 44:42-44:56 in madani map to 44:45-44:59 in kufi
          RangeOffsetOperator(sura = 44, startAyah = 42, endAyah = 56, offset = 3)
      ),
      45 to listOf(
          // 45:1 madani is 45:1-45:2 kufi
          SplitOperator(sura = 45, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 45:2-45:36 in madani map to 45:3-45:37 in kufi
          RangeOffsetOperator(sura = 45, startAyah = 2, endAyah = 36, offset = 1)
      ),
      46 to listOf(
          // 46:1 madani is 46:1-46:2 kufi
          SplitOperator(sura = 46, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 46:2-46:34 in madani map to 46:3-46:35 in kufi
          RangeOffsetOperator(sura = 46, startAyah = 2, endAyah = 34, offset = 1)
      ),
      47 to listOf(
          // 47:1-47:3 no difference
          RangeOffsetOperator(sura = 47, startAyah = 1, endAyah = 3, offset = 0),
          // 47:4-47:5 in madani map to 47:4 in kufi
          JoinOperator(sura = 47, startAyah = 4, endAyah = 5, targetAyah = 4),
          // 47:6-47:39 in madani map to 47:5-47:38 in kufi
          RangeOffsetOperator(sura = 47, startAyah = 6, endAyah = 39, offset = -1)
      ),
      48 to listOf(
          // 48:1-48:29 no difference
          RangeOffsetOperator(sura = 48, startAyah = 1, endAyah = 29, offset = 0)
      ),
      49 to listOf(
          // 49:1-49:18 no difference
          RangeOffsetOperator(sura = 49, startAyah = 1, endAyah = 18, offset = 0)
      ),
      50 to listOf(
          // 50:1-50:45 no difference
          RangeOffsetOperator(sura = 50, startAyah = 1, endAyah = 45, offset = 0)
      ),
      51 to listOf(
          // 51:1-51:60 no difference
          RangeOffsetOperator(sura = 51, startAyah = 1, endAyah = 60, offset = 0)
      ),
      52 to listOf(
          // 52:1 madani is 52:1-52:2 kufi
          SplitOperator(sura = 52, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 52:2-52:11 in madani map to 52:3-52:12 in kufi
          RangeOffsetOperator(sura = 52, startAyah = 2, endAyah = 11, offset = 1),
          // 52:12 madani is 52:13-52:14 kufi
          SplitOperator(sura = 52, ayah = 12, firstAyah = 13, secondAyah = 14),
          // 52:13-52:47 in madani map to 52:15-52:49 in kufi
          RangeOffsetOperator(sura = 52, startAyah = 13, endAyah = 47, offset = 2)
      ),
      53 to listOf(
          // 53:1-53:27 no difference
          RangeOffsetOperator(sura = 53, startAyah = 1, endAyah = 27, offset = 0),
          // 53:28 madani is 53:28-53:29 kufi
          SplitOperator(sura = 53, ayah = 28, firstAyah = 28, secondAyah = 29),
          // 53:29-53:61 in madani map to 53:30-53:62 in kufi
          RangeOffsetOperator(sura = 53, startAyah = 29, endAyah = 61, offset = 1)
      ),
      54 to listOf(
          // 54:1-54:55 no difference
          RangeOffsetOperator(sura = 54, startAyah = 1, endAyah = 55, offset = 0)
      ),
      55 to listOf(
          // 55:1 madani is 55:1-55:2 kufi
          SplitOperator(sura = 55, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 55:2 madani is 55:3-55:4 kufi
          SplitOperator(sura = 55, ayah = 2, firstAyah = 3, secondAyah = 4),
          // 55:3-55:32 in madani map to 55:5-55:34 in kufi
          RangeOffsetOperator(sura = 55, startAyah = 3, endAyah = 32, offset = 2),
          // 55:33-55:34 in madani map to 55:35 in kufi
          JoinOperator(sura = 55, startAyah = 33, endAyah = 34, targetAyah = 35),
          // 55:35-55:77 in madani map to 55:36-55:78 in kufi
          RangeOffsetOperator(sura = 55, startAyah = 35, endAyah = 77, offset = 1)
      ),
      56 to listOf(
          // 56:1-56:7 no difference
          RangeOffsetOperator(sura = 56, startAyah = 1, endAyah = 7, offset = 0),
          // 56:8-56:9 in madani map to 56:8 in kufi
          JoinOperator(sura = 56, startAyah = 8, endAyah = 9, targetAyah = 8),
          // 56:10-56:11 in madani map to 56:9 in kufi
          JoinOperator(sura = 56, startAyah = 10, endAyah = 11, targetAyah = 9),
          // 56:12-56:19 in madani map to 56:10-56:17 in kufi
          RangeOffsetOperator(sura = 56, startAyah = 12, endAyah = 19, offset = -2),
          // 56:20-56:21 in madani map to 56:18 in kufi
          JoinOperator(sura = 56, startAyah = 20, endAyah = 21, targetAyah = 18),
          // 56:22-56:24 in madani map to 56:19-56:21 in kufi
          RangeOffsetOperator(sura = 56, startAyah = 22, endAyah = 24, offset = -3),
          // 56:25 madani is 56:22-56:23 kufi
          SplitOperator(sura = 56, ayah = 25, firstAyah = 22, secondAyah = 23),
          // 56:26-56:42 in madani map to 56:24-56:40 in kufi
          RangeOffsetOperator(sura = 56, startAyah = 26, endAyah = 42, offset = -2),
          // 56:43-56:44 in madani map to 56:41 in kufi
          JoinOperator(sura = 56, startAyah = 43, endAyah = 44, targetAyah = 41),
          // 56:45-56:99 in madani map to 56:42-56:96 in kufi
          RangeOffsetOperator(sura = 56, startAyah = 45, endAyah = 99, offset = -3)
      ),
      57 to listOf(
          // 57:1-57:12 no difference
          RangeOffsetOperator(sura = 57, startAyah = 1, endAyah = 12, offset = 0),
          // 57:13 madani is 57:13-57:14 kufi
          SplitOperator(sura = 57, ayah = 13, firstAyah = 13, secondAyah = 14),
          // 57:14-57:28 in madani map to 57:15-57:29 in kufi
          RangeOffsetOperator(sura = 57, startAyah = 14, endAyah = 28, offset = 1)
      ),
      58 to listOf(
          // 58:1-58:19 no difference
          RangeOffsetOperator(sura = 58, startAyah = 1, endAyah = 19, offset = 0),
          // 58:20 madani is 58:20-58:21 kufi
          SplitOperator(sura = 58, ayah = 20, firstAyah = 20, secondAyah = 21),
          // 58:21 in madani map to 58:22 in kufi
          RangeOffsetOperator(sura = 58, startAyah = 21, endAyah = 21, offset = 1)
      ),
      59 to listOf(
          // 59:1-59:24 no difference
          RangeOffsetOperator(sura = 59, startAyah = 1, endAyah = 24, offset = 0)
      ),
      60 to listOf(
          // 60:1-60:13 no difference
          RangeOffsetOperator(sura = 60, startAyah = 1, endAyah = 13, offset = 0)
      ),
      61 to listOf(
          // 61:1-61:14 no difference
          RangeOffsetOperator(sura = 61, startAyah = 1, endAyah = 14, offset = 0)
      ),
      62 to listOf(
          // 62:1-62:11 no difference
          RangeOffsetOperator(sura = 62, startAyah = 1, endAyah = 11, offset = 0)
      ),
      63 to listOf(
          // 63:1-63:11 no difference
          RangeOffsetOperator(sura = 63, startAyah = 1, endAyah = 11, offset = 0)
      ),
      64 to listOf(
          // 64:1-64:18 no difference
          RangeOffsetOperator(sura = 64, startAyah = 1, endAyah = 18, offset = 0)
      ),
      65 to listOf(
          // 65:1-65:12 no difference
          RangeOffsetOperator(sura = 65, startAyah = 1, endAyah = 12, offset = 0)
      ),
      66 to listOf(
          // 66:1-66:12 no difference
          RangeOffsetOperator(sura = 66, startAyah = 1, endAyah = 12, offset = 0)
      ),
      67 to listOf(
          // 67:1-67:8 no difference
          RangeOffsetOperator(sura = 67, startAyah = 1, endAyah = 8, offset = 0),
          // 67:9-67:10 in madani map to 67:9 in kufi
          JoinOperator(sura = 67, startAyah = 9, endAyah = 10, targetAyah = 9),
          // 67:11-67:31 in madani map to 67:10-67:30 in kufi
          RangeOffsetOperator(sura = 67, startAyah = 11, endAyah = 31, offset = -1)
      ),
      68 to listOf(
          // 68:1-68:52 no difference
          RangeOffsetOperator(sura = 68, startAyah = 1, endAyah = 52, offset = 0)
      ),
      69 to listOf(
          // 69:1 madani is 69:1-69:2 kufi
          SplitOperator(sura = 69, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 69:2-69:23 in madani map to 69:3-69:24 in kufi
          RangeOffsetOperator(sura = 69, startAyah = 2, endAyah = 23, offset = 1),
          // 69:24-69:25 in madani map to 69:25 in kufi
          JoinOperator(sura = 69, startAyah = 24, endAyah = 25, targetAyah = 25),
          // 69:26-69:52 no difference
          RangeOffsetOperator(sura = 69, startAyah = 26, endAyah = 52, offset = 0)
      ),
      70 to listOf(
          // 70:1-70:44 no difference
          RangeOffsetOperator(sura = 70, startAyah = 1, endAyah = 44, offset = 0)
      ),
      71 to listOf(
          // 71:1-71:22 no difference
          RangeOffsetOperator(sura = 71, startAyah = 1, endAyah = 22, offset = 0),
          // 71:23-71:24 in madani map to 71:23 in kufi
          JoinOperator(sura = 71, startAyah = 23, endAyah = 24, targetAyah = 23),
          // 71:25 in madani map to 71:24 in kufi
          RangeOffsetOperator(sura = 71, startAyah = 25, endAyah = 25, offset = -1),
          // 71:26-71:27 in madani map to 71:25 in kufi
          JoinOperator(sura = 71, startAyah = 26, endAyah = 27, targetAyah = 25),
          // 71:28-71:30 in madani map to 71:26-71:28 in kufi
          RangeOffsetOperator(sura = 71, startAyah = 28, endAyah = 30, offset = -2)
      ),
      72 to listOf(
          // 72:1-72:28 no difference
          RangeOffsetOperator(sura = 72, startAyah = 1, endAyah = 28, offset = 0)
      ),
      73 to listOf(
          // 73:1 madani is 73:1-73:2 kufi
          SplitOperator(sura = 73, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 73:2-73:15 in madani map to 73:3-73:16 in kufi
          RangeOffsetOperator(sura = 73, startAyah = 2, endAyah = 15, offset = 1),
          // 73:16 madani is 73:17-73:18 kufi
          SplitOperator(sura = 73, ayah = 16, firstAyah = 17, secondAyah = 18),
          // 73:17-73:18 in madani map to 73:19-73:20 in kufi
          RangeOffsetOperator(sura = 73, startAyah = 17, endAyah = 18, offset = 2)
      ),
      74 to listOf(
          // 74:1-74:39 no difference
          RangeOffsetOperator(sura = 74, startAyah = 1, endAyah = 39, offset = 0),
          // 74:40 madani is 74:40-74:41 kufi
          SplitOperator(sura = 74, ayah = 40, firstAyah = 40, secondAyah = 41),
          // 74:41-74:55 in madani map to 74:42-74:56 in kufi
          RangeOffsetOperator(sura = 74, startAyah = 41, endAyah = 55, offset = 1)
      ),
      75 to listOf(
          // 75:1-75:15 no difference
          RangeOffsetOperator(sura = 75, startAyah = 1, endAyah = 15, offset = 0),
          // 75:16 madani is 75:16-75:17 kufi
          SplitOperator(sura = 75, ayah = 16, firstAyah = 16, secondAyah = 17),
          // 75:17-75:39 in madani map to 75:18-75:40 in kufi
          RangeOffsetOperator(sura = 75, startAyah = 17, endAyah = 39, offset = 1)
      ),
      76 to listOf(
          // 76:1-76:31 no difference
          RangeOffsetOperator(sura = 76, startAyah = 1, endAyah = 31, offset = 0)
      ),
      77 to listOf(
          // 77:1-77:50 no difference
          RangeOffsetOperator(sura = 77, startAyah = 1, endAyah = 50, offset = 0)
      ),
      78 to listOf(
          // 78:1-78:40 no difference
          RangeOffsetOperator(sura = 78, startAyah = 1, endAyah = 40, offset = 0)
      ),
      79 to listOf(
          // 79:1-79:36 no difference
          RangeOffsetOperator(sura = 79, startAyah = 1, endAyah = 36, offset = 0),
          // 79:37 madani is 79:37-79:38 kufi
          SplitOperator(sura = 79, ayah = 37, firstAyah = 37, secondAyah = 38),
          // 79:38-79:45 in madani map to 79:39-79:46 in kufi
          RangeOffsetOperator(sura = 79, startAyah = 38, endAyah = 45, offset = 1)
      ),
      80 to listOf(
          // 80:1-80:42 no difference
          RangeOffsetOperator(sura = 80, startAyah = 1, endAyah = 42, offset = 0)
      ),
      81 to listOf(
          // 81:1-81:29 no difference
          RangeOffsetOperator(sura = 81, startAyah = 1, endAyah = 29, offset = 0)
      ),
      82 to listOf(
          // 82:1-82:19 no difference
          RangeOffsetOperator(sura = 82, startAyah = 1, endAyah = 19, offset = 0)
      ),
      83 to listOf(
          // 83:1-83:36 no difference
          RangeOffsetOperator(sura = 83, startAyah = 1, endAyah = 36, offset = 0)
      ),
      84 to listOf(
          // 84:1-84:25 no difference
          RangeOffsetOperator(sura = 84, startAyah = 1, endAyah = 25, offset = 0)
      ),
      85 to listOf(
          // 85:1-85:22 no difference
          RangeOffsetOperator(sura = 85, startAyah = 1, endAyah = 22, offset = 0)
      ),
      86 to listOf(
          // 86:1-86:17 no difference
          RangeOffsetOperator(sura = 86, startAyah = 1, endAyah = 17, offset = 0)
      ),
      87 to listOf(
          // 87:1-87:19 no difference
          RangeOffsetOperator(sura = 87, startAyah = 1, endAyah = 19, offset = 0)
      ),
      88 to listOf(
          // 88:1-88:26 no difference
          RangeOffsetOperator(sura = 88, startAyah = 1, endAyah = 26, offset = 0)
      ),
      89 to listOf(
          // 89:1-89:14 no difference
          RangeOffsetOperator(sura = 89, startAyah = 1, endAyah = 14, offset = 0),
          // 89:15-89:16 in madani map to 89:15 in kufi
          JoinOperator(sura = 89, startAyah = 15, endAyah = 16, targetAyah = 15),
          // 89:17-89:18 in madani map to 89:16 in kufi
          JoinOperator(sura = 89, startAyah = 17, endAyah = 18, targetAyah = 16),
          // 89:19-89:32 in madani map to 89:17-89:30 in kufi
          RangeOffsetOperator(sura = 89, startAyah = 19, endAyah = 32, offset = -2)
      ),
      90 to listOf(
          // 90:1-90:20 no difference
          RangeOffsetOperator(sura = 90, startAyah = 1, endAyah = 20, offset = 0)
      ),
      91 to listOf(
          // 91:1-91:15 no difference
          RangeOffsetOperator(sura = 91, startAyah = 1, endAyah = 15, offset = 0)
      ),
      92 to listOf(
          // 92:1-92:21 no difference
          RangeOffsetOperator(sura = 92, startAyah = 1, endAyah = 21, offset = 0)
      ),
      93 to listOf(
          // 93:1-93:11 no difference
          RangeOffsetOperator(sura = 93, startAyah = 1, endAyah = 11, offset = 0)
      ),
      94 to listOf(
          // 94:1-94:8 no difference
          RangeOffsetOperator(sura = 94, startAyah = 1, endAyah = 8, offset = 0)
      ),
      95 to listOf(
          // 95:1-95:8 no difference
          RangeOffsetOperator(sura = 95, startAyah = 1, endAyah = 8, offset = 0)
      ),
      96 to listOf(
          // 96:1-96:14 no difference
          RangeOffsetOperator(sura = 96, startAyah = 1, endAyah = 14, offset = 0),
          // 96:15-96:16 in madani map to 96:15 in kufi
          JoinOperator(sura = 96, startAyah = 15, endAyah = 16, targetAyah = 15),
          // 96:17-96:20 in madani map to 96:16-96:19 in kufi
          RangeOffsetOperator(sura = 96, startAyah = 17, endAyah = 20, offset = -1)
      ),
      97 to listOf(
          // 97:1-97:5 no difference
          RangeOffsetOperator(sura = 97, startAyah = 1, endAyah = 5, offset = 0)
      ),
      98 to listOf(
          // 98:1-98:8 no difference
          RangeOffsetOperator(sura = 98, startAyah = 1, endAyah = 8, offset = 0)
      ),
      99 to listOf(
          // 99:1-99:5 no difference
          RangeOffsetOperator(sura = 99, startAyah = 1, endAyah = 5, offset = 0),
          // 99:6-99:7 in madani map to 99:6 in kufi
          JoinOperator(sura = 99, startAyah = 6, endAyah = 7, targetAyah = 6),
          // 99:8-99:9 in madani map to 99:7-99:8 in kufi
          RangeOffsetOperator(sura = 99, startAyah = 8, endAyah = 9, offset = -1)
      ),
      100 to listOf(
          // 100:1-100:11 no difference
          RangeOffsetOperator(sura = 100, startAyah = 1, endAyah = 11, offset = 0)
      ),
      101 to listOf(
          // 101:1 madani is 101:1-101:2 kufi
          SplitOperator(sura = 101, ayah = 1, firstAyah = 1, secondAyah = 2),
          // 101:2-101:10 in madani map to 101:3-101:11 in kufi
          RangeOffsetOperator(sura = 101, startAyah = 2, endAyah = 10, offset = 1)
      ),
      102 to listOf(
          // 102:1-102:8 no difference
          RangeOffsetOperator(sura = 102, startAyah = 1, endAyah = 8, offset = 0)
      ),
      103 to listOf(
          // 103:1-103:3 no difference
          RangeOffsetOperator(sura = 103, startAyah = 1, endAyah = 3, offset = 0)
      ),
      104 to listOf(
          // 104:1-104:9 no difference
          RangeOffsetOperator(sura = 104, startAyah = 1, endAyah = 9, offset = 0)
      ),
      105 to listOf(
          // 105:1-105:5 no difference
          RangeOffsetOperator(sura = 105, startAyah = 1, endAyah = 5, offset = 0)
      ),
      106 to listOf(
          // 106:1-106:3 no difference
          RangeOffsetOperator(sura = 106, startAyah = 1, endAyah = 3, offset = 0),
          // 106:4-106:5 in madani map to 106:4 in kufi
          JoinOperator(sura = 106, startAyah = 4, endAyah = 5, targetAyah = 4)
      ),
      107 to listOf(
          // 107:1-107:5 no difference
          RangeOffsetOperator(sura = 107, startAyah = 1, endAyah = 5, offset = 0),
          // 107:6 madani is 107:6-107:7 kufi
          SplitOperator(sura = 107, ayah = 6, firstAyah = 6, secondAyah = 7)
      ),
      108 to listOf(
          // 108:1-108:3 no difference
          RangeOffsetOperator(sura = 108, startAyah = 1, endAyah = 3, offset = 0)
      ),
      109 to listOf(
          // 109:1-109:6 no difference
          RangeOffsetOperator(sura = 109, startAyah = 1, endAyah = 6, offset = 0)
      ),
      110 to listOf(
          // 110:1-110:3 no difference
          RangeOffsetOperator(sura = 110, startAyah = 1, endAyah = 3, offset = 0)
      ),
      111 to listOf(
          // 111:1-111:5 no difference
          RangeOffsetOperator(sura = 111, startAyah = 1, endAyah = 5, offset = 0)
      ),
      112 to listOf(
          // 112:1-112:4 no difference
          RangeOffsetOperator(sura = 112, startAyah = 1, endAyah = 4, offset = 0)
      ),
      113 to listOf(
          // 113:1-113:5 no difference
          RangeOffsetOperator(sura = 113, startAyah = 1, endAyah = 5, offset = 0)
      ),
      114 to listOf(
          // 114:1-114:6 no difference
          RangeOffsetOperator(sura = 114, startAyah = 1, endAyah = 6, offset = 0)
      )
  )
}
