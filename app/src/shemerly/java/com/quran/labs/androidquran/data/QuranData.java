package com.quran.labs.androidquran.data;

public class QuranData {

  public static int[] SURA_NUM_AYAHS = BaseQuranData.SURA_NUM_AYAHS;
  public static boolean[] SURA_IS_MAKKI = BaseQuranData.SURA_IS_MAKKI;
  public static int[][] QUARTERS = BaseQuranData.QUARTERS;

  public static int[] SURA_PAGE_START = {
        1,   2,  41,  63,  86, 104, 122, 144, 152, 168, 180, 192, 204, 209,
      215, 220, 232, 242, 252, 259, 267, 275, 283, 290, 299, 305, 314, 322,
      331, 337, 343, 346, 349, 357, 363, 368, 373, 379, 384, 391, 399, 404,
      410, 416, 418, 422, 426, 429, 433, 436, 438, 441, 443, 446, 448, 451,
      454, 458, 461, 464, 467, 468, 470, 471, 473, 475, 477, 479, 481, 483,
      485, 487, 489, 490, 492, 494, 496, 497, 499, 500, 501, 502, 503, 504,
      505, 506, 507, 508, 509, 510, 511, 511, 512, 513, 513, 513, 514, 515,
      515, 516, 516, 517, 517, 518, 518, 519, 519, 519, 520, 520, 520, 521,
      521, 521
  };

  public static int[] PAGE_SURA_START = {
        1,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,
        2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,
        2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   2,   3,
        3,   3,   3,   3,   3,   3,   3,   3,   3,   3,   3,   3,   3,   3,
        3,   3,   3,   3,   3,   3,   3,   4,   4,   4,   4,   4,   4,   4,
        4,   4,   4,   4,   4,   4,   4,   4,   4,   4,   4,   4,   4,   4,
        4,   4,   5,   5,   5,   5,   5,   5,   5,   5,   5,   5,   5,   5,
        5,   5,   5,   5,   5,   6,   6,   6,   6,   6,   6,   6,   6,   6,
        6,   6,   6,   6,   6,   6,   6,   6,   6,   6,   7,   7,   7,   7,
        7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,   7,
        7,   7,   7,   8,   8,   8,   8,   8,   8,   8,   8,   8,   9,   9,
        9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,   9,
       10,  10,  10,  10,  10,  10,  10,  10,  10,  10,  10,  10,  11,  11,
       11,  11,  11,  11,  11,  11,  11,  11,  11,  11,  12,  12,  12,  12,
       12,  12,  12,  12,  12,  12,  12,  13,  13,  13,  13,  13,  13,  14,
       14,  14,  14,  14,  14,  15,  15,  15,  15,  16,  16,  16,  16,  16,
       16,  16,  16,  16,  16,  16,  16,  16,  17,  17,  17,  17,  17,  17,
       17,  17,  17,  17,  18,  18,  18,  18,  18,  18,  18,  18,  18,  18,
       19,  19,  19,  19,  19,  19,  19,  20,  20,  20,  20,  20,  20,  20,
       20,  21,  21,  21,  21,  21,  21,  21,  21,  22,  22,  22,  22,  22,
       22,  22,  22,  23,  23,  23,  23,  23,  23,  23,  24,  24,  24,  24,
       24,  24,  24,  24,  24,  25,  25,  25,  25,  25,  25,  26,  26,  26,
       26,  26,  26,  26,  26,  26,  27,  27,  27,  27,  27,  27,  27,  28,
       28,  28,  28,  28,  28,  28,  28,  28,  29,  29,  29,  29,  29,  29,
       29,  30,  30,  30,  30,  30,  31,  31,  31,  31,  32,  32,  33,  33,
       33,  33,  33,  33,  33,  33,  33,  34,  34,  34,  34,  34,  35,  35,
       35,  35,  35,  36,  36,  36,  36,  36,  37,  37,  37,  37,  37,  37,
       37,  38,  38,  38,  38,  38,  39,  39,  39,  39,  39,  39,  39,  40,
       40,  40,  40,  40,  40,  40,  40,  41,  41,  41,  41,  41,  42,  42,
       42,  42,  42,  42,  43,  43,  43,  43,  43,  44,  44,  44,  45,  45,
       45,  46,  46,  46,  46,  47,  47,  47,  47,  48,  48,  48,  48,  49,
       49,  49,  50,  50,  51,  51,  51,  52,  52,  53,  53,  53,  54,  54,
       55,  55,  55,  56,  56,  56,  57,  57,  57,  57,  58,  58,  58,  59,
       59,  59,  60,  60,  61,  61,  62,  63,  63,  64,  64,  65,  65,  66,
       67,  67,  67,  68,  68,  69,  69,  70,  70,  71,  71,  72,  73,  73,
       74,  74,  75,  76,  76,  77,  77,  78,  79,  79,  80,  81,  82,  83,
       84,  85,  86,  87,  89,  89,  91,  92,  94,  96,  98,  99, 101, 104,
      106, 109, 112
  };

  public static int[] PAGE_AYAH_START = {
        1,   1,   5,  16,  24,  30,  39,  50,  60,  65,  75,  83,  90,  98,
      105, 113, 122, 130, 139, 145, 154, 164, 173, 179, 187, 193, 199, 210,
      217, 221, 229, 233, 238, 247, 251, 257, 262, 267, 275, 282,   1,   7,
       14,  23,  31,  41,  50,  61,  72,  80,  89,  99, 109, 117, 126, 139,
      149, 154, 163, 172, 181, 189,   1,   4,  11,  16,  23,  27,  35,  43,
       51,  60,  69,  77,  84,  91,  95, 102, 111, 120, 129, 137, 145, 154,
      163,   1,   2,   5,  11,  16,  23,  32,  41,  46,  52,  60,  67,  75,
       83,  92,  99, 106, 113,   1,   9,  20,  31,  40,  51,  59,  70,  78,
       89,  94, 103, 113, 122, 130, 139, 145, 152,   1,   3,  18,  28,  37,
       43,  52,  60,  71,  81,  89, 101, 117, 131, 140, 146, 155, 160, 167,
      176, 186, 195,   1,  11,  21,  32,  41,  48,  59,  67,   1,   7,  17,
       24,  32,  38,  46,  55,  64,  71,  79,  87,  95, 103, 111, 118,   1,
        3,  12,  20,  26,  35,  45,  58,  68,  76,  88,  98,   1,   7,  17,
       26,  36,  45,  56,  65,  77,  87,  96, 109,   1,   6,  18,  26,  36,
       43,  52,  64,  73,  82,  93, 103,   1,   7,  16,  23,  31,   1,   4,
       10,  21,  28,  39,   1,  14,  32,  54,  77,   1,  12,  25,  34,  44,
       58,  68,  76,  85,  93, 104, 115,   1,   7,  17,  28,  40,  52,  61,
       71,  83,  96,   1,   6,  17,  24,  32,  44,  53,  62,  77,  88,   1,
        4,  20,  35,  49,  63,  76,   1,  17,  40,  58,  72,  86,  97, 114,
        1,   2,  17,  31,  44,  59,  75,  87,   1,   2,  11,  20,  30,  40,
       51,  62,   1,   5,  22,  34,  51,  71,  88,   1,   2,  12,  22,  31,
       35,  43,  53,  59,   1,   6,  18,  32,  45,  59,   1,  12,  34,  56,
       83, 112, 139, 165, 190,   1,   7,  18,  32,  42,  55,  65,  82,   1,
       10,  19,  29,  38,  47,  58,  70,  79,   1,  11,  22,  32,  42,  52,
        1,   8,  20,  29,  40,  50,   1,  14,  22,   1,   8,  20,   1,   9,
       19,  27,  35,  43,  51,  57,   1,   3,  13,  22,  33,  43,   1,   8,
       14,  28,  39,   1,  15,  34,  49,  67,   1,  22,  51,  81, 110, 141,
        1,   9,  24,  38,  59,   1,   5,  13,  23,  37,  46,  58,   1,   4,
       13,  25,  34,  45,  57,  67,   1,   6,  16,  28,  39,   1,   3,  13,
       20,  29,  42,   1,  12,  25,  39,  55,  71,   1,  20,   1,   5,  17,
       27,   1,  11,  18,  27,   1,  10,  18,   1,   2,  11,  18,   1,   2,
       11,   1,  14,   1,   7,  33,   1,  17,   1,   6,  30,   1,  14,   1,
        5,  33,   1,   9,  47,   1,   3,  12,  19,   1,   3,   9,   1,   2,
       10,   1,   1,   9,   1,   1,   3,   1,   1,   7,   1,   4,   1,   6,
        1,  12,   1,  16,   1,   6,   1,   4,   1,   7,   1,  13,   1,   1,
       13,   1,  12,   1,  19,   1,   1,  14,   1,   1,   1,   1,   1,   1,
        1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,
        1,   1,   1
  };


  public static int[] JUZ_PAGE_START = {
        1,  19,  35,  67,  83,  98, 115, 131, 147, 163,
      180, 198, 215, 232, 249, 267, 283, 301, 319, 335,
      344, 352, 369, 387, 404, 422, 439, 458, 477, 497
  };

  public static int[] PAGE_RUB3_START = {
       -1,  -1,  -1,  -1,   1,  -1,   2,  -1,   3,  -1,   4,  -1,   5,  -1,
        6,  -1,   7,  -1,   8,  -1,   9,  -1,  10,  -1,  11,  -1,  12,  -1,
       13,  -1,  14,  -1,  15,  -1,  16,  -1,  17,  18,  -1,  19,  -1,  -1,
       20,  -1,  21,  -1,  22,  -1,  23,  -1,  24,  -1,  25,  -1,  26,  -1,
       27,  -1,  28,  -1,  29,  -1,  30,  -1,  31,  -1,  32,  -1,  33,  -1,
       34,  -1,  35,  -1,  36,  -1,  37,  -1,  38,  -1,  39,  -1,  40,  41,
       -1,  42,  -1,  -1,  43,  -1,  44,  45,  -1,  46,  -1,  47,  -1,  48,
       -1,  49,  -1,  50,  -1,  -1,  51,  -1,  52,  -1,  53,  -1,  54,  -1,
       -1,  55,  56,  -1,  57,  -1,  58,  59,  -1,  60,  -1,  -1,  61,  -1,
       62,  -1,  63,  -1,  64,  -1,  -1,  65,  -1,  66,  -1,  67,  -1,  68,
       -1,  69,  -1,  70,  -1,  71,  72,  -1,  -1,  73,  -1,  74,  -1,  75,
       -1,  76,  77,  -1,  78,  -1,  79,  -1,  80,  -1,  81,  -1,  82,  -1,
       83,  -1,  84,  -1,  -1,  85,  -1,  86,  -1,  87,  -1,  88,  -1,  89,
       -1,  90,  -1,  91,  -1,  92,  -1,  93,  -1,  -1,  94,  -1,  95,  -1,
       -1,  96,  -1,  97,  -1,  98,  -1,  99,  -1, 100,  -1, 101,  -1, 102,
       -1, 103,  -1,  -1, 104,  -1, 105,  -1,  -1, 106,  -1, 107,  -1, 108,
       -1, 109,  -1, 110,  -1, 111,  -1, 112,  -1, 113,  -1, 114,  -1, 115,
       -1,  -1, 116,  -1, 117,  -1,  -1, 118, 119,  -1, 120,  -1, 121,  -1,
       -1, 122,  -1, 123,  -1,  -1, 124,  -1, 125,  -1, 126,  -1, 127,  -1,
      128,  -1, 129,  -1, 130,  -1, 131,  -1, 132,  -1, 133,  -1, 134,  -1,
      135,  -1, 136,  -1,  -1, 137,  -1, 138,  -1, 139,  -1, 140,  -1, 141,
       -1, 142,  -1,  -1, 143,  -1, 144,  -1, 145,  -1, 146,  -1, 147,  -1,
      148,  -1,  -1, 149,  -1, 150,  -1, 151,  -1,  -1, 152, 153,  -1,  -1,
      154, 155,  -1,  -1, 156,  -1, 157,  -1, 158,  -1, 159,  -1, 160,  -1,
      161,  -1,  -1, 162,  -1, 163,  -1, 164,  -1,  -1, 165,  -1, 166, 167,
       -1, 168,  -1, 169,  -1, 170,  -1, 171,  -1, 172,  -1, 173,  -1,  -1,
      174,  -1, 175,  -1, 176,  -1, 177,  -1, 178,  -1,  -1, 179,  -1, 180,
       -1, 181,  -1, 182,  -1,  -1, 183,  -1, 184,  -1, 185,  -1, 186,  -1,
      187,  -1, 188,  -1, 189,  -1,  -1, 190, 191,  -1, 192,  -1, 193,  -1,
      194,  -1, 195,  -1, 196,  -1,  -1, 197,  -1, 198,  -1,  -1, 199,  -1,
       -1, 200,  -1, 201,  -1, 202,  -1,  -1, 203,  -1, 204,  -1, 205,  -1,
      206,  -1, 207,  -1, 208,  -1,  -1, 209,  -1, 210,  -1, 211,  -1, 212,
       -1,  -1, 213,  -1,  -1, 214,  -1, 215,  -1, 216,  -1, 217,  -1,  -1,
      218,  -1, 219,  -1,  -1, 220,  -1, 221,  -1,  -1, 222,  -1, 223,  -1,
      224,  -1, 225,  -1, 226,  -1,  -1, 227,  -1,  -1, 228,  -1,  -1, 229,
       -1, 230,  -1,  -1, 231,  -1, 232,  -1,  -1, 233,  -1, 234,  -1, 235,
       -1,  -1, 236,  -1,  -1, 237,  -1,  -1, 238,  -1,  -1, 239,  -1,  -1,
       -1,  -1,  -1
  };
}
