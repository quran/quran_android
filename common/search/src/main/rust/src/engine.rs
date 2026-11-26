//! Pure-Rust, zero-unsafe comprehensive Arabic Quran search engine operating directly on UTF-16
//! code units (`u16`) with exact UTF-16 highlight span mapping.
//!
//! Features:
//! - Full Arabic orthographic normalization (strips harakat, Quranic annotation marks, dagger alif,
//!   tatweel, zero-width chars; maps Arabic Presentation Forms-B; folds Alef/Hamza/Ya/Waw variants).
//! - Complete attached Arabic proclitic handling (`و/ف/ب/ك/ل/ت/أ/س`) AND `لِـ + ال` elision
//!   (`لله`/`ولله`/`فلله`, `للجنة`, `للناس`, `للرحمن`, `للمؤمنين`, `لليل`, etc.).
//! - Uthmani open-Ta (`ة` <-> `ت`) matching (`جنت`/`رحمت`/`نعمت`/`شجرت`/`سنت`/`امرأت`/`كلمت`).
//! - Ranked morphological recall for Arabic nouns (Tier 1: exact/proclitic/`لل`/open-Ta ->
//!   Tier 2: singular indefinite/possessive -> Tier 3: dual `تان/تين/تا/تي` & plural `ات/وات/ون/ين`)
//!   strictly guarded by Arabic word boundaries for zero false positives.
//! - Multi-word phrase search (Tier 1 contiguous exact -> Tier 2 contiguous morphological ->
//!   topical all-words fallback when no contiguous phrase exists) + mid-typing prefix fallback.
//! - Sub-millisecond warm lookup over all 6,236 Quran verses via an in-memory inverted word index.

use std::collections::{HashMap, HashSet};

/// Maximum verses allowed in a single loaded index (~16x full Quran of 6,236 verses).
pub const MAX_VERSES: usize = 100_000;
/// Maximum total UTF-16 code units in the contiguous verse text arena (~32 MiB cap;
/// full 6,236-verse Arabic Quran uses ~404,846 code units = ~791 KiB).
pub const MAX_ARENA_CODE_UNITS: usize = 16_000_000;

/// Packed integer stride returned per match by `VerseIndex::search`:
/// `[verse_index, row_id, sura, ayah]`.
pub const MATCH_STRIDE: usize = 4;

// Arabic Unicode constants
const CH_HAMZA: u16 = 0x0621; // ء
const CH_ALEF: u16 = 0x0627; // ا
const CH_BA: u16 = 0x0628; // ب
const CH_TA_MARBUTA: u16 = 0x0629; // ة
const CH_TA: u16 = 0x062A; // ت
const CH_DAL: u16 = 0x062F; // د
const CH_RA: u16 = 0x0631; // ر
const CH_SIN: u16 = 0x0633; // س
const CH_AIN: u16 = 0x0639; // ع
const CH_FA: u16 = 0x0641; // ف
const CH_KAF: u16 = 0x0643; // ك
const CH_LAM: u16 = 0x0644; // ل
const CH_MIM: u16 = 0x0645; // م
const CH_NUN: u16 = 0x0646; // ن
const CH_HA: u16 = 0x0647; // ه
const CH_WAW: u16 = 0x0648; // و
const CH_YA: u16 = 0x064A; // ي
const CH_ALEF_WASLA: u16 = 0x0671; // ٱ
const CH_HA_MARBUTA_END: u16 = 0x064A;
const CH_SPACE: u16 = 0x0020;

const EQUIV_BASE: u16 = 0x0621;
const EQUIV_MAX: u16 = 0x064A;
const EQUIV_SPAN: u16 = EQUIV_MAX - EQUIV_BASE;

#[inline(always)]
const fn equiv_bit(ch: u16) -> u64 {
    1u64 << ((ch - EQUIV_BASE) as u32)
}

const MASK_ALEF: u64 =
    equiv_bit(0x0622) | equiv_bit(0x0623) | equiv_bit(0x0625) | equiv_bit(0x0627) | equiv_bit(0x0649);
const MASK_WAW: u64 = equiv_bit(0x0648) | equiv_bit(0x0624);
const MASK_HAMZA_ALEF: u64 =
    equiv_bit(0x0621) | equiv_bit(0x0623) | equiv_bit(0x0624) | equiv_bit(0x0626) | equiv_bit(0x0627);
const MASK_TA: u64 = equiv_bit(0x062A) | equiv_bit(0x0629);
const MASK_TA_MARBUTA: u64 = equiv_bit(0x0629) | equiv_bit(0x062A) | equiv_bit(0x0647);
const MASK_HA: u64 = equiv_bit(0x0647) | equiv_bit(0x0629);
const MASK_ALEF_MAQSURA_YA: u64 = equiv_bit(0x0649) | equiv_bit(0x064A);
const MASK_YA_HAMZA: u64 = equiv_bit(0x0626) | equiv_bit(0x0649) | equiv_bit(0x064A);

const Q_BIT_ALEF: u64 = 1u64 << 42;
const Q_BIT_WAW: u64 = 1u64 << 43;
const Q_BIT_HAMZA_ALEF: u64 = 1u64 << 44;
const Q_BIT_TA: u64 = 1u64 << 45;
const Q_BIT_TA_MARBUTA: u64 = 1u64 << 46;
const Q_BIT_HA: u64 = 1u64 << 47;
const Q_BIT_ALEF_MAQSURA_YA: u64 = 1u64 << 48;
const Q_BIT_YA_HAMZA: u64 = 1u64 << 49;
const Q_BIT_SPACE: u64 = 1u64 << 50;

/// Returns `true` for Arabic diacritics (harakat/tashkeel), Quranic annotation marks,
/// dagger alif, tatweel, and invisible directional/joiner formatting marks.
#[inline(always)]
pub const fn is_arabic_ignorable(ch: u16) -> bool {
    matches!(
        ch,
        0x0610..=0x061A
            | 0x061C
            | 0x0640
            | 0x064B..=0x065F
            | 0x0670
            | 0x06D6..=0x06ED
            | 0x200B..=0x200F
            | 0xFEFF
    )
}

/// Maps Arabic Presentation Forms-B (`U+FE80..=U+FEFC`) back to standard Arabic base letters.
#[inline(always)]
pub const fn map_presentation_form(ch: u16) -> u16 {
    match ch {
        0xFE80 => 0x0621,
        0xFE81..=0xFE82 => 0x0622,
        0xFE83..=0xFE84 => 0x0623,
        0xFE85..=0xFE86 => 0x0624,
        0xFE87..=0xFE88 => 0x0625,
        0xFE89..=0xFE8C => 0x0626,
        0xFE8D..=0xFE8E => 0x0627,
        0xFE8F..=0xFE92 => 0x0628,
        0xFE93..=0xFE94 => 0x0629,
        0xFE95..=0xFE98 => 0x062A,
        0xFE99..=0xFE9C => 0x062B,
        0xFE9D..=0xFEA0 => 0x062C,
        0xFEA1..=0xFEA4 => 0x062D,
        0xFEA5..=0xFEA8 => 0x062E,
        0xFEA9..=0xFEAA => 0x062F,
        0xFEAB..=0xFEAC => 0x0630,
        0xFEAD..=0xFEAE => 0x0631,
        0xFEAF..=0xFEB0 => 0x0632,
        0xFEB1..=0xFEB4 => 0x0633,
        0xFEB5..=0xFEB8 => 0x0634,
        0xFEB9..=0xFEBC => 0x0635,
        0xFEBD..=0xFEC0 => 0x0636,
        0xFEC1..=0xFEC4 => 0x0637,
        0xFEC5..=0xFEC8 => 0x0638,
        0xFEC9..=0xFECC => 0x0639,
        0xFECD..=0xFED0 => 0x063A,
        0xFED1..=0xFED4 => 0x0641,
        0xFED5..=0xFED8 => 0x0642,
        0xFED9..=0xFEDC => 0x0643,
        0xFEDD..=0xFEE0 => 0x0644,
        0xFEE1..=0xFEE4 => 0x0645,
        0xFEE5..=0xFEE8 => 0x0646,
        0xFEE9..=0xFEEC => 0x0647,
        0xFEED..=0xFEEE => 0x0648,
        0xFEEF..=0xFEF0 => 0x0649,
        0xFEF1..=0xFEF4 => 0x064A,
        _ => ch,
    }
}

/// Folds Arabic letter variants to canonical base letters while preserving `ة` (`0x0629`)
/// distinct from `ه` (`0x0647`) for zero-noise morphological analysis.
#[inline(always)]
pub const fn fold_arabic_letter(ch: u16) -> u16 {
    let base = map_presentation_form(ch);
    match base {
        0x0622 | 0x0623 | 0x0625 | 0x0671 | 0x0672 | 0x0673 => CH_ALEF,
        0x0624 => CH_WAW,
        0x0626 | 0x0649 | 0x06CC => CH_YA,
        _ => base,
    }
}

#[inline(always)]
pub const fn is_arabic_letter(ch: u16) -> bool {
    ch >= EQUIV_BASE && ch <= CH_HA_MARBUTA_END
}

#[inline(always)]
fn is_whitespace_u16(ch: u16) -> bool {
    matches!(ch, 0x0009..=0x000D | 0x0020 | 0x00A0 | 0x2000..=0x200A | 0x202F | 0x3000)
}

#[inline(always)]
pub const fn equivalence_mask(ch: u16) -> Option<u64> {
    match equivalence_mask_and_qbit(ch) {
        Some((mask, _)) => Some(mask),
        None => None,
    }
}

#[inline(always)]
const fn equivalence_mask_and_qbit(ch: u16) -> Option<(u64, u64)> {
    match ch {
        0x0627 => Some((MASK_ALEF, Q_BIT_ALEF)),
        0x0648 => Some((MASK_WAW, Q_BIT_WAW)),
        0x0623 => Some((MASK_HAMZA_ALEF, Q_BIT_HAMZA_ALEF)),
        0x0621 => Some((MASK_HAMZA_ALEF, Q_BIT_HAMZA_ALEF)),
        0x062A => Some((MASK_TA, Q_BIT_TA)),
        0x0629 => Some((MASK_TA_MARBUTA, Q_BIT_TA_MARBUTA)),
        0x0647 => Some((MASK_HA, Q_BIT_HA)),
        0x0649 => Some((MASK_ALEF_MAQSURA_YA, Q_BIT_ALEF_MAQSURA_YA)),
        0x064A => Some((MASK_ALEF_MAQSURA_YA, Q_BIT_ALEF_MAQSURA_YA)),
        0x0626 => Some((MASK_YA_HAMZA, Q_BIT_YA_HAMZA)),
        _ => None,
    }
}

/// Computes the 64-bit character & equivalence-group presence mask for a UTF-16 slice.
#[inline]
pub fn compute_verse_letter_mask(u16s: &[u16]) -> u64 {
    let mut raw_mask = 0u64;
    for &ch in u16s {
        let delta = ch.wrapping_sub(EQUIV_BASE);
        if delta <= EQUIV_SPAN {
            raw_mask |= 1u64 << (delta as u32);
        } else if ch == CH_SPACE {
            raw_mask |= Q_BIT_SPACE;
        }
    }
    let mut mask = raw_mask;
    if (raw_mask & MASK_ALEF) != 0 {
        mask |= Q_BIT_ALEF;
    }
    if (raw_mask & MASK_WAW) != 0 {
        mask |= Q_BIT_WAW;
    }
    if (raw_mask & MASK_HAMZA_ALEF) != 0 {
        mask |= Q_BIT_HAMZA_ALEF;
    }
    if (raw_mask & MASK_TA) != 0 {
        mask |= Q_BIT_TA;
    }
    if (raw_mask & MASK_TA_MARBUTA) != 0 {
        mask |= Q_BIT_TA_MARBUTA;
    }
    if (raw_mask & MASK_HA) != 0 {
        mask |= Q_BIT_HA;
    }
    if (raw_mask & MASK_ALEF_MAQSURA_YA) != 0 {
        mask |= Q_BIT_ALEF_MAQSURA_YA;
    }
    if (raw_mask & MASK_YA_HAMZA) != 0 {
        mask |= Q_BIT_YA_HAMZA;
    }
    mask
}

#[inline(always)]
fn matches_mask(ch: u16, mask: u64) -> bool {
    let delta = ch.wrapping_sub(EQUIV_BASE);
    delta <= EQUIV_SPAN && ((mask >> delta) & 1) != 0
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PatternUnit {
    Exact(u16),
    Mask(u64),
}

impl PatternUnit {
    #[inline(always)]
    fn matches(self, ch: u16) -> bool {
        match self {
            PatternUnit::Exact(expected) => ch == expected,
            PatternUnit::Mask(mask) => matches_mask(ch, mask),
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Anchor {
    ExactPair { offset: usize, first: u16, second: u16 },
    ExactSingle { offset: usize, ch: u16 },
    FirstMask(u64),
}

#[derive(Clone, Debug)]
struct LiteralMatcher {
    units: Vec<PatternUnit>,
    anchor: Anchor,
    query_mask: u64,
}

impl LiteralMatcher {
    fn compile(query_u16: &[u16]) -> Option<Self> {
        let mut units = Vec::with_capacity(query_u16.len());
        let mut query_mask = 0u64;
        for &ch in query_u16 {
            if ch == b'(' as u16 || ch == b')' as u16 {
                continue;
            }
            if let Some((mask, qbit)) = equivalence_mask_and_qbit(ch) {
                units.push(PatternUnit::Mask(mask));
                query_mask |= qbit;
            } else {
                units.push(PatternUnit::Exact(ch));
                let delta = ch.wrapping_sub(EQUIV_BASE);
                if delta <= EQUIV_SPAN {
                    query_mask |= 1u64 << (delta as u32);
                } else if ch == CH_SPACE {
                    query_mask |= Q_BIT_SPACE;
                }
            }
        }
        if units.is_empty() {
            return None;
        }
        let anchor = Self::select_anchor(&units);
        Some(Self { units, anchor, query_mask })
    }

    fn select_anchor(units: &[PatternUnit]) -> Anchor {
        let mut best_pair: Option<(usize, u16, u16, u8)> = None;
        for i in 0..units.len().saturating_sub(1) {
            if let (PatternUnit::Exact(a), PatternUnit::Exact(b)) = (units[i], units[i + 1]) {
                if a == CH_SPACE && b == CH_SPACE {
                    continue;
                }
                let score = Self::exact_rarity_score(a) + Self::exact_rarity_score(b);
                match best_pair {
                    None => best_pair = Some((i, a, b, score)),
                    Some((_, _, _, best_score)) if score > best_score => {
                        best_pair = Some((i, a, b, score));
                    }
                    _ => {}
                }
            }
        }
        if let Some((offset, first, second, _)) = best_pair {
            return Anchor::ExactPair { offset, first, second };
        }
        let mut best_single: Option<(usize, u16, u8)> = None;
        for (i, &unit) in units.iter().enumerate() {
            if let PatternUnit::Exact(ch) = unit {
                let score = Self::exact_rarity_score(ch);
                match best_single {
                    None => best_single = Some((i, ch, score)),
                    Some((_, _, best_score)) if score > best_score => {
                        best_single = Some((i, ch, score));
                    }
                    _ => {}
                }
            }
        }
        if let Some((offset, ch, _)) = best_single {
            return Anchor::ExactSingle { offset, ch };
        }
        match units[0] {
            PatternUnit::Mask(mask) => Anchor::FirstMask(mask),
            PatternUnit::Exact(ch) => Anchor::ExactSingle { offset: 0, ch },
        }
    }

    #[inline]
    fn exact_rarity_score(ch: u16) -> u8 {
        match ch {
            CH_SPACE => 0,
            CH_LAM | CH_MIM | CH_NUN => 1,
            CH_RA | CH_BA | CH_KAF => 2,
            _ => 3,
        }
    }

    #[inline]
    fn matches_at(&self, text: &[u16], start: usize, skip_a: usize, skip_b: usize) -> bool {
        let Some(window) = text.get(start..start + self.units.len()) else {
            return false;
        };
        for (i, (&unit, &ch)) in self.units.iter().zip(window.iter()).enumerate() {
            if i == skip_a || i == skip_b {
                continue;
            }
            if !unit.matches(ch) {
                return false;
            }
        }
        true
    }

    #[inline]
    fn find_first(&self, text: &[u16], from_index: usize) -> Option<usize> {
        let pat_len = self.units.len();
        if pat_len == 0 || text.len() < pat_len {
            return None;
        }
        let max_start = text.len() - pat_len;
        if from_index > max_start {
            return None;
        }
        match self.anchor {
            Anchor::ExactPair { offset, first, second } => {
                let scan_start = from_index + offset;
                let scan_end_inclusive = max_start + offset;
                let mut pos = scan_start;
                while pos <= scan_end_inclusive {
                    let b = text[pos + 1];
                    if b != second {
                        pos += if b == first { 1 } else { 2 };
                    } else if text[pos] == first {
                        let candidate = pos - offset;
                        if self.matches_at(text, candidate, offset, offset + 1) {
                            return Some(candidate);
                        }
                        pos += if first == second { 1 } else { 2 };
                    } else {
                        pos += if first == second { 1 } else { 2 };
                    }
                }
                None
            }
            Anchor::ExactSingle { offset, ch } => {
                let scan_start = from_index + offset;
                let scan_end_inclusive = max_start + offset;
                let mut pos = scan_start;
                while pos <= scan_end_inclusive {
                    if text[pos] == ch {
                        let candidate = pos - offset;
                        if self.matches_at(text, candidate, offset, usize::MAX) {
                            return Some(candidate);
                        }
                    }
                    pos += 1;
                }
                None
            }
            Anchor::FirstMask(first_mask) => {
                let mut candidate = from_index;
                while candidate <= max_start {
                    if matches_mask(text[candidate], first_mask)
                        && self.matches_at(text, candidate, 0, usize::MAX)
                    {
                        return Some(candidate);
                    }
                    candidate += 1;
                }
                None
            }
        }
    }

    fn find_all_spans(&self, text: &[u16]) -> Vec<i32> {
        let pat_len = self.units.len();
        if pat_len == 0 || text.len() < pat_len {
            return Vec::new();
        }
        let mut spans = Vec::with_capacity(8);
        let mut pos = 0usize;
        while let Some(start) = self.find_first(text, pos) {
            let end = start + pat_len;
            if let (Ok(s), Ok(e)) = (i32::try_from(start), i32::try_from(end)) {
                spans.push(s);
                spans.push(e);
            } else {
                break;
            }
            pos = end;
        }
        spans
    }
}

// ================================================================================================
// Comprehensive Arabic Morphological Word & Phrase Matcher
// ================================================================================================

const ALLAH_PROCLITICS: &[&[u16]] = &[
    &[],
    &[CH_WAW],
    &[CH_FA],
    &[CH_BA],
    &[CH_TA],
    &[CH_KAF],
    &[CH_ALEF],
    &[CH_WAW, CH_BA],
    &[CH_FA, CH_BA],
    &[CH_WAW, CH_TA],
    &[CH_FA, CH_TA],
    &[CH_ALEF, CH_BA],
    &[CH_ALEF, CH_WAW],
    &[CH_ALEF, CH_FA],
    &[CH_FA, CH_WAW],
    &[CH_WAW, CH_KAF],
    &[CH_FA, CH_KAF],
];

const DEF_PROCLITICS: &[&[u16]] = &[
    &[],
    &[CH_WAW],
    &[CH_FA],
    &[CH_BA],
    &[CH_KAF],
    &[CH_ALEF],
    &[CH_WAW, CH_BA],
    &[CH_FA, CH_BA],
    &[CH_WAW, CH_KAF],
    &[CH_FA, CH_KAF],
    &[CH_ALEF, CH_WAW],
    &[CH_ALEF, CH_FA],
    &[CH_ALEF, CH_BA],
];

const L_PROCLITICS: &[&[u16]] = &[
    &[],
    &[CH_WAW],
    &[CH_FA],
    &[CH_ALEF],
    &[CH_ALEF, CH_WAW],
    &[CH_ALEF, CH_FA],
];

const INDEF_PROCLITICS: &[&[u16]] = &[
    &[],
    &[CH_WAW],
    &[CH_FA],
    &[CH_BA],
    &[CH_KAF],
    &[CH_LAM],
    &[CH_WAW, CH_BA],
    &[CH_FA, CH_BA],
    &[CH_WAW, CH_LAM],
    &[CH_FA, CH_LAM],
    &[CH_WAW, CH_KAF],
    &[CH_FA, CH_KAF],
];

const VERB_PROCLITICS: &[&[u16]] = &[
    &[CH_SIN],
    &[CH_WAW, CH_SIN],
    &[CH_FA, CH_SIN],
];

const SHORT_PHRASE_PROCLITICS: &[&[u16]] = &[
    &[],
    &[CH_WAW],
    &[CH_FA],
    &[CH_BA],
    &[CH_LAM],
    &[CH_KAF],
];

const PRONOUNS: &[&[u16]] = &[
    &[CH_HA],
    &[CH_HA, CH_ALEF],
    &[CH_HA, CH_MIM],
    &[CH_HA, CH_MIM, CH_ALEF],
    &[CH_HA, CH_NUN],
    &[CH_KAF],
    &[CH_KAF, CH_MIM],
    &[CH_KAF, CH_MIM, CH_ALEF],
    &[CH_KAF, CH_NUN],
    &[CH_YA],
    &[CH_NUN, CH_ALEF],
];

const WORD_ALLAH: &[u16] = &[CH_ALEF, CH_LAM, CH_LAM, CH_HA];
const WORD_ALLAH_TA: &[u16] = &[CH_ALEF, CH_LAM, CH_LAM, CH_TA_MARBUTA];
const WORD_LILLAH: &[u16] = &[CH_LAM, CH_LAM, CH_HA];
const WORD_ALLAHUMMA: &[u16] = &[CH_ALEF, CH_LAM, CH_LAM, CH_HA, CH_MIM];
const WORD_ABDULLAH: &[u16] = &[CH_AIN, CH_BA, CH_DAL, CH_ALEF, CH_LAM, CH_LAM, CH_HA];
const WORD_RAHMAN: &[u16] = &[CH_RA, 0x062D, CH_MIM, CH_NUN]; // رحمن

#[inline]
fn concat_slices(parts: &[&[u16]]) -> Vec<u16> {
    let total: usize = parts.iter().map(|p| p.len()).sum();
    let mut out = Vec::with_capacity(total);
    for p in parts {
        out.extend_from_slice(p);
    }
    out
}

fn add_definite_forms(set: &mut HashSet<Vec<u16>>, stem: &[u16]) {
    let al: &[u16] = &[CH_ALEF, CH_LAM];
    for &p in DEF_PROCLITICS {
        set.insert(concat_slices(&[p, al, stem]));
    }
    // Arabic preposition لِـ + ال elides Alef -> لل (or single ل if stem already starts with ل)
    let l_elided: &[u16] = if stem.first() == Some(&CH_LAM) {
        &[CH_LAM]
    } else {
        &[CH_LAM, CH_LAM]
    };
    for &p in L_PROCLITICS {
        set.insert(concat_slices(&[p, l_elided, stem]));
    }
}

fn add_indefinite_forms(set: &mut HashSet<Vec<u16>>, stem: &[u16], allow_verb_future: bool) {
    for &p in INDEF_PROCLITICS {
        set.insert(concat_slices(&[p, stem]));
    }
    if allow_verb_future {
        for &p in VERB_PROCLITICS {
            set.insert(concat_slices(&[p, stem]));
        }
    }
}

#[inline]
fn ends_with_slice(hay: &[u16], suf: &[u16]) -> bool {
    hay.len() >= suf.len() && &hay[hay.len() - suf.len()..] == suf
}

#[derive(Clone, Debug)]
struct WordMatcher {
    tier1: HashSet<Vec<u16>>,
    tier2: HashSet<Vec<u16>>,
    tier3: HashSet<Vec<u16>>,
}

impl WordMatcher {
    fn build(orig_first: u16, orig_last: u16, norm_w: &[u16]) -> Self {
        let mut t1: HashSet<Vec<u16>> = HashSet::with_capacity(32);
        let mut t2: HashSet<Vec<u16>> = HashSet::new();
        let mut t3: HashSet<Vec<u16>> = HashSet::new();

        // Short words (< 3 letters) inside multi-word phrases
        if norm_w.len() < 3 {
            for &p in SHORT_PHRASE_PROCLITICS {
                t1.insert(concat_slices(&[p, norm_w]));
            }
            if norm_w.len() == 2 {
                add_definite_forms(&mut t1, norm_w);
            }
            return Self { tier1: t1, tier2: t2, tier3: t3 };
        }

        // Case 1: The Blessed Name "الله" (or "اللة")
        if norm_w == WORD_ALLAH || norm_w == WORD_ALLAH_TA {
            for &p in ALLAH_PROCLITICS {
                t1.insert(concat_slices(&[p, WORD_ALLAH]));
            }
            for &p in L_PROCLITICS {
                t1.insert(concat_slices(&[p, WORD_LILLAH]));
            }
            for &p in &[&[][..], &[CH_WAW][..], &[CH_FA][..]] {
                t1.insert(concat_slices(&[p, WORD_ALLAHUMMA]));
            }
            for &p in SHORT_PHRASE_PROCLITICS {
                t1.insert(concat_slices(&[p, WORD_ABDULLAH]));
            }
            return Self { tier1: t1, tier2: t2, tier3: t3 };
        }

        // Detect definite article ال (orig_first must be plain ا or Alef Wasla ٱ, never أ/إ/آ)
        let has_al = (orig_first == CH_ALEF || orig_first == CH_ALEF_WASLA)
            && norm_w.len() >= 4
            && norm_w[0] == CH_ALEF
            && norm_w[1] == CH_LAM;
        let stem = if has_al { &norm_w[2..] } else { norm_w };

        // Detect feminine ة-noun:
        // - Explicit ة ending (stem len >= 3), OR
        // - Definite ال + ه ending (lazy keyboard typing like الجنه / الصلاه / الرحمه)
        let is_fem = stem.len() >= 3
            && (orig_last == CH_TA_MARBUTA || (has_al && orig_last == CH_HA));

        if is_fem {
            let base = &stem[..stem.len() - 1]; // len >= 2
            let fem_ends: &[u16] = if orig_last == CH_HA {
                &[CH_TA_MARBUTA, CH_TA, CH_HA]
            } else {
                &[CH_TA_MARBUTA, CH_TA]
            };

            if has_al {
                for &e in fem_ends {
                    let form = concat_slices(&[base, &[e]]);
                    add_definite_forms(&mut t1, &form);
                    add_indefinite_forms(&mut t2, &form, false);
                }
            } else {
                for &e in fem_ends {
                    let form = concat_slices(&[base, &[e]]);
                    add_indefinite_forms(&mut t1, &form, false);
                    add_definite_forms(&mut t1, &form);
                }
            }

            // Tier 2: Singular possessive (base + ت + pronoun)
            for &pron in PRONOUNS {
                let form = concat_slices(&[base, &[CH_TA], pron]);
                add_indefinite_forms(&mut t2, &form, false);
            }

            // Tier 3: Dual (تان / تين + construct تا/تي + pronoun)
            for dual_end in &[&[CH_TA, CH_ALEF, CH_NUN][..], &[CH_TA, CH_YA, CH_NUN][..]] {
                let form = concat_slices(&[base, dual_end]);
                add_definite_forms(&mut t3, &form);
                add_indefinite_forms(&mut t3, &form, false);
            }
            for dual_c in &[&[CH_TA, CH_ALEF][..], &[CH_TA, CH_YA][..]] {
                for &pron in PRONOUNS {
                    let form = concat_slices(&[base, dual_c, pron]);
                    add_indefinite_forms(&mut t3, &form, false);
                }
            }

            // Tier 3: Sound feminine plural (ات + optional pronoun; صلا -> صلوات for base ending in ا)
            let mut plurals = Vec::with_capacity(2);
            plurals.push(concat_slices(&[base, &[CH_ALEF, CH_TA]]));
            if base.len() >= 3 && base.last() == Some(&CH_ALEF) {
                plurals.push(concat_slices(&[&base[..base.len() - 1], &[CH_WAW, CH_ALEF, CH_TA]]));
            }
            for pl in &plurals {
                add_definite_forms(&mut t3, pl);
                add_indefinite_forms(&mut t3, pl, false);
                for &pron in PRONOUNS {
                    let form = concat_slices(&[pl, pron]);
                    add_indefinite_forms(&mut t3, &form, false);
                }
            }
        } else if has_al {
            // Definite non-feminine noun
            add_definite_forms(&mut t1, stem);
            if stem.len() >= 3 {
                add_indefinite_forms(&mut t2, stem, false);
                let last = *stem.last().unwrap_or(&0);
                if last != CH_ALEF && last != CH_HAMZA && stem != WORD_RAHMAN {
                    let tanwin = concat_slices(&[stem, &[CH_ALEF]]);
                    add_indefinite_forms(&mut t2, &tanwin, false);
                }
                let ends_wn = ends_with_slice(stem, &[CH_WAW, CH_NUN]);
                let ends_yn = ends_with_slice(stem, &[CH_YA, CH_NUN]);
                if !ends_wn && !ends_yn {
                    for &pron in PRONOUNS {
                        let form = concat_slices(&[stem, pron]);
                        add_indefinite_forms(&mut t2, &form, false);
                    }
                }
                if stem.len() >= 5 && ends_wn {
                    let alt = concat_slices(&[&stem[..stem.len() - 2], &[CH_YA, CH_NUN]]);
                    add_definite_forms(&mut t3, &alt);
                    add_indefinite_forms(&mut t3, &alt, false);
                } else if stem.len() >= 5 && ends_yn {
                    let alt = concat_slices(&[&stem[..stem.len() - 2], &[CH_WAW, CH_NUN]]);
                    add_definite_forms(&mut t3, &alt);
                    add_indefinite_forms(&mut t3, &alt, false);
                }
            } else {
                // 2-letter definite stems (e.g. الجن/اليد/الدم): allow only bare & و+stem
                // so unrelated 3-letter roots (e.g. فرح from الرح, كلب from اللب) never collide.
                t2.insert(stem.to_vec());
                t2.insert(concat_slices(&[&[CH_WAW], stem]));
            }
        } else {
            // Indefinite non-feminine Arabic word (len >= 3)
            let is_verb_like = norm_w.len() >= 4
                && matches!(norm_w[0], CH_YA | CH_TA | CH_NUN);
            add_indefinite_forms(&mut t1, norm_w, is_verb_like);
            add_definite_forms(&mut t1, norm_w);
            if norm_w.last() == Some(&CH_HA) {
                let alt_ta = concat_slices(&[&norm_w[..norm_w.len() - 1], &[CH_TA_MARBUTA]]);
                add_indefinite_forms(&mut t1, &alt_ta, false);
                add_definite_forms(&mut t1, &alt_ta);
            }

            let last = *norm_w.last().unwrap_or(&0);
            let ends_wn = ends_with_slice(norm_w, &[CH_WAW, CH_NUN]);
            let ends_yn = ends_with_slice(norm_w, &[CH_YA, CH_NUN]);
            let ends_wa = ends_with_slice(norm_w, &[CH_WAW, CH_ALEF]);
            let ends_tm = ends_with_slice(norm_w, &[CH_TA, CH_MIM]);
            let ends_tn = ends_with_slice(norm_w, &[CH_TA, CH_NUN]);
            let ends_pron = last == CH_HA
                || ends_with_slice(norm_w, &[CH_HA, CH_ALEF])
                || ends_with_slice(norm_w, &[CH_HA, CH_MIM])
                || ends_with_slice(norm_w, &[CH_KAF, CH_MIM])
                || ends_with_slice(norm_w, &[CH_NUN, CH_ALEF]);

            if last != CH_ALEF
                && last != CH_HAMZA
                && !ends_wn
                && !ends_yn
                && !ends_wa
                && !ends_tm
                && !ends_tn
                && norm_w != WORD_RAHMAN
            {
                let tanwin = concat_slices(&[norm_w, &[CH_ALEF]]);
                add_indefinite_forms(&mut t2, &tanwin, false);
            }
            if !ends_wa && !ends_wn && !ends_yn && !ends_tm && !ends_tn && !ends_pron {
                for &pron in PRONOUNS {
                    let form = concat_slices(&[norm_w, pron]);
                    add_indefinite_forms(&mut t2, &form, false);
                }
            }
            if norm_w.len() >= 5 && ends_wn {
                let alt = concat_slices(&[&norm_w[..norm_w.len() - 2], &[CH_YA, CH_NUN]]);
                add_indefinite_forms(&mut t3, &alt, false);
                add_definite_forms(&mut t3, &alt);
            } else if norm_w.len() >= 5 && ends_yn {
                let alt = concat_slices(&[&norm_w[..norm_w.len() - 2], &[CH_WAW, CH_NUN]]);
                add_indefinite_forms(&mut t3, &alt, false);
                add_definite_forms(&mut t3, &alt);
            }
        }

        // Deduplicate strictly across tiers so Tier 1 > Tier 2 > Tier 3
        t2.retain(|k| !t1.contains(k));
        t3.retain(|k| !t1.contains(k) && !t2.contains(k));

        Self { tier1: t1, tier2: t2, tier3: t3 }
    }

    #[inline]
    fn matches_any(&self, tok: &[u16]) -> bool {
        self.tier1.contains(tok) || self.tier2.contains(tok) || self.tier3.contains(tok)
    }

    #[inline]
    fn match_tier(&self, tok: &[u16]) -> u8 {
        if self.tier1.contains(tok) {
            1
        } else if self.tier2.contains(tok) {
            2
        } else if self.tier3.contains(tok) {
            3
        } else {
            0
        }
    }
}

/// Token extracted from raw UTF-16 text with exact original `[start, end)` span (including trailing diacritics).
struct TokenSpan {
    start: usize,
    end: usize,
    norm: Vec<u16>,
}

fn tokenize_with_spans(text: &[u16]) -> Vec<TokenSpan> {
    let mut tokens = Vec::with_capacity(16);
    let mut cur_norm: Vec<u16> = Vec::with_capacity(16);
    let mut cur_start = 0usize;
    let mut cur_end = 0usize;

    for (i, &ch) in text.iter().enumerate() {
        if is_arabic_ignorable(ch) {
            if !cur_norm.is_empty() {
                cur_end = i + 1;
            }
            continue;
        }
        let folded = fold_arabic_letter(ch);
        if is_arabic_letter(folded) {
            if cur_norm.is_empty() {
                cur_start = i;
            }
            cur_norm.push(folded);
            cur_end = i + 1;
        } else if !cur_norm.is_empty() {
            tokens.push(TokenSpan {
                start: cur_start,
                end: cur_end,
                norm: std::mem::take(&mut cur_norm),
            });
        }
    }
    if !cur_norm.is_empty() {
        tokens.push(TokenSpan {
            start: cur_start,
            end: cur_end,
            norm: cur_norm,
        });
    }
    tokens
}

/// Normalizes a raw UTF-16 slice into space-separated folded Arabic tokens along with per-character
/// original UTF-16 `[orig_start, orig_end)` ranges (absorbing trailing diacritics).
fn normalize_with_offsets(text: &[u16]) -> (Vec<u16>, Vec<usize>, Vec<usize>) {
    let mut norm = Vec::with_capacity(text.len());
    let mut orig_start = Vec::with_capacity(text.len());
    let mut orig_end = Vec::with_capacity(text.len());
    let mut prev_space = true;

    for (i, &ch) in text.iter().enumerate() {
        if is_arabic_ignorable(ch) {
            if !norm.is_empty() && !prev_space {
                if let Some(last_end) = orig_end.last_mut() {
                    *last_end = i + 1;
                }
            }
            continue;
        }
        let folded = fold_arabic_letter(ch);
        if is_arabic_letter(folded) {
            norm.push(folded);
            orig_start.push(i);
            orig_end.push(i + 1);
            prev_space = false;
        } else if !prev_space && !norm.is_empty() {
            norm.push(CH_SPACE);
            orig_start.push(i);
            orig_end.push(i + 1);
            prev_space = true;
        }
    }
    if norm.last() == Some(&CH_SPACE) {
        norm.pop();
        orig_start.pop();
        orig_end.pop();
    }
    (norm, orig_start, orig_end)
}

#[inline(always)]
fn norm_char_matches(query_ch: u16, text_ch: u16) -> bool {
    if query_ch == text_ch {
        return true;
    }
    match query_ch {
        CH_HA => text_ch == CH_TA_MARBUTA,
        CH_TA_MARBUTA => text_ch == CH_HA || text_ch == CH_TA,
        CH_TA => text_ch == CH_TA_MARBUTA,
        _ => false,
    }
}

fn find_norm_substr_first(haystack: &[u16], needle: &[u16], from: usize) -> Option<usize> {
    let nlen = needle.len();
    if nlen == 0 || haystack.len() < nlen || from > haystack.len() - nlen {
        return None;
    }
    let max_start = haystack.len() - nlen;
    let first = needle[0];
    let mut i = from;
    while i <= max_start {
        if norm_char_matches(first, haystack[i]) {
            let mut j = 1usize;
            while j < nlen && norm_char_matches(needle[j], haystack[i + j]) {
                j += 1;
            }
            if j == nlen {
                return Some(i);
            }
        }
        i += 1;
    }
    None
}

fn find_norm_substr_spans_in_raw(raw_text: &[u16], needle: &[u16]) -> Vec<i32> {
    if needle.is_empty() || raw_text.is_empty() {
        return Vec::new();
    }
    let (norm, orig_start, orig_end) = normalize_with_offsets(raw_text);
    if norm.len() < needle.len() {
        return Vec::new();
    }
    let mut spans = Vec::with_capacity(8);
    let mut pos = 0usize;
    while let Some(idx) = find_norm_substr_first(&norm, needle, pos) {
        let end_idx = idx + needle.len();
        let s = orig_start[idx];
        let e = orig_end[end_idx - 1];
        if let (Ok(si), Ok(ei)) = (i32::try_from(s), i32::try_from(e)) {
            spans.push(si);
            spans.push(ei);
        }
        pos = end_idx;
    }
    spans
}

#[derive(Clone, Debug)]
enum QueryMode {
    SingleWord {
        matcher: WordMatcher,
        fallback_needle: Vec<u16>,
    },
    MultiWord {
        words: Vec<WordMatcher>,
        norm_phrase: Vec<u16>,
    },
    ShortArabic {
        needle: Vec<u16>,
        literal_fallback: LiteralMatcher,
    },
    Literal(LiteralMatcher),
    Empty,
}

/// Compiled comprehensive Arabic search query over UTF-16 code units.
#[derive(Clone, Debug)]
pub struct CompiledArabicQuery {
    mode: QueryMode,
    effective_len: usize,
    query_mask: u64,
}

impl CompiledArabicQuery {
    /// Compiles a UTF-16 query into a comprehensive Arabic search matcher.
    ///
    /// Returns `None` ONLY if the query is empty or parentheses-only (`""`, `"()"`).
    pub fn compile(query_u16: &[u16]) -> Option<Self> {
        // Strip outer/literal parentheses as in original engine; return None if nothing remains.
        let mut non_paren_count = 0usize;
        let mut has_arabic_letter = false;
        let mut has_non_arabic_symbol = false;

        for &ch in query_u16 {
            if ch == b'(' as u16 || ch == b')' as u16 {
                continue;
            }
            non_paren_count += 1;
            if is_arabic_ignorable(ch) || is_whitespace_u16(ch) {
                continue;
            }
            let folded = fold_arabic_letter(ch);
            if is_arabic_letter(folded) {
                has_arabic_letter = true;
            } else {
                has_non_arabic_symbol = true;
            }
        }

        if non_paren_count == 0 {
            return None;
        }

        // If query contains symbols, Latin, regex metacharacters, or emoji (e.g. ".*+[", "🕌الله"),
        // preserve exact literal UTF-16 matching.
        if !has_arabic_letter || has_non_arabic_symbol {
            let lit = LiteralMatcher::compile(query_u16)?;
            let mask = lit.query_mask;
            let len = lit.units.len();
            return Some(Self {
                mode: QueryMode::Literal(lit),
                effective_len: len,
                query_mask: mask,
            });
        }

        // Pure Arabic query: extract words with original first/last characters preserved.
        let mut raw_words: Vec<(u16, u16, Vec<u16>)> = Vec::new();
        let mut cur_norm: Vec<u16> = Vec::new();
        let mut cur_orig_first = 0u16;
        let mut cur_orig_last = 0u16;

        for &ch in query_u16 {
            if ch == b'(' as u16 || ch == b')' as u16 || is_arabic_ignorable(ch) {
                continue;
            }
            if is_whitespace_u16(ch) {
                if !cur_norm.is_empty() {
                    raw_words.push((
                        cur_orig_first,
                        cur_orig_last,
                        std::mem::take(&mut cur_norm),
                    ));
                }
                continue;
            }
            let unpresented = map_presentation_form(ch);
            let folded = fold_arabic_letter(ch);
            if is_arabic_letter(folded) {
                if cur_norm.is_empty() {
                    cur_orig_first = unpresented;
                }
                cur_orig_last = unpresented;
                cur_norm.push(folded);
            }
        }
        if !cur_norm.is_empty() {
            raw_words.push((cur_orig_first, cur_orig_last, cur_norm));
        }

        if raw_words.is_empty() {
            return Some(Self {
                mode: QueryMode::Empty,
                effective_len: 0,
                query_mask: 0,
            });
        }

        if raw_words.len() == 1 {
            let (orig_first, orig_last, norm_w) = raw_words.into_iter().next().unwrap();
            let eff_len = norm_w.len();
            if eff_len < 3 {
                // Short 1-2 letter Arabic query: preserve direct substring semantics with normalization
                let lit = LiteralMatcher::compile(&norm_w)?;
                let mask = lit.query_mask;
                return Some(Self {
                    mode: QueryMode::ShortArabic {
                        needle: norm_w,
                        literal_fallback: lit,
                    },
                    effective_len: eff_len,
                    query_mask: mask,
                });
            }
            let matcher = WordMatcher::build(orig_first, orig_last, &norm_w);
            return Some(Self {
                mode: QueryMode::SingleWord {
                    matcher,
                    fallback_needle: norm_w,
                },
                effective_len: eff_len,
                query_mask: 0,
            });
        }

        // Multi-word Arabic query
        let mut norm_phrase = Vec::new();
        let mut words = Vec::with_capacity(raw_words.len());
        for (idx, (orig_first, orig_last, norm_w)) in raw_words.into_iter().enumerate() {
            if idx > 0 {
                norm_phrase.push(CH_SPACE);
            }
            norm_phrase.extend_from_slice(&norm_w);
            words.push(WordMatcher::build(orig_first, orig_last, &norm_w));
        }
        let eff_len = norm_phrase.len();
        Some(Self {
            mode: QueryMode::MultiWord { words, norm_phrase },
            effective_len: eff_len,
            query_mask: 0,
        })
    }

    pub fn compile_str(query: &str) -> Option<Self> {
        let u16s: Vec<u16> = query.encode_utf16().collect();
        Self::compile(&u16s)
    }

    #[inline(always)]
    pub fn query_mask(&self) -> u64 {
        self.query_mask
    }

    pub fn len(&self) -> usize {
        self.effective_len
    }

    pub fn is_empty(&self) -> bool {
        matches!(self.mode, QueryMode::Empty) || self.effective_len == 0
    }

    /// Finds the first UTF-16 start offset `>= from_index` where the query matches `text`.
    pub fn find_first(&self, text: &[u16], from_index: usize) -> Option<usize> {
        if from_index > text.len() {
            return None;
        }
        match &self.mode {
            QueryMode::Empty => None,
            QueryMode::Literal(lit) => lit.find_first(text, from_index),
            _ => {
                let spans = self.find_all_spans(text);
                for pair in spans.chunks_exact(2) {
                    let s = pair[0] as usize;
                    if s >= from_index {
                        return Some(s);
                    }
                }
                None
            }
        }
    }

    /// Finds all non-overlapping left-to-right UTF-16 match spans `[start0, end0, start1, end1, ...]`
    /// directly in the original `text` UTF-16 coordinate space (absorbing trailing diacritics).
    pub fn find_all_spans(&self, text: &[u16]) -> Vec<i32> {
        if text.is_empty() {
            return Vec::new();
        }
        match &self.mode {
            QueryMode::Empty => Vec::new(),
            QueryMode::Literal(lit) => lit.find_all_spans(text),
            QueryMode::ShortArabic { needle, .. } => find_norm_substr_spans_in_raw(text, needle),
            QueryMode::SingleWord { matcher, fallback_needle } => {
                let tokens = tokenize_with_spans(text);
                let mut spans = Vec::with_capacity(8);
                for tok in &tokens {
                    if matcher.matches_any(&tok.norm) {
                        if let (Ok(s), Ok(e)) = (i32::try_from(tok.start), i32::try_from(tok.end)) {
                            spans.push(s);
                            spans.push(e);
                        }
                    }
                }
                if !spans.is_empty() {
                    return spans;
                }
                // Substring fallback (e.g. mid-typing prefix like "الرح")
                find_norm_substr_spans_in_raw(text, fallback_needle)
            }
            QueryMode::MultiWord { words, norm_phrase } => {
                let tokens = tokenize_with_spans(text);
                let k = words.len();
                let mut spans = Vec::with_capacity(8);
                if tokens.len() >= k {
                    // 1. Prefer contiguous phrase spans (merged start..end)
                    let mut i = 0usize;
                    while i + k <= tokens.len() {
                        let mut matched = true;
                        for j in 0..k {
                            if !words[j].matches_any(&tokens[i + j].norm) {
                                matched = false;
                                break;
                            }
                        }
                        if matched {
                            let s = tokens[i].start;
                            let e = tokens[i + k - 1].end;
                            if let (Ok(si), Ok(ei)) = (i32::try_from(s), i32::try_from(e)) {
                                spans.push(si);
                                spans.push(ei);
                            }
                            i += k;
                        } else {
                            i += 1;
                        }
                    }
                    if !spans.is_empty() {
                        return spans;
                    }
                }
                // 2. Non-contiguous all-words match: highlight every matched word token
                let mut found_word = vec![false; k];
                for tok in &tokens {
                    let mut any_match = false;
                    for (wi, wm) in words.iter().enumerate() {
                        if wm.matches_any(&tok.norm) {
                            found_word[wi] = true;
                            any_match = true;
                        }
                    }
                    if any_match {
                        if let (Ok(s), Ok(e)) = (i32::try_from(tok.start), i32::try_from(tok.end)) {
                            spans.push(s);
                            spans.push(e);
                        }
                    }
                }
                if !spans.is_empty() && found_word.iter().all(|&f| f) {
                    return spans;
                }
                // 3. Substring fallback (mid-typing last word)
                find_norm_substr_spans_in_raw(text, norm_phrase)
            }
        }
    }
}

// ================================================================================================
// Indexed Verse Arena + Inverted Arabic Word Index
// ================================================================================================

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct VerseMeta {
    pub row_id: i32,
    pub sura: i32,
    pub ayah: i32,
    pub offset: u32,
    pub len: u32,
    pub letter_mask: u64,
}

#[derive(Clone, Copy, Debug)]
struct NormVerseMeta {
    offset: u32,
    len: u32,
    letter_mask: u64,
}

/// Cache-friendly contiguous UTF-16 verse arena plus inverted normalized Arabic word index.
/// Holds all 6,236 Quran verses in ~2.1 MiB and answers morphological searches in < 0.2 ms.
#[derive(Debug)]
pub struct VerseIndex {
    verses: Vec<VerseMeta>,
    arena: Vec<u16>,
    norm_meta: Vec<NormVerseMeta>,
    norm_arena: Vec<u16>,
    word_postings: HashMap<Vec<u16>, Vec<u32>>,
}

impl VerseIndex {
    pub fn new(verses: Vec<VerseMeta>, arena: Vec<u16>) -> Self {
        let verse_count = verses.len();
        let mut norm_meta = Vec::with_capacity(verse_count);
        let mut norm_arena = Vec::with_capacity(arena.len());
        let mut word_postings: HashMap<Vec<u16>, Vec<u32>> =
            HashMap::with_capacity(verse_count.saturating_mul(3).min(20_000));
        let mut cur_tok: Vec<u16> = Vec::with_capacity(24);

        for (vidx, meta) in verses.iter().enumerate() {
            let start = meta.offset as usize;
            let end = start.saturating_add(meta.len as usize).min(arena.len());
            let raw_slice = &arena[start..end];

            let norm_start = norm_arena.len();
            let mut prev_space = true;
            cur_tok.clear();

            for &ch in raw_slice {
                if is_arabic_ignorable(ch) {
                    continue;
                }
                let folded = fold_arabic_letter(ch);
                if is_arabic_letter(folded) {
                    norm_arena.push(folded);
                    cur_tok.push(folded);
                    prev_space = false;
                } else {
                    if !cur_tok.is_empty() {
                        Self::record_posting(&mut word_postings, &cur_tok, vidx as u32);
                        cur_tok.clear();
                    }
                    if !prev_space && norm_arena.len() > norm_start {
                        norm_arena.push(CH_SPACE);
                        prev_space = true;
                    }
                }
            }
            if !cur_tok.is_empty() {
                Self::record_posting(&mut word_postings, &cur_tok, vidx as u32);
                cur_tok.clear();
            }
            if norm_arena.len() > norm_start && norm_arena.last() == Some(&CH_SPACE) {
                norm_arena.pop();
            }
            let norm_end = norm_arena.len();
            let norm_slice = &norm_arena[norm_start..norm_end];
            let norm_mask = compute_verse_letter_mask(norm_slice);
            norm_meta.push(NormVerseMeta {
                offset: norm_start as u32,
                len: (norm_end - norm_start) as u32,
                letter_mask: norm_mask,
            });
        }

        Self {
            verses,
            arena,
            norm_meta,
            norm_arena,
            word_postings,
        }
    }

    #[inline]
    fn record_posting(map: &mut HashMap<Vec<u16>, Vec<u32>>, token: &[u16], vidx: u32) {
        if let Some(list) = map.get_mut(token) {
            if list.last().copied() != Some(vidx) {
                list.push(vidx);
            }
        } else {
            map.insert(token.to_vec(), vec![vidx]);
        }
    }

    /// Builds a `VerseIndex` from Rust slices/strings (used in tests and benchmarks).
    pub fn from_utf8_rows(rows: &[(i32, i32, i32, &str)]) -> Result<Self, &'static str> {
        if rows.len() > MAX_VERSES {
            return Err("verse count exceeds MAX_VERSES");
        }
        let mut verses = Vec::with_capacity(rows.len());
        let mut arena = Vec::with_capacity(rows.len().saturating_mul(68));
        for &(row_id, sura, ayah, text) in rows {
            let offset = u32::try_from(arena.len()).map_err(|_| "arena offset overflow")?;
            arena.extend(text.encode_utf16());
            let new_len = arena.len();
            if new_len > MAX_ARENA_CODE_UNITS {
                return Err("arena exceeds MAX_ARENA_CODE_UNITS");
            }
            let offset_usize = offset as usize;
            let len = u32::try_from(new_len - offset_usize).map_err(|_| "verse length overflow")?;
            let letter_mask = compute_verse_letter_mask(&arena[offset_usize..new_len]);
            verses.push(VerseMeta { row_id, sura, ayah, offset, len, letter_mask });
        }
        Ok(Self::new(verses, arena))
    }

    #[inline]
    pub fn len(&self) -> usize {
        self.verses.len()
    }

    #[inline]
    pub fn is_empty(&self) -> bool {
        self.verses.is_empty()
    }

    #[inline]
    pub fn arena_code_units(&self) -> usize {
        self.arena.len()
    }

    #[inline]
    pub fn verse_slice(&self, verse_index: usize) -> Option<&[u16]> {
        let meta = self.verses.get(verse_index)?;
        let start = meta.offset as usize;
        let end = start.checked_add(meta.len as usize)?;
        self.arena.get(start..end)
    }

    #[inline]
    fn norm_slice(&self, verse_index: usize) -> &[u16] {
        let meta = self.norm_meta[verse_index];
        let start = meta.offset as usize;
        let end = start + meta.len as usize;
        &self.norm_arena[start..end]
    }

    fn collect_tier_verses(&self, words: &HashSet<Vec<u16>>, seen: &mut [u64]) -> Vec<u32> {
        let mut out = Vec::new();
        for w in words {
            if let Some(postings) = self.word_postings.get(w) {
                for &vidx in postings {
                    let word_i = (vidx as usize) >> 6;
                    let bit = 1u64 << ((vidx as usize) & 63);
                    if (seen[word_i] & bit) == 0 {
                        seen[word_i] |= bit;
                        out.push(vidx);
                    }
                }
            }
        }
        out.sort_unstable();
        out
    }

    fn fill_word_bitset(&self, wm: &WordMatcher, bits: &mut [u64]) {
        for set in [&wm.tier1, &wm.tier2, &wm.tier3] {
            for w in set {
                if let Some(postings) = self.word_postings.get(w) {
                    for &vidx in postings {
                        let idx = vidx as usize;
                        bits[idx >> 6] |= 1u64 << (idx & 63);
                    }
                }
            }
        }
    }

    fn scan_norm_substring(&self, needle: &[u16], prefer_word_prefix: bool) -> Vec<u32> {
        if needle.is_empty() {
            return Vec::new();
        }
        let mut prefix_hits = Vec::new();
        let mut substr_hits = Vec::new();
        for vidx in 0..self.verses.len() {
            let slice = self.norm_slice(vidx);
            if let Some(pos) = find_norm_substr_first(slice, needle, 0) {
                if prefer_word_prefix && (pos == 0 || slice[pos - 1] == CH_SPACE) {
                    prefix_hits.push(vidx as u32);
                } else {
                    substr_hits.push(vidx as u32);
                }
            }
        }
        if prefer_word_prefix {
            prefix_hits.extend_from_slice(&substr_hits);
            prefix_hits
        } else {
            substr_hits
        }
    }

    fn pack_indices(&self, indices: &[u32], max_results: usize) -> Vec<i32> {
        let count = indices.len().min(max_results);
        let mut results = Vec::with_capacity(count.saturating_mul(MATCH_STRIDE));
        for &vidx in &indices[..count] {
            let idx = vidx as usize;
            let meta = self.verses[idx];
            let Ok(idx_i32) = i32::try_from(idx) else {
                break;
            };
            results.push(idx_i32);
            results.push(meta.row_id);
            results.push(meta.sura);
            results.push(meta.ayah);
        }
        results
    }

    /// Searches all indexed verses for `query`, returning up to `max_results` ranked matches
    /// packed as `[verse_index, row_id, sura, ayah, ...]`.
    pub fn search(&self, query: &CompiledArabicQuery, max_results: usize) -> Vec<i32> {
        if max_results == 0 || query.is_empty() || self.verses.is_empty() {
            return Vec::new();
        }

        let n = self.verses.len();
        let bitset_words = (n + 63) / 64;

        match &query.mode {
            QueryMode::Empty => Vec::new(),

            QueryMode::Literal(lit) => {
                let mut results = Vec::with_capacity(max_results.min(2048).saturating_mul(MATCH_STRIDE));
                let mut hits = 0usize;
                let qmask = lit.query_mask;
                for (idx, meta) in self.verses.iter().enumerate() {
                    if (meta.letter_mask & qmask) != qmask {
                        continue;
                    }
                    let start = meta.offset as usize;
                    let Some(end) = start.checked_add(meta.len as usize) else {
                        continue;
                    };
                    let Some(slice) = self.arena.get(start..end) else {
                        continue;
                    };
                    if lit.find_first(slice, 0).is_some() {
                        let Ok(idx_i32) = i32::try_from(idx) else {
                            break;
                        };
                        results.push(idx_i32);
                        results.push(meta.row_id);
                        results.push(meta.sura);
                        results.push(meta.ayah);
                        hits += 1;
                        if hits >= max_results {
                            break;
                        }
                    }
                }
                results
            }

            QueryMode::ShortArabic { needle, literal_fallback } => {
                let qmask = literal_fallback.query_mask;
                let mut results = Vec::with_capacity(max_results.min(2048).saturating_mul(MATCH_STRIDE));
                let mut hits = 0usize;
                for vidx in 0..n {
                    let nmeta = self.norm_meta[vidx];
                    if (nmeta.letter_mask & qmask) != qmask {
                        continue;
                    }
                    let slice = self.norm_slice(vidx);
                    if find_norm_substr_first(slice, needle, 0).is_some() {
                        let meta = self.verses[vidx];
                        let Ok(idx_i32) = i32::try_from(vidx) else {
                            break;
                        };
                        results.push(idx_i32);
                        results.push(meta.row_id);
                        results.push(meta.sura);
                        results.push(meta.ayah);
                        hits += 1;
                        if hits >= max_results {
                            break;
                        }
                    }
                }
                results
            }

            QueryMode::SingleWord { matcher, fallback_needle } => {
                let mut seen = vec![0u64; bitset_words];
                let t1 = self.collect_tier_verses(&matcher.tier1, &mut seen);
                let t2 = self.collect_tier_verses(&matcher.tier2, &mut seen);
                let t3 = self.collect_tier_verses(&matcher.tier3, &mut seen);

                let total = t1.len() + t2.len() + t3.len();
                if total > 0 {
                    let mut combined = Vec::with_capacity(total.min(max_results));
                    for slice in [&t1, &t2, &t3] {
                        let rem = max_results.saturating_sub(combined.len());
                        if rem == 0 {
                            break;
                        }
                        combined.extend_from_slice(&slice[..slice.len().min(rem)]);
                    }
                    return self.pack_indices(&combined, max_results);
                }

                // Mid-typing prefix / partial-word fallback (zero hits at word level)
                let fallback = self.scan_norm_substring(fallback_needle, true);
                self.pack_indices(&fallback, max_results)
            }

            QueryMode::MultiWord { words, norm_phrase } => {
                let k = words.len();
                let mut common = vec![0u64; bitset_words];
                self.fill_word_bitset(&words[0], &mut common);
                let mut scratch = vec![0u64; bitset_words];
                for wm in &words[1..] {
                    scratch.fill(0);
                    self.fill_word_bitset(wm, &mut scratch);
                    for i in 0..bitset_words {
                        common[i] &= scratch[i];
                    }
                }

                let mut t1_hits = Vec::new();
                let mut t2_hits = Vec::new();
                let mut topical_hits = Vec::new();

                for (word_i, &bits_val) in common.iter().enumerate() {
                    let mut b = bits_val;
                    while b != 0 {
                        let tz = b.trailing_zeros() as usize;
                        b &= b - 1;
                        let vidx = (word_i << 6) + tz;
                        if vidx >= n {
                            break;
                        }
                        let norm_slice = self.norm_slice(vidx);
                        let verse_words: Vec<&[u16]> = norm_slice
                            .split(|&c| c == CH_SPACE)
                            .filter(|s| !s.is_empty())
                            .collect();

                        let mut best_contiguous = 0u8;
                        if verse_words.len() >= k {
                            for start in 0..=verse_words.len() - k {
                                let mut worst_tier = 1u8;
                                let mut ok = true;
                                for j in 0..k {
                                    let mt = words[j].match_tier(verse_words[start + j]);
                                    if mt == 0 {
                                        ok = false;
                                        break;
                                    }
                                    if mt > worst_tier {
                                        worst_tier = mt;
                                    }
                                }
                                if ok {
                                    if best_contiguous == 0 || worst_tier < best_contiguous {
                                        best_contiguous = worst_tier;
                                    }
                                    if best_contiguous == 1 {
                                        break;
                                    }
                                }
                            }
                        }

                        match best_contiguous {
                            1 => t1_hits.push(vidx as u32),
                            2 | 3 => t2_hits.push(vidx as u32),
                            _ => topical_hits.push(vidx as u32),
                        }
                    }
                }

                let total = t1_hits.len() + t2_hits.len() + topical_hits.len();
                if total > 0 {
                    let mut combined = Vec::with_capacity(total.min(max_results));
                    for slice in [&t1_hits, &t2_hits, &topical_hits] {
                        let rem = max_results.saturating_sub(combined.len());
                        if rem == 0 {
                            break;
                        }
                        combined.extend_from_slice(&slice[..slice.len().min(rem)]);
                    }
                    return self.pack_indices(&combined, max_results);
                }

                // Mid-typing last word fallback
                let fallback = self.scan_norm_substring(norm_phrase, true);
                self.pack_indices(&fallback, max_results)
            }
        }
    }
}
