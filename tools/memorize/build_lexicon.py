#!/usr/bin/env python3
"""Build the phoneme lexicon used by the memorize tracker.

For every word of the Quran (Hafs, glyph-word numbering of the madani pages) the lexicon stores the
word's phonemes in the quran-transcript phonetic script (the alphabet the speech model emits):
  * wasl: as pronounced inside its ayah (assimilation with neighbours, hamzat al-wasl dropped),
  * iso:  as pronounced alone (what a reciter says when starting or stopping on this word).
It also stores the phrases a person says during salah that are not part of the passage being
recited (Al-Fatiha is tracked from the words table itself), so the tracker can ignore them.

Requires `quran-transcript` (pip install quran-transcript; MIT).

Usage:
  python -I tools/memorize/build_lexicon.py --glyph-types glyph_types_madani_v1.txt --out lexicon_v1.db
"""
import argparse
import os
import sqlite3
import sys

from quran_transcript import Aya, MoshafAttributes, quran_phonetizer

# Said during salah, never part of the passage. Written in Uthmani-style orthography so the
# phonetizer treats them like Quranic text.
SALAH_PHRASES = {
    "takbir": ["ٱللَّهُ أَكْبَرُ"],
    "tasmi": ["سَمِعَ ٱللَّهُ لِمَنْ حَمِدَهُۥ"],
    "tahmid": ["رَبَّنَا وَلَكَ ٱلْحَمْدُ", "رَبَّنَا لَكَ ٱلْحَمْدُ", "ٱللَّهُمَّ رَبَّنَا لَكَ ٱلْحَمْدُ"],
    "ruku": ["سُبْحَـٰنَ رَبِّىَ ٱلْعَظِيمِ"],
    "sujud": ["سُبْحَـٰنَ رَبِّىَ ٱلْأَعْلَىٰ"],
    "ameen": ["ءَامِينَ"],
    "isti'adha": ["أَعُوذُ بِٱللَّهِ مِنَ ٱلشَّيْطَـٰنِ ٱلرَّجِيمِ"],
    "basmala": ["بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"],
    "istiftah": ["سُبْحَـٰنَكَ ٱللَّهُمَّ وَبِحَمْدِكَ وَتَبَارَكَ ٱسْمُكَ وَتَعَـٰلَىٰ جَدُّكَ وَلَآ إِلَـٰهَ غَيْرُكَ"],
    "tasleem": ["ٱلسَّلَـٰمُ عَلَيْكُمْ وَرَحْمَةُ ٱللَّهِ"],
}


SHADDA = "\u0651"
HARAKAT = set("\u064b\u064c\u064d\u064e\u064f\u0650")


def canonical_marks(text):
    """Puts shadda before the vowel mark on the same letter, the order the Tanzil text (and therefore
    the phonetizer) uses."""
    chars = list(text)
    for i in range(len(chars) - 1):
        if chars[i] in HARAKAT and chars[i + 1] == SHADDA:
            chars[i], chars[i + 1] = chars[i + 1], chars[i]
    return "".join(chars)


def moshaf():
    return MoshafAttributes(rewaya="hafs", madd_monfasel_len=4, madd_mottasel_len=4,
                            madd_mottasel_waqf=4, madd_aared_len=4)


def word_spans(words, out):
    """Splits the phonetized ayah into per-word phoneme strings using the character mappings."""
    result = []
    pos = 0
    for word in words:
        starts, ends = [], []
        for _ in word:
            mapping = out.mappings[pos]
            if not mapping.deleted:
                starts.append(mapping.pos[0])
                ends.append(mapping.pos[1])
            pos += 1
        pos += 1  # the space between words
        result.append(out.phonemes[min(starts):max(ends)].strip() if starts else "")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--glyph-types", required=True, help="output of build_glyph_types.py")
    parser.add_argument("--out", required=True)
    parser.add_argument("--compact", action="store_true", help="omit the Uthmani text (app asset)")
    args = parser.parse_args()

    word_counts = {}
    with open(args.glyph_types, encoding="utf-8") as f:
        for line in f:
            sura, ayah, types = line.split()
            word_counts[(int(sura), int(ayah))] = types.count("w")

    m = moshaf()
    rows = []
    aya = Aya(1, 1)
    while True:
        info = aya.get()
        key = (info.sura_idx, info.aya_idx)
        words = info.uthmani_words
        if len(words) != word_counts[key]:
            sys.exit(f"{key}: {len(words)} words in quran-transcript, {word_counts[key]} glyph words")
        wasl = word_spans(words, quran_phonetizer(" ".join(words), m, sura_idx=info.sura_idx))
        for index, (word, in_context) in enumerate(zip(words, wasl), start=1):
            alone = quran_phonetizer(word, m, sura_idx=info.sura_idx).phonemes.strip()
            if not in_context:
                in_context = alone
            rows.append((info.sura_idx, info.aya_idx, index, word, in_context, alone))
        if key == (114, 6):
            break
        aya = aya.step(1)

    phrases = []
    for kind, texts in SALAH_PHRASES.items():
        for text in texts:
            text = canonical_marks(text)
            phrases.append((kind, text, quran_phonetizer(text, m).phonemes.strip()))

    if os.path.exists(args.out):
        os.remove(args.out)
    con = sqlite3.connect(args.out)
    con.execute("create table properties(property text primary key, value text)")
    con.execute("create table words(sura integer not null, ayah integer not null, word integer not null, "
                + ("" if args.compact else "uthmani text not null, ") +
                "wasl text not null, iso text not null, primary key(sura, ayah, word)) without rowid")
    con.execute("create table phrases(kind text not null, text text not null, phonemes text not null)")
    con.executemany("insert into properties values (?, ?)", [
        ("version", "1"), ("riwayah", "hafs"), ("word_numbering", "glyph"), ("alphabet", "quran-transcript"),
    ])
    if args.compact:
        rows = [(s, a, w, wasl, iso) for s, a, w, _, wasl, iso in rows]
        con.executemany("insert into words values (?, ?, ?, ?, ?)", rows)
    else:
        con.executemany("insert into words values (?, ?, ?, ?, ?, ?)", rows)
    con.executemany("insert into phrases values (?, ?, ?)", phrases)
    con.commit()
    con.execute("vacuum")
    print(f"{len(rows)} words, {len(phrases)} phrases -> {args.out} ({os.path.getsize(args.out) / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
