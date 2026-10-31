import surasJson from './suras.json';
import arabicJson from './text.ar.json';
import englishJson from './text.en.json';
import urduJson from './text.ur.json';
import translitJson from './text.translit.json';
import mushafJson from './mushaf.json';

export type Sura = {
  n: number;
  ar: string;
  tr: string;
  en: string;
  ur: string;
  type: 'meccan' | 'medinan';
  count: number;
};

export type TranslationId = 'en' | 'ur' | 'none';

export type Ref = { sura: number; ayah: number };

export const SURAS = surasJson as Sura[];
const ARABIC = arabicJson as string[][];
const TRANSLATIONS: Record<'en' | 'ur', string[][]> = {
  en: englishJson as string[][],
  ur: urduJson as string[][],
};
const TRANSLIT = translitJson as string[][];
const PAGE_STARTS = mushafJson.pageStarts as [number, number][];
const QUARTERS = mushafJson.quarters as [number, number][];

export const TOTAL_PAGES = PAGE_STARTS.length;

export const TRANSLATION_LABELS: Record<TranslationId, string> = {
  en: 'English — Saheeh International',
  ur: 'اردو — Urdu',
  none: 'Arabic only',
};

export function sura(n: number): Sura {
  return SURAS[n - 1];
}

export function arabic(s: number, a: number): string {
  return ARABIC[s - 1]?.[a - 1] ?? '';
}

export function translation(id: TranslationId, s: number, a: number): string {
  if (id === 'none') return '';
  return TRANSLATIONS[id][s - 1]?.[a - 1] ?? '';
}

export function transliteration(s: number, a: number): string {
  return TRANSLIT[s - 1]?.[a - 1] ?? '';
}

export const refKey = (r: Ref) => `${r.sura}:${r.ayah}`;

export function isValidRef(r: Ref): boolean {
  return r.sura >= 1 && r.sura <= 114 && r.ayah >= 1 && r.ayah <= sura(r.sura).count;
}

const cmp = (a: Ref, s: number, y: number) => (a.sura - s) * 1000 + (a.ayah - y);

/** Madani mushaf page (1-604) that an ayah is printed on. */
export function pageOf(r: Ref): number {
  let lo = 0;
  let hi = PAGE_STARTS.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    const [s, a] = PAGE_STARTS[mid];
    if (cmp(r, s, a) >= 0) lo = mid;
    else hi = mid - 1;
  }
  return lo + 1;
}

/** First ayah printed on a mushaf page. */
export function pageStart(page: number): Ref {
  const [s, a] = PAGE_STARTS[Math.min(Math.max(page, 1), TOTAL_PAGES) - 1];
  return { sura: s, ayah: a };
}

export type Juz = { n: number; start: Ref; page: number };

export const JUZ: Juz[] = Array.from({ length: 30 }, (_, i) => {
  const [s, a] = QUARTERS[i * 8];
  const start = { sura: s, ayah: a };
  return { n: i + 1, start, page: pageOf(start) };
});

export function juzOf(r: Ref): number {
  let j = 1;
  for (const juz of JUZ) {
    if (cmp(r, juz.start.sura, juz.start.ayah) >= 0) j = juz.n;
    else break;
  }
  return j;
}

/** Fraction (0-1) of the Quran's 6236 ayahs up to and including this one. */
export function progressOf(r: Ref): number {
  let before = 0;
  for (let s = 1; s < r.sura; s++) before += SURAS[s - 1].count;
  return (before + r.ayah) / 6236;
}

const ARABIC_DIGITS = '٠١٢٣٤٥٦٧٨٩';
export function arabicNumber(n: number): string {
  return String(n).replace(/\d/g, (d) => ARABIC_DIGITS[Number(d)]);
}

/** Parses "2:255", "2 255", "2.255" or just "36". */
export function parseRef(q: string): Ref | null {
  const m = q.trim().match(/^(\d{1,3})(?:\s*[:.\s]\s*(\d{1,3}))?$/);
  if (!m) return null;
  const r = { sura: Number(m[1]), ayah: m[2] ? Number(m[2]) : 1 };
  return isValidRef(r) ? r : null;
}

export function formatRef(r: Ref): string {
  return `${sura(r.sura).tr} ${r.sura}:${r.ayah}`;
}

const strip = (t: string) =>
  t
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ًͯ-ٰٟۖ-ۭ]/g, '')
    .replace(/[^\p{L}\p{N}\s]/gu, ' ');

export function findSuras(q: string): Sura[] {
  const needle = strip(q).replace(/\s+/g, '');
  if (!needle) return SURAS;
  return SURAS.filter((s) => {
    const hay = strip(`${s.n} ${s.tr} ${s.en} ${s.ar} ${s.ur}`).replace(/\s+/g, '');
    return hay.includes(needle);
  });
}

export type VerseHit = Ref & { text: string };

/** Searches the chosen translation (or English) plus Arabic text. */
export function searchVerses(q: string, id: TranslationId, limit = 60): VerseHit[] {
  const needle = strip(q).trim();
  if (needle.length < 3) return [];
  const lang = id === 'none' ? 'en' : id;
  const hits: VerseHit[] = [];
  const table = TRANSLATIONS[lang];
  for (let s = 0; s < 114 && hits.length < limit; s++) {
    for (let a = 0; a < table[s].length && hits.length < limit; a++) {
      const t = table[s][a];
      if (strip(t).includes(needle) || strip(ARABIC[s][a]).includes(needle)) {
        hits.push({ sura: s + 1, ayah: a + 1, text: t });
      }
    }
  }
  return hits;
}

export const BISMILLAH = 'بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ';
