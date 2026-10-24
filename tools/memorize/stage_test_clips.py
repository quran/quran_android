#!/usr/bin/env python3
"""Stages the audio of the on-device memorize tests into build/memorize-testlab/ (not in git): clips of
different professional reciters and styles, non-professional readers and non-native readers, and a
manifest saying where each clip starts in the Quran, how many words it covers, when each word ends
(when known) and what a session should make of it.

  professional  QUD-Technologies/quran-alignment-benchmark (CC-BY-4.0), ~35 s segments with word times,
                plus Husary (the app's own sura files)
  reader        sobolev210/quran-recitation-errors (MIT): non-professional readers, incl. real
                wording mistakes
  non_native    RetaSy/quranic_audio_dataset (crowdsourced, non-Arabic speakers): correct recitations
                of different countries, genders and ages, a different ayah, an unfinished ayah

The audio stays local (and in private test runs); it is never committed.

Needs numpy, soundfile and pyarrow, and ffmpeg on PATH:
  python tools/memorize/stage_test_clips.py --data ~/quran-asr-lab/data \
      --husary-audio ~/quran-asr-lab/audio/husary_murattal --text-lexicon ~/quran-asr-lab/cache/lexicon_v1.db
"""
import argparse
import glob
import io
import json
import os
import re
import sqlite3
import subprocess

import numpy as np
import pyarrow.parquet as pq
import soundfile as sf

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
OUT = os.path.join(ROOT, "build", "memorize-testlab")
ASSETS = os.path.join(ROOT, "feature", "memorize", "src", "main", "assets", "memorize")
SR = 16_000

# QUD recordings: (id, label, seconds into the recording to look for an ayah start)
QUD = [
    ("afasy-baqarah", "Mishary Al-Afasy, murattal", 600),
    ("abdulbasit-raad", "Abdul Basit, mujawwad", 120),
    ("badri-juz-tabarak", "Abdul Qadir Badri, fast hadr", 60),
    ("jaber-inshiqaq", "Ali Jaber, murattal", 0),
    ("ayyub-juz-amma", "Muhammad Ayyub, murattal", 300),
    ("mandour-taha", "Mandour, live recording with background noise", 120),
    ("luhaidan-fatir", "Muhammad Al-Luhaidan, live, crushed and reverberant", 120),
    ("minshawi-luqman", "Minshawi teaching class, children repeat each ayah", 60),
]
RETASY_SURAS = {"Al-Faatihah": 1, "Al-Ikhlas": 112, "An-Nas": 114, "Ayat al-Kursi": 2, "Al-Kafiroon": 109,
                "Al-Falaq": 113, "Al-Kauthar": 108, "Al-Asr": 103, "Al-Masad": 111, "Al-Humazah": 104,
                "Quraish": 106, "Al-Feel": 105, "Al-Maun": 107, "An-Nasr": 110, "At-Takathur": 102,
                "Al-Qariah": 101}

DIACRITICS = re.compile(r"[ؐ-ًؚ-ٰٟۖ-ۭـ࣓-ࣿ]")
UNIFY = str.maketrans({"أ": "ء", "إ": "ء", "ؤ": "ء", "ئ": "ء", "آ": "ءا", "ٱ": "ا", "ى": "ي", "ة": "ه",
                       "ٰ": "ا", "ۥ": "و", "ۦ": "ي"})


def skeleton(text):
    """Letters only, spelling variants unified (Uthmani and standard spelling compare equal)."""
    text = DIACRITICS.sub("", text.translate(UNIFY))
    return re.sub(r"(.)\1+", r"\1", re.sub(r"[^ء-ي]", "", text))


def decode(data=None, path=None, start=0.0, duration=None):
    # -nostdin would also stop ffmpeg reading audio given on stdin (it then outputs nothing)
    cmd = ["ffmpeg", "-v", "error"] + ([] if data is not None else ["-nostdin"])
    if start:
        cmd += ["-ss", f"{start:.3f}"]
    if duration:
        cmd += ["-t", f"{duration:.3f}"]
    cmd += ["-i", path or "pipe:0", "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"]
    out = subprocess.run(cmd, input=data, check=True, capture_output=True).stdout
    if not out:
        raise ValueError(f"no audio decoded from {path or 'the given bytes'}")
    return np.frombuffer(out, dtype=np.int16)


def write(name, pcm):
    path = os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, pcm, SR, subtype="PCM_16")
    return name


class Lexicon:
    def __init__(self, text_db):
        con = sqlite3.connect(os.path.join(ASSETS, "lexicon_v1.db"))
        self.count = dict(((s, a), n) for s, a, n in
                          con.execute("select sura, ayah, count(*) from words group by sura, ayah"))
        # the app bundles a compact lexicon without the text; the full one has it
        text = sqlite3.connect(text_db)
        self.text = {(s, a): skeleton(t) for s, a, t in
                     text.execute("select sura, ayah, group_concat(uthmani, ' ') from words group by sura, ayah")}

    def find(self, sura, text):
        """The ayah of `sura` whose text the clip's text matches."""
        sk = skeleton(text)
        for (s, a), t in self.text.items():
            if s == sura and (t == sk or (len(sk) >= 8 and sk in t)):
                return a
        return None


def qud_clips(data, lex):
    path = glob.glob(os.path.join(data, "QUD-Technologies__quran-alignment-benchmark", "**", "*.parquet"),
                     recursive=True)[0]
    clips = []
    for row in pq.read_table(path, columns=["id", "audio", "truth"]).to_pylist():
        spec = next((q for q in QUD if q[0] == row["id"]), None)
        if spec is None:
            continue
        _, label, offset = spec
        words = [(tuple(int(x) for x in w["word"].split(":")), w["start_s"], w["end_s"])
                 for w in row["truth"]["words"] if re.fullmatch(r"\d+:\d+:\d+", w["word"])]
        # the first ayah start after the offset; then the words in mushaf order for ~35 s, ending
        # with an ayah (repeats in between, e.g. a class repeating the teacher, are part of it)
        start = next(i for i, (k, s, _) in enumerate(words) if k[2] == 1 and s >= offset and k[0] != 1)
        (sura, ayah, _), t0, _ = words[start]
        # ~35 s, longer when a single (mujawwad) ayah takes more than that
        for window in (35, 50, 70, 90):
            first_end, expected = {}, [(sura, ayah, 1)]
            for k, s, e in words[start:]:
                first_end.setdefault(k, e)
                if s - t0 > window:
                    break
            while True:
                s_, a_, w_ = expected[-1]
                nxt = (s_, a_, w_ + 1) if w_ < lex.count[(s_, a_)] else (s_, a_ + 1, 1)
                if nxt not in first_end:
                    break
                expected.append(nxt)
            # end with a complete ayah
            while expected and expected[-1][2] != lex.count[expected[-1][:2]]:
                expected.pop()
            if expected:
                break
        if not expected:
            print(f"{row['id']}: no complete ayah near {offset} s, skipped")
            continue
        end = max(first_end[k] for k in expected)
        begin = max(0.0, t0 - 0.3)
        pcm = decode(row["audio"]["bytes"], start=begin, duration=end + 0.6 - begin)
        clips.append({
            "file": write(f"clips/qud_{row['id']}.wav", pcm), "group": "professional", "label": label,
            "source": "QUD-Technologies/quran-alignment-benchmark (CC-BY-4.0)", "sura": sura, "ayah": ayah,
            "words": len(expected), "word_ends_ms": [round((first_end[k] - begin) * 1000) for k in expected],
            "expect": "complete",
        })
    return clips


def husary_clips(husary_audio, lex):
    timings = sqlite3.connect(os.path.join(ASSETS, "timings", "husary_murattal_wbw_v1.db"))
    # Al-Mulk 1-5, from just before 67:1 to just after 67:5 (67:6 starts 1.4 s later)
    begin, end = 8.699, 126.0
    ends = []
    for ayah in range(1, 6):
        (words,) = timings.execute("select words from timings where sura=67 and ayah=?", (ayah,)).fetchone()
        ends += [int(w.split(":")[2]) - round(begin * 1000) for w in words.split(",")]
    write("mulk_1_5_husary.wav", decode(path=os.path.join(husary_audio, "067.mp3"), start=begin, duration=end - begin))
    # the speech engine benchmark: the first 30 s of Al-Fatiha (isti'adha, basmala, 1:2...)
    write("fatiha_husary_30s.wav", decode(path=os.path.join(husary_audio, "001.mp3"), duration=30.0))
    # continuous recitation into the next suras (as in taraweeh): Al-Ikhlas, Al-Falaq and An-Nas
    # one after the other, each sura file with its basmala
    pcm, ends_all, words = [], [], 0
    for sura in (112, 113, 114):
        offset = sum(len(p) for p in pcm) * 1000 // SR
        audio = decode(path=os.path.join(husary_audio, f"{sura:03d}.mp3"))
        for (words_text,) in timings.execute("select words from timings where sura=? order by ayah", (sura,)):
            ends_all += [int(w.split(":")[2]) + offset for w in words_text.split(",")]
        words += sum(n for (s, _), n in lex.count.items() if s == sura)
        pcm.append(audio)
    write("ikhlas_to_nas_husary.wav", np.concatenate(pcm))
    return [{
        "file": "mulk_1_5_husary.wav", "group": "professional", "label": "Mahmoud Khalil Al-Husary, murattal",
        "source": "Husary murattal sura files (the app's own audio)", "sura": 67, "ayah": 1,
        "words": sum(lex.count[(67, a)] for a in range(1, 6)), "word_ends_ms": ends, "expect": "complete",
    }, {
        "file": "ikhlas_to_nas_husary.wav", "group": "continuous", "label": "Al-Husary, Al-Ikhlas to An-Nas in a row",
        "source": "Husary murattal sura files (the app's own audio)", "sura": 112, "ayah": 1,
        "words": words, "word_ends_ms": ends_all, "expect": "complete",
    }]


def reader_clips(data, lex):
    base = os.path.join(data, "sobolev210__quran-recitation-errors")
    rows = [json.loads(l) for l in open(os.path.join(base, "metadata.jsonl"), encoding="utf-8")]
    rows = [r for r in rows if r["riwayah"] == "Hafs"]
    clips, seen = [], set()

    def ayah_of(r):
        return lex.find(int(r["surah"]), r["text"]) or int(r["ayah"])

    def add(r, expect, label):
        sura = int(r["surah"])
        ayah = ayah_of(r)
        pcm = decode(path=os.path.join(base, r["file_name"]))
        clips.append({
            "file": write(f"clips/reader_{len(clips) + 1}.wav", pcm), "group": "reader", "label": label,
            "source": "sobolev210/quran-recitation-errors (MIT)", "sura": sura, "ayah": ayah,
            "words": lex.count[(sura, ayah)], "word_ends_ms": None, "expect": expect,
        })

    correct = sorted((r for r in rows if not r["errors"] and 3 <= sf.info(os.path.join(base, r["file_name"])).duration <= 15),
                     key=lambda r: (r["surah"], r["recording_id"]))
    suras = set()
    for r in correct:
        if r["recording_id"] in seen or r["surah"] in suras or lex.count[(int(r["surah"]), ayah_of(r))] < 4:
            continue
        seen.add(r["recording_id"])
        suras.add(r["surah"])
        add(r, "complete", "non-professional reader, correct")
        if len(clips) == 4:
            break
    for r in rows:
        if any("Wording" in v for v in r["errors"].values()):
            add(r, "mistake", "non-professional reader, wrong word")
    return clips


def non_native_clips(data, lex):
    rows = []
    for f in sorted(glob.glob(os.path.join(data, "RetaSy__quranic_audio_dataset", "**", "*.parquet"), recursive=True)):
        rows += pq.read_table(f, columns=["audio", "Surah", "Aya", "final_label", "reciter_id", "reciter_country",
                                          "reciter_gender", "reciter_age", "reciter_qiraah", "duration_ms"]).to_pylist()
    # "Aya" is the text of the ayah the reciter was asked for
    rows = [r for r in rows if r["Surah"] in RETASY_SURAS and r["Aya"]
            and 2_000 <= (r["duration_ms"] or 0) <= 20_000 and r["reciter_qiraah"] in ("hafs", None)]
    clips = []

    def add(r, expect, what):
        sura = RETASY_SURAS[r["Surah"]]
        ayah = lex.find(sura, r["Aya"])
        if ayah is None:  # e.g. the isti'adha
            return False
        who = ", ".join(x for x in (r["reciter_country"], r["reciter_gender"], r["reciter_age"])
                        if x and x != "Unknown") or "unknown speaker"
        pcm = decode(r["audio"]["bytes"])
        clips.append({
            "file": write(f"clips/non_native_{len(clips) + 1}.wav", pcm), "group": "non_native",
            "label": f"non-native reader ({who}), {what}",
            "source": "RetaSy/quranic_audio_dataset (local use only)", "sura": sura, "ayah": ayah,
            "words": lex.count[(sura, ayah)], "word_ends_ms": None, "expect": expect,
        })
        return True

    # correct recitations: the youngest first, then as many countries and both genders as possible
    correct = [r for r in rows if r["final_label"] == "correct" and r["reciter_qiraah"] == "hafs"]
    correct.sort(key=lambda r: (r["reciter_age"] != "14-18", r["reciter_country"] or "", r["reciter_id"]))
    reciters, countries, genders = set(), {}, {}
    for r in correct:
        country, gender = r["reciter_country"], r["reciter_gender"]
        if r["reciter_id"] in reciters or countries.get(country, 0) >= 1 or genders.get(gender, 0) >= 4:
            continue
        if add(r, "complete", "correct"):
            reciters.add(r["reciter_id"])
            countries[country] = countries.get(country, 0) + 1
            genders[gender] = genders.get(gender, 0) + 1
        if len(clips) == 8:
            break
    for label, expect, what, n in (("not_match_aya", "mistake", "recited a different ayah", 2),
                                   ("in_complete", "incomplete", "stopped before the end", 1)):
        for r in [r for r in rows if r["final_label"] == label][:n]:
            add(r, expect, what)
    return clips


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", required=True, help="directory with the downloaded datasets")
    ap.add_argument("--husary-audio", required=True, help="directory with Husary murattal 001.mp3 ... 114.mp3")
    ap.add_argument("--text-lexicon", required=True,
                    help="lexicon built without --compact (tools/memorize/build_lexicon.py), for the Uthmani text")
    a = ap.parse_args()
    lex = Lexicon(a.text_lexicon)
    clips = husary_clips(a.husary_audio, lex) + qud_clips(a.data, lex) + reader_clips(a.data, lex) \
        + non_native_clips(a.data, lex)
    os.makedirs(os.path.join(OUT, "clips"), exist_ok=True)
    with open(os.path.join(OUT, "clips", "manifest.json"), "w", encoding="utf-8") as f:
        json.dump({"clips": clips}, f, ensure_ascii=False, indent=1)
    total = sum(sf.info(os.path.join(OUT, c["file"])).duration for c in clips)
    for c in clips:
        print(f"{c['group']:12s} {c['sura']:3d}:{c['ayah']:<3d} {c['words']:3d} words  {c['expect']:10s} {c['label']}")
    print(f"{len(clips)} clips, {total / 60:.1f} min of audio in {OUT}")


if __name__ == "__main__":
    main()
