#!/usr/bin/env python3
"""Derive per-ayah glyph types for the madani page images.

The madani ayahinfo databases store one bounding box per glyph but no glyph type. The memorize
feature needs to know which glyphs are words (to hide/reveal them one by one) and which are waqf
marks, sajdah/hizb markers or the ayah-end marker.

Rules (validated against every ayah):
  * the last glyph of an ayah is the ayah-end marker,
  * an ayah whose text starts with ۞ starts with a hizb marker glyph,
  * an ayah whose text contains ۩ has a sajdah marker glyph just before the end marker,
  * of the remaining glyphs, the number of words is known from word_alignment.db (the same
    glyph-word numbering the app already uses); the leftovers are waqf marks, which are always the
    shortest glyphs (they float above the line; some are wide, e.g. the ج in 38:26).

Output: one line per ayah, "sura ayah types", where types has one character per glyph position:
  w = word, p = pause (waqf mark), s = sajdah marker, h = hizb marker, e = ayah end.

Usage:
  python3 -I tools/memorize/build_glyph_types.py --ayahinfo ayahinfo_1024.db \
      --arabic quran.ar.uthmani.v2.db --word-alignment app/src/madani/assets/word_alignment.db \
      --out glyph_types_madani_v1.txt [--verify ayahinfo_1260.db ...]
"""
import argparse
import collections
import sqlite3
import sys


def glyph_widths(ayahinfo_path):
    con = sqlite3.connect(ayahinfo_path)
    rows = collections.defaultdict(list)
    query = ("select sura_number, ayah_number, position, max_y - min_y, max_x - min_x from glyphs "
             "order by sura_number, ayah_number, position")
    for sura, ayah, position, height, width in con.execute(query):
        rows[(sura, ayah)].append((position, height, width))
    return rows


def glyph_word_counts(word_alignment_path):
    con = sqlite3.connect(word_alignment_path)
    counts = {}
    for sura, ayah, numbers in con.execute("select sura, ayah, glyph_word_numbers from word_alignment"):
        highest = max(int(n) for n in numbers.split(","))
        counts[(sura, ayah)] = max(counts.get((sura, ayah), 0), highest)
    return counts


def ayah_texts(arabic_path):
    con = sqlite3.connect(arabic_path)
    return {(s, a): t for s, a, t in con.execute("select sura, ayah, text from arabic_text")}


def derive_types(sizes, text, word_count):
    """Returns the type string for one ayah, or raises ValueError if the rules don't fit.

    sizes holds (height, width) per glyph position.
    """
    count = len(sizes)
    types = [None] * count
    types[-1] = "e"
    start = 0
    if text.startswith("۞"):
        types[0] = "h"
        start = 1
    end = count - 1
    if "۩" in text:
        types[end - 1] = "s"
        end -= 1
    body = list(range(start, end))
    pauses = len(body) - word_count
    if pauses < 0:
        raise ValueError(f"{len(body)} glyphs but {word_count} words")
    smallest = set(sorted(body, key=lambda i: (sizes[i][0], sizes[i][1], i))[:pauses])
    for i in body:
        types[i] = "p" if i in smallest else "w"
    return "".join(types)


def build(ayahinfo, texts, word_counts):
    rows = glyph_widths(ayahinfo)
    result = {}
    for key in sorted(texts):
        positions = rows.get(key)
        if not positions:
            raise ValueError(f"{key}: no glyphs")
        if [p for p, _, _ in positions] != list(range(1, len(positions) + 1)):
            raise ValueError(f"{key}: glyph positions are not contiguous")
        result[key] = derive_types([(h, w) for _, h, w in positions], texts[key], word_counts[key])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ayahinfo", required=True)
    parser.add_argument("--arabic", required=True)
    parser.add_argument("--word-alignment", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--verify", nargs="*", default=[],
                        help="other ayahinfo widths that must yield identical types")
    args = parser.parse_args()

    texts = ayah_texts(args.arabic)
    word_counts = glyph_word_counts(args.word_alignment)
    types = build(args.ayahinfo, texts, word_counts)

    pause_wider_than_word = [k for k, t in types.items() if "p" in t and t.count("w") != word_counts[k]]
    if pause_wider_than_word:
        sys.exit(f"word count mismatch in {pause_wider_than_word[:5]}")

    for other in args.verify:
        other_types = build(other, texts, word_counts)
        diffs = [k for k in types if types[k] != other_types[k]]
        print(f"{other}: {len(diffs)} ayahs differ {diffs[:10]}")
        if diffs:
            sys.exit(1)

    with open(args.out, "w", encoding="utf-8") as out:
        for (sura, ayah), t in sorted(types.items()):
            out.write(f"{sura} {ayah} {t}\n")
    totals = collections.Counter("".join(types.values()))
    print(f"wrote {len(types)} ayahs: {dict(totals)}")


if __name__ == "__main__":
    main()
