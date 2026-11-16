#!/usr/bin/env python3
"""Builds a word-timing pack by forced alignment of a qari's own audio files.

The streaming phoneme model (sherpa-onnx, zipformer2-CTC) is run over each sura file; its tokens
carry timestamps (positions in the audio). The token sequence is aligned (edit distance) with the
expected phoneme sequence of the sura. When quran.com (QDC) segments exist for the recitation, the
expected sequence follows their order, which includes the reciter going back to repeat words
(waqf/ibtida); otherwise it's the plain mushaf order. Each word keeps its last occurrence, the one
that continues forward, which is what a "next word" hint should play.

A word's start is its first token's time (minus a small lead), and its end is the next word's start
when the recitation continues, or its last token plus a tail before a pause.

Output schema: see build_timing_pack.py (word numbering = glyph words).

Usage (in the ML environment: needs sherpa-onnx and numpy):
  python -I tools/memorize/align_timing_pack.py --audio-dir DIR --model-dir MODEL --lexicon lexicon_v1.db \
      --voice husary_murattal --out husary_murattal_wbw.db [--qdc-reciter 6] [--suras 1 2 ...]
"""
import argparse
import hashlib
import json
import os
import sqlite3
import subprocess
import sys
import time
import urllib.request

import numpy as np
import sherpa_onnx

# quran-transcript phonetic script -> letter level (same folding as the app's PhonemeNormalizer)
SPECIAL = {"ڇ": "", "ۜ": "", "ـ": "", "ں": "ن", "۾": "م", "ٲ": "ء", "ؙ": "ُ", "۪": "َ"}
LONG = {"ا": "ا", "ۥ": "و", "ۦ": "ي"}
VOWELS = set("َُِ")
QDC_URL = "https://api.qurancdn.com/api/qdc/audio/reciters/{reciter}/audio_files?chapter={sura}&segments=true"

LEAD_MS = 120        # audio kept before a word's first token
TAIL_MS = 260        # audio kept after a word's last token when a pause follows
MAX_GAP_MS = 700     # a gap longer than this between words counts as a pause


def normalize(text):
    out = []
    for ch in text:
        if ch.isspace():
            continue
        ch = SPECIAL.get(ch, ch)
        if not ch:
            continue
        ch = LONG.get(ch, ch)
        if ch in VOWELS:
            continue  # consonant skeleton + long vowels: robust to vowel recognition errors
        if out and out[-1] == ch:
            continue
        out.append(ch)
    return "".join(out)


def load_audio(path):
    cmd = ["ffmpeg", "-nostdin", "-v", "error", "-i", path, "-ac", "1", "-ar", "16000", "-f", "f32le", "-"]
    return np.frombuffer(subprocess.run(cmd, check=True, capture_output=True).stdout, dtype=np.float32)


def recognize(model_dir, audio):
    rec = sherpa_onnx.OnlineRecognizer.from_zipformer2_ctc(
        tokens=os.path.join(model_dir, "tokens.txt"), model=os.path.join(model_dir, "quran-stt-int8.onnx"),
        num_threads=4, enable_endpoint_detection=False)
    s = rec.create_stream()
    step = 16000
    for i in range(0, len(audio), step):
        s.accept_waveform(16000, audio[i:i + step])
        while rec.is_ready(s):
            rec.decode_stream(s)
    s.accept_waveform(16000, np.zeros(16000, dtype=np.float32))
    s.input_finished()
    while rec.is_ready(s):
        rec.decode_stream(s)
    r = rec.get_result_all(s)
    symbols = []  # (symbol, ms)
    for tok, ts in zip(r.tokens, r.timestamps):
        for ch in normalize(tok):
            if not symbols or symbols[-1][0] != ch:
                symbols.append((ch, int(ts * 1000)))
    return symbols


def expected_sequence(con, sura, qdc):
    """[(ayah, word, phonemes)] in recited order."""
    words = {(a, w): normalize(p) for a, w, p in
             con.execute("select ayah, word, wasl from words where sura=? order by ayah, word", (sura,))}
    if not qdc:
        return [(a, w, p) for (a, w), p in sorted(words.items())]
    seq = []
    for v in qdc["audio_files"][0]["verse_timings"]:
        s, a = (int(x) for x in v["verse_key"].split(":"))
        segments = [seg for seg in (v.get("segments") or []) if len(seg) >= 3]
        order = [int(seg[0]) for seg in segments] or sorted(w for (aa, w) in words if aa == a)
        for w in order:
            if (a, w) in words:
                seq.append((a, w, words[(a, w)]))
        # words QDC has no segment for still have to be in the sequence
        present = set(order)
        for (aa, w) in sorted(words):
            if aa == a and w not in present:
                seq.append((a, w, words[(aa, w)]))
    return seq


def align(symbols, seq):
    """Global alignment of heard symbols with the expected sequence; returns, per sequence item,
    the (first, last) indexes of heard symbols aligned with it, or None."""
    expected = []
    owner = []
    for k, (_, _, ph) in enumerate(seq):
        for ch in ph:
            expected.append(ch)
            owner.append(k)
    heard = [c for c, _ in symbols]
    n, m = len(heard), len(expected)
    # banded DP would be faster; files are at most a few hours so a full DP in numpy rows is fine
    INF = 1 << 30
    prev = np.arange(m + 1, dtype=np.int32)
    back = np.zeros((n + 1, m + 1), dtype=np.uint8)  # 0 diag, 1 up (extra heard), 2 left (missing)
    back[0, 1:] = 2
    exp_arr = np.array([ord(c) for c in expected], dtype=np.int32)
    for i in range(1, n + 1):
        cur = np.empty(m + 1, dtype=np.int32)
        cur[0] = i
        back[i, 0] = 1
        sub = prev[:-1] + (exp_arr != ord(heard[i - 1])).astype(np.int32)
        up = prev[1:] + 1
        best = np.minimum(sub, up)
        choice = np.where(sub <= up, 0, 1).astype(np.uint8)
        # left moves need a sequential pass
        for j in range(1, m + 1):
            v = best[j - 1]
            c = choice[j - 1]
            left = cur[j - 1] + 1
            if left < v:
                v, c = left, 2
            cur[j] = v
            back[i, j] = c
        prev = cur
    spans = [None] * len(seq)
    i, j = n, m
    while i > 0 and j > 0:
        c = back[i, j]
        if c == 0:
            k = owner[j - 1]
            f, l = spans[k] if spans[k] else (i - 1, i - 1)
            spans[k] = (min(f, i - 1), max(l, i - 1))
            i, j = i - 1, j - 1
        elif c == 1:
            i -= 1
        else:
            j -= 1
    return spans


def fetch_qdc(reciter, sura, cache_dir):
    path = os.path.join(cache_dir, str(reciter), f"{sura}.json")
    if os.path.exists(path):
        return json.load(open(path, encoding="utf-8"))
    req = urllib.request.Request(QDC_URL.format(reciter=reciter, sura=sura), headers={"User-Agent": "quran-android-tools"})
    data = json.load(urllib.request.urlopen(req, timeout=60))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(data, open(path, "w", encoding="utf-8"))
    return data


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--audio-dir", required=True)
    ap.add_argument("--model-dir", required=True)
    ap.add_argument("--lexicon", required=True)
    ap.add_argument("--voice", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--qdc-reciter", type=int)
    ap.add_argument("--cache-dir", default=os.path.expanduser("~/quran-asr-lab/cache/qdc"))
    ap.add_argument("--suras", nargs="*", type=int, default=list(range(1, 115)))
    a = ap.parse_args()

    con = sqlite3.connect(a.lexicon)
    out_exists = os.path.exists(a.out)
    db = sqlite3.connect(a.out)
    if not out_exists:
        db.execute("create table properties(property text primary key, value text)")
        db.execute("create table timings(sura integer not null, ayah integer not null, time integer not null, "
                   "words text not null, primary key(sura, ayah))")
        db.execute("create table audio_files(sura integer primary key, size integer not null, md5 text not null)")
        db.executemany("insert into properties values (?, ?)", [
            ("version", "1"), ("schema_version", "2"), ("wbw", "1"), ("word_numbering", "glyph"),
            ("voice", a.voice), ("source", "forced alignment of the qari's own audio"),
        ])
    for sura in a.suras:
        path = os.path.join(a.audio_dir, f"{sura:03d}.mp3")
        if not os.path.exists(path):
            print(f"sura {sura}: no audio, skipped")
            continue
        t0 = time.time()
        audio = load_audio(path)
        symbols = recognize(a.model_dir, audio)
        qdc = fetch_qdc(a.qdc_reciter, sura, a.cache_dir) if a.qdc_reciter else None
        seq = expected_sequence(con, sura, qdc)
        spans = align(symbols, seq)

        # last occurrence of every word
        last = {}
        for k, (ayah, word, _) in enumerate(seq):
            if spans[k] is not None:
                last[(ayah, word)] = k
        starts = {k: symbols[spans[k][0]][1] for k in range(len(seq)) if spans[k] is not None}
        rows = {}
        missing = 0
        for (ayah, word) in sorted({(x[0], x[1]) for x in seq}):
            k = last.get((ayah, word))
            if k is None:
                missing += 1
                continue
            first_ms = symbols[spans[k][0]][1]
            last_ms = symbols[spans[k][1]][1]
            nxt = next((starts[j] for j in range(k + 1, len(seq)) if j in starts), None)
            if nxt is not None and nxt - last_ms <= MAX_GAP_MS:
                end = nxt - LEAD_MS // 2
            else:
                end = last_ms + TAIL_MS
            start = max(0, first_ms - LEAD_MS)
            rows.setdefault(ayah, []).append(f"{word}:{start}:{max(end, start + 150)}")
        for ayah, words in rows.items():
            first = int(words[0].split(":")[1])
            db.execute("insert or replace into timings values (?, ?, ?, ?)", (sura, ayah, first, ",".join(words)))
        digest = hashlib.md5(open(path, "rb").read()).hexdigest()
        db.execute("insert or replace into audio_files values (?, ?, ?)", (sura, os.path.getsize(path), digest))
        db.commit()
        print(f"sura {sura}: {len(seq)} recited words, {missing} unaligned, {time.time() - t0:.0f}s", flush=True)


if __name__ == "__main__":
    sys.exit(main())
