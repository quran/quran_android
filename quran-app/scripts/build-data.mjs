// Generates the bundled Quran data in src/data from the `quran-json` package
// (text: quranenc.com, CC BY-SA 4.0) and the Madani page/quarter tables that
// live in the original Android project.
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const pkgDir = dirname(require.resolve('quran-json/package.json'));
const out = join(here, '..', 'src', 'data');

const load = (f) => JSON.parse(readFileSync(join(pkgDir, 'dist', f), 'utf8'));
const en = load('quran_en.json');
const ur = load('quran_ur.json');
const tr = load('quran_transliteration.json');

// --- Madani page + quarter tables from the Kotlin source ---
const kt = readFileSync(
  join(here, '..', '..', 'pages', 'data', 'madani', 'src', 'main', 'kotlin', 'com', 'quran',
    'labs', 'androidquran', 'pages', 'data', 'madani', 'MadaniDataSource.kt'),
  'utf8'
);
const intArray = (name) => {
  const m = kt.match(new RegExp(`${name}\\s*=\\s*intArrayOf\\(([^)]*)\\)`));
  if (!m) throw new Error(`missing ${name}`);
  return m[1].split(',').map((s) => s.trim()).filter(Boolean).map(Number);
};
const suraForPage = intArray('suraForPageArray');
const ayahForPage = intArray('ayahForPageArray');
if (suraForPage.length !== 604 || ayahForPage.length !== 604) throw new Error('bad page tables');

const quarterBlock = kt.split('quartersArray')[1].split('manzilPageArray')[0];
const quarters = [...quarterBlock.matchAll(/SuraAyah\((\d+),\s*(\d+)\)/g)].map((m) => [+m[1], +m[2]]);
if (quarters.length !== 240) throw new Error(`expected 240 quarters, got ${quarters.length}`);

const suras = en.map((s, i) => ({
  n: s.id,
  ar: s.name,
  tr: s.transliteration,
  en: s.translation,
  ur: ur[i].translation,
  type: s.type,
  count: s.total_verses,
}));

writeFileSync(join(out, 'suras.json'), JSON.stringify(suras));
writeFileSync(join(out, 'text.ar.json'), JSON.stringify(en.map((s) => s.verses.map((v) => v.text))));
writeFileSync(join(out, 'text.en.json'), JSON.stringify(en.map((s) => s.verses.map((v) => v.translation))));
writeFileSync(join(out, 'text.ur.json'), JSON.stringify(ur.map((s) => s.verses.map((v) => v.translation))));
writeFileSync(join(out, 'text.translit.json'), JSON.stringify(tr.map((s) => s.verses.map((v) => v.transliteration))));
writeFileSync(
  join(out, 'mushaf.json'),
  JSON.stringify({ pageStarts: suraForPage.map((s, i) => [s, ayahForPage[i]]), quarters })
);
console.log('wrote data for', suras.length, 'suras');
