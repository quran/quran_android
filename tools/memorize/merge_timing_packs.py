#!/usr/bin/env python3
"""Merges timing packs of one voice made in parallel (align_timing_pack.py --suras ...) into a new,
complete pack for the app, and checks that it covers the whole Quran.

Some upstream sura files stop early (e.g. husary_muallim/004.mp3 on download.quranicaudio.com ends
in 4:34, while the recording continues to 4:176): --truncated SURA:LAST_AYAH keeps that sura's file
(so its size still matches what users download) but drops the ayat after LAST_AYAH, whose
alignment is meaningless. Hints for them are then shown without audio.

Usage:
  python -I tools/memorize/merge_timing_packs.py --voice husary_mujawwad --out husary_mujawwad_wbw_v1.db \
      [--truncated 4:33] part0.db part1.db ...
"""
import argparse
import os
import sqlite3
import sys

SURA_AYAHS = [
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78,
    118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38,
    29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20,
    56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
    11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
]
AYAH_COUNT = sum(SURA_AYAHS)
SURA_COUNT = len(SURA_AYAHS)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--voice", required=True)
    ap.add_argument("--source", default="forced alignment of the qari's own audio")
    ap.add_argument("--out", required=True)
    ap.add_argument("--truncated", action="append", default=[], metavar="SURA:LAST_AYAH")
    ap.add_argument("parts", nargs="+")
    a = ap.parse_args()

    if os.path.exists(a.out):
        sys.exit(f"{a.out} exists; refusing to overwrite it")
    db = sqlite3.connect(a.out)
    db.execute("create table properties(property text primary key, value text)")
    db.execute("create table timings(sura integer not null, ayah integer not null, time integer not null, "
               "words text not null, primary key(sura, ayah))")
    db.execute("create table audio_files(sura integer primary key, size integer not null, md5 text not null)")
    db.executemany("insert into properties values (?, ?)", [
        ("version", "1"), ("schema_version", "2"), ("wbw", "1"), ("word_numbering", "glyph"),
        ("voice", a.voice), ("source", a.source),
    ])
    for part in a.parts:
        voice = sqlite3.connect(part).execute("select value from properties where property = 'voice'").fetchone()
        if not voice or voice[0] != a.voice:
            sys.exit(f"{part} is for voice {voice[0] if voice else None}, not {a.voice}")
        db.execute("attach database ? as part", (part,))
        db.execute("insert or replace into timings select sura, ayah, time, words from part.timings")
        db.execute("insert or replace into audio_files select sura, size, md5 from part.audio_files")
        db.commit()
        db.execute("detach database part")

    expected = list(SURA_AYAHS)
    for spec in a.truncated:
        sura, last = (int(x) for x in spec.split(":"))
        dropped = db.execute("delete from timings where sura = ? and ayah > ?", (sura, last)).rowcount
        expected[sura - 1] = last
        print(f"sura {sura}: dropped {dropped} ayat after {last} (truncated upstream file)")
    have = dict(db.execute("select sura, count(*) from timings group by sura"))
    missing = [f"{sura}: {have.get(sura, 0)}/{n}" for sura, n in enumerate(expected, 1) if have.get(sura, 0) != n]
    ayahs = db.execute("select count(*) from timings").fetchone()[0]
    suras = db.execute("select count(*) from audio_files").fetchone()[0]
    empty = db.execute("select count(*) from timings where words = ''").fetchone()[0]
    db.commit()
    db.execute("vacuum")
    db.close()
    print(f"{a.out}: {ayahs}/{AYAH_COUNT} ayat, {suras}/{SURA_COUNT} sura files, {empty} ayat without words")
    if missing or suras != SURA_COUNT:
        sys.exit("incomplete: " + ", ".join(missing))


if __name__ == "__main__":
    main()
