#!/usr/bin/env python3
"""Regenerate offline KMP Juz metadata from the existing canonical XML; never write Quran text."""
from pathlib import Path
from xml.etree import ElementTree
import hashlib

root = Path(__file__).resolve().parents[1]
source = root / "core/data/content/quran-data.xml"
entries = ElementTree.parse(source).getroot().find("juzs")
if entries is None or len(entries) != 30:
    raise ValueError("Canonical XML must contain exactly 30 juz starts")
starts = [(int(entry.attrib["index"]), int(entry.attrib["sura"]), int(entry.attrib["aya"])) for entry in entries]
if [number for number, _, _ in starts] != list(range(1, 31)):
    raise ValueError("Canonical juz indices must be ordered 1 through 30")
checksum = hashlib.sha256(source.read_bytes()).hexdigest()
output = root / "core/data/src/commonMain/kotlin/org/quran/app/data/CanonicalJuzMetadata.kt"
header = """package org.quran.app.data

import org.quran.app.model.Juz
import org.quran.app.model.VerseId

/**
 * Generated from unchanged core/data/content/quran-data.xml by tools/generate-juz-metadata.py.
 * Source SHA-256: %s
 * Runtime metadata is synchronous and offline on every KMP target.
 */
internal object CanonicalJuzMetadata {
    private val entries = listOf(
""" % checksum
body = "".join(f"        Juz({number}, VerseId({surah}, {ayah})),\n" for number, surah, ayah in starts)
footer = """    )

    fun load(): List<Juz> = entries.toList()
}
"""
output.write_text(header + body + footer)
