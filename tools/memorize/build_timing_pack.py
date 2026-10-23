#!/usr/bin/env python3
"""Build a word-timing pack for a gapless qari, used by the prayer corrector to play single words.

Word segments come from the quran.com (QDC) audio API. They are timed against QDC's own copy of the
recording, which can be trimmed/resampled differently from the file the app downloads. For each sura
we therefore fit a linear map  app_ms = a + b * qdc_ms  using the ayah start times that both sides
know (the qari's ayah timing database vs. QDC's verse timestamps), reject outliers, and report the
residuals so a bad fit is visible instead of silently shipping wrong timings.

Output (SQLite):
  properties(property, value)      version, voice, source, word_numbering=glyph
  timings(sura, ayah, time, words) time = ayah start in ms; words = "word:start:end,..." where word is
                                   the 1-based glyph word number on the madani page (WordGlyph.wordPosition)
  audio_files(sura, size, md5)     the exact files the timings were fitted for (optional, --audio-dir)

Usage:
  python3 -I tools/memorize/build_timing_pack.py --qdc-reciter 6 --ayah-db husary.db \
      --glyph-types glyph_types_madani_v1.txt --voice husary_murattal --out husary_murattal_wbw.db \
      [--audio-dir <dir with 001.mp3..114.mp3>] [--cache-dir <dir>] [--identity]

--identity skips the fit (for qaris whose audio *is* QDC's audio, e.g. Husary Muallim).
"""
import argparse
import hashlib
import json
import os
import sqlite3
import statistics
import sys
import time
import urllib.request

QDC_URL = "https://api.qurancdn.com/api/qdc/audio/reciters/{reciter}/audio_files?chapter={sura}&segments=true"


def fetch_json(url, cache_path):
    if cache_path and os.path.exists(cache_path):
        with open(cache_path, encoding="utf-8") as f:
            return json.load(f)
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "quran-android-tools"})
            data = json.load(urllib.request.urlopen(req, timeout=60))
            break
        except OSError:
            if attempt == 4:
                raise
            time.sleep(2 ** attempt)
    if cache_path:
        os.makedirs(os.path.dirname(cache_path), exist_ok=True)
        with open(cache_path, "w", encoding="utf-8") as f:
            json.dump(data, f)
    return data


def glyph_word_counts(path):
    counts = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            sura, ayah, types = line.split()
            counts[(int(sura), int(ayah))] = types.count("w")
    return counts


def ayah_starts(ayah_db):
    con = sqlite3.connect(ayah_db)
    starts = {}
    for sura, ayah, ms in con.execute("select sura, ayah, time from timings"):
        starts[(sura, ayah)] = ms
    return starts


def fit_linear(pairs):
    """Least squares fit y = a + b x with one round of outlier rejection. Returns a, b, residuals."""
    def solve(points):
        n = len(points)
        if n == 1:
            x, y = points[0]
            return y - x, 1.0
        mx = sum(p[0] for p in points) / n
        my = sum(p[1] for p in points) / n
        sxx = sum((p[0] - mx) ** 2 for p in points)
        if sxx == 0:
            return my - mx, 1.0
        b = sum((p[0] - mx) * (p[1] - my) for p in points) / sxx
        return my - b * mx, b

    a, b = solve(pairs)
    residuals = [y - (a + b * x) for x, y in pairs]
    if len(pairs) > 4:
        cutoff = max(300.0, 3 * statistics.pstdev(residuals))
        kept = [p for p, r in zip(pairs, residuals) if abs(r) <= cutoff]
        if len(kept) >= 2:
            a, b = solve(kept)
            residuals = [y - (a + b * x) for x, y in pairs]
    return a, b, residuals


def md5_of(path):
    digest = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--qdc-reciter", type=int, required=True)
    parser.add_argument("--ayah-db", help="qari ayah timing db (timings(sura, ayah, time)); required unless --identity")
    parser.add_argument("--glyph-types", required=True)
    parser.add_argument("--voice", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--audio-dir")
    parser.add_argument("--cache-dir", default=os.path.expanduser("~/quran-asr-lab/cache/qdc"))
    parser.add_argument("--identity", action="store_true")
    parser.add_argument("--anchor", choices=["sura", "ayah"], default="ayah",
                        help="ayah: re-anchor every ayah on the qari's own ayah start (default)")
    parser.add_argument("--max-residual-ms", type=float, default=250.0)
    args = parser.parse_args()
    if not args.identity and not args.ayah_db:
        parser.error("--ayah-db is required unless --identity")

    word_counts = glyph_word_counts(args.glyph_types)
    starts = ayah_starts(args.ayah_db) if args.ayah_db else {}

    rows = []
    report = []
    problems = []
    for sura in range(1, 115):
        url = QDC_URL.format(reciter=args.qdc_reciter, sura=sura)
        cache = os.path.join(args.cache_dir, str(args.qdc_reciter), f"{sura}.json")
        data = fetch_json(url, cache)
        verses = data["audio_files"][0]["verse_timings"]

        if args.identity:
            a, b, residuals = 0.0, 1.0, [0.0]
        else:
            pairs = []
            for v in verses:
                s, ay = (int(x) for x in v["verse_key"].split(":"))
                if (s, ay) in starts:
                    pairs.append((float(v["timestamp_from"]), float(starts[(s, ay)])))
            a, b, residuals = fit_linear(pairs)
        worst = max(abs(r) for r in residuals)
        report.append((sura, a, b, statistics.median(abs(r) for r in residuals), worst))
        if worst > args.max_residual_ms:
            bad = sorted(range(len(residuals)), key=lambda i: -abs(residuals[i]))[:3]
            problems.append(f"sura {sura}: worst ayah-start residual {worst:.0f} ms "
                            f"(ayah indexes {bad}, scale {b:.5f}, offset {a:.0f})")

        for v in verses:
            s, ay = (int(x) for x in v["verse_key"].split(":"))
            segments = [seg for seg in (v.get("segments") or []) if len(seg) >= 3]
            expected = word_counts[(s, ay)]
            # Reciters stop and restart a few words back (waqf/ibtida), so a word can have several
            # segments. Keep the last one: it belongs to the run that continues to the ayah's end,
            # which is what a prompt for "the next word" should play.
            last = {}
            for seg in segments:
                word = int(seg[0])
                if 1 <= word <= expected:
                    last[word] = seg
            missing = [w for w in range(1, expected + 1) if w not in last]
            if missing:
                problems.append(f"{s}:{ay}: no segment for glyph words {missing[:5]} of {expected}")
            if not args.identity and args.anchor == "ayah" and (s, ay) in starts:
                # the qari's own ayah start, with QDC's timing of the words relative to the ayah
                offset = starts[(s, ay)] - b * float(v["timestamp_from"])
            else:
                offset = a
            words = []
            for word in sorted(last):
                seg = last[word]
                start = round(offset + b * float(seg[1]))
                end = round(offset + b * float(seg[2]))
                words.append(f"{word}:{start}:{max(end, start)}")
            ayah_start = round(offset + b * float(v["timestamp_from"]))
            rows.append((s, ay, starts.get((s, ay), ayah_start) if not args.identity else ayah_start,
                         ",".join(words)))

    if os.path.exists(args.out):
        os.remove(args.out)
    con = sqlite3.connect(args.out)
    con.execute("create table properties(property text primary key, value text)")
    con.execute("create table timings(sura integer not null, ayah integer not null, time integer not null, "
                "words text not null, primary key(sura, ayah))")
    con.execute("create table audio_files(sura integer primary key, size integer not null, md5 text not null)")
    con.executemany("insert into properties values (?, ?)", [
        ("version", "1"), ("schema_version", "2"), ("wbw", "1"), ("word_numbering", "glyph"),
        ("voice", args.voice), ("source", f"qdc reciter {args.qdc_reciter}" + ("" if args.identity else " (refitted)")),
    ])
    con.executemany("insert into timings values (?, ?, ?, ?)", rows)
    if args.audio_dir:
        for sura in range(1, 115):
            path = os.path.join(args.audio_dir, f"{sura:03d}.mp3")
            if os.path.exists(path):
                con.execute("insert into audio_files values (?, ?, ?)", (sura, os.path.getsize(path), md5_of(path)))
    con.commit()

    medians = [r[3] for r in report]
    worsts = [r[4] for r in report]
    scales = [r[2] for r in report]
    print(f"{len(rows)} ayahs written to {args.out}")
    print(f"fit: median |residual| per sura {statistics.median(medians):.0f} ms, worst {max(worsts):.0f} ms, "
          f"scale range {min(scales):.5f}..{max(scales):.5f}")
    for line in problems[:40]:
        print("WARN", line)
    if len(problems) > 40:
        print(f"... {len(problems) - 40} more warnings")
    return 0 if not problems else 2


if __name__ == "__main__":
    sys.exit(main())
