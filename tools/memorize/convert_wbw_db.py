#!/usr/bin/env python3
"""Converts a gapless qari timing database that already has word-by-word timings (schema v2,
`words` = "audioWord:start:end,..." in audio/imlaei word numbering) into a memorize timing pack
(glyph word numbering, see build_timing_pack.py), using the app's word_alignment.db.

The sizes of the sura files the timings belong to are needed so the app only plays a file that
matches; give either --audio-dir (local copies) or --sizes (a "sura size" list, e.g. from HTTP
Content-Length).

Usage:
  python3 -I tools/memorize/convert_wbw_db.py --db husary_muallim.db \
      --word-alignment app/src/madani/assets/word_alignment.db --glyph-types glyph_types_madani_v1.txt \
      --voice husary_muallim --sizes sizes.txt --out husary_muallim_wbw.db
"""
import argparse
import os
import sqlite3
import sys


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--db", required=True)
    ap.add_argument("--word-alignment", required=True)
    ap.add_argument("--glyph-types", required=True)
    ap.add_argument("--voice", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--sizes")
    ap.add_argument("--audio-dir")
    a = ap.parse_args()

    glyph_words = {}
    with open(a.glyph_types, encoding="utf-8") as f:
        for line in f:
            s, ay, t = line.split()
            glyph_words[(int(s), int(ay))] = t.count("w")
    mapping = {}
    for s, ay, audio_word, glyphs in sqlite3.connect(a.word_alignment).execute(
            "select sura, ayah, audio_word_number, glyph_word_numbers from word_alignment"):
        mapping[(s, ay, audio_word)] = [int(g) for g in glyphs.split(",")]

    src = sqlite3.connect(a.db)
    rows = []
    incomplete = 0
    for sura, ayah, time, words in src.execute("select sura, ayah, time, words from timings where ayah > 0 and ayah < 999"):
        spans = {}
        for part in filter(None, (words or "").split(",")):
            n, start, end = (int(x) for x in part.split(":"))
            for g in mapping.get((sura, ayah, n), []):
                old = spans.get(g)
                spans[g] = (min(start, old[0]), max(end, old[1])) if old else (start, end)
        if len(spans) != glyph_words.get((sura, ayah), -1):
            incomplete += 1
        rows.append((sura, ayah, time, ",".join(f"{g}:{s}:{e}" for g, (s, e) in sorted(spans.items()))))

    sizes = {}
    if a.sizes:
        for line in open(a.sizes):
            s, size = line.split()
            sizes[int(s)] = int(size)
    elif a.audio_dir:
        for s in range(1, 115):
            path = os.path.join(a.audio_dir, f"{s:03d}.mp3")
            if os.path.exists(path):
                sizes[s] = os.path.getsize(path)

    if os.path.exists(a.out):
        os.remove(a.out)
    out = sqlite3.connect(a.out)
    out.execute("create table properties(property text primary key, value text)")
    out.execute("create table timings(sura integer not null, ayah integer not null, time integer not null, "
                "words text not null, primary key(sura, ayah))")
    out.execute("create table audio_files(sura integer primary key, size integer not null, md5 text not null)")
    out.executemany("insert into properties values (?, ?)", [
        ("version", "1"), ("schema_version", "2"), ("wbw", "1"), ("word_numbering", "glyph"),
        ("voice", a.voice), ("source", f"converted from {os.path.basename(a.db)}"),
    ])
    out.executemany("insert into timings values (?, ?, ?, ?)", rows)
    out.executemany("insert into audio_files values (?, ?, '')", sorted(sizes.items()))
    out.commit()
    out.execute("vacuum")
    print(f"{len(rows)} ayahs ({incomplete} without a timing for every glyph word), {len(sizes)} sura files -> {a.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
