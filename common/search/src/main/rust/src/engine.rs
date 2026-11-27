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
use std::sync::OnceLock;

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
const CH_ALEF_MADDA: u16 = 0x0622; // آ
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

const SAFE_WA_BA_PROCLITICS: &[&[u16]] = &[
    &[],
    &[CH_WAW],
    &[CH_BA],
    &[CH_WAW, CH_BA],
    &[CH_FA, CH_BA],
];

const SAFE_NO_KAF_PROCLITICS: &[&[u16]] = &[
    &[],
    &[CH_WAW],
    &[CH_FA],
    &[CH_BA],
    &[CH_LAM],
    &[CH_WAW, CH_BA],
    &[CH_FA, CH_BA],
    &[CH_WAW, CH_LAM],
    &[CH_FA, CH_LAM],
];

const SAFE_WA_LAM_PROCLITICS: &[&[u16]] = &[&[], &[CH_WAW], &[CH_LAM]];
const SAFE_WA_BA_LAM_PROCLITICS: &[&[u16]] = &[&[], &[CH_WAW], &[CH_BA], &[CH_LAM]];

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

const MOTHER_PRONOUNS: &[&[u16]] = &[
    &[CH_HA],
    &[CH_HA, CH_ALEF],
    &[CH_HA, CH_MIM],
    &[CH_HA, CH_MIM, CH_ALEF],
    &[CH_HA, CH_NUN],
    &[CH_KAF],
    &[CH_KAF, CH_MIM],
    &[CH_KAF, CH_MIM, CH_ALEF],
];

const WORD_ALLAH: &[u16] = &[CH_ALEF, CH_LAM, CH_LAM, CH_HA];
const WORD_ALLAH_TA: &[u16] = &[CH_ALEF, CH_LAM, CH_LAM, CH_TA_MARBUTA];
const WORD_LILLAH: &[u16] = &[CH_LAM, CH_LAM, CH_HA];
const WORD_ALLAHUMMA: &[u16] = &[CH_ALEF, CH_LAM, CH_LAM, CH_HA, CH_MIM];
const WORD_ABDULLAH: &[u16] = &[CH_AIN, CH_BA, CH_DAL, CH_ALEF, CH_LAM, CH_LAM, CH_HA];
const WORD_RAHMAN: &[u16] = &[CH_RA, 0x062D, CH_MIM, CH_NUN]; // رحمن

// ================================================================================================
// Curated Quranic Broken-Plural, Irregular-Lemma & Precision Guardrail Lexicon
// ================================================================================================

const CARDINAL_NUMBERS_RAW: &[&str] = &[
    "ثلاث", "اربع", "خمس", "ست", "سبع", "ثمان", "ثماني", "تسع", "عشر",
    "عشرون", "عشرين", "ثلاثون", "ثلاثين", "اربعون", "اربعين",
    "خمسون", "خمسين", "ستون", "ستين", "سبعون", "سبعين",
    "ثمانون", "ثمانين", "تسعون", "تسعين", "اثنين", "اثنتين", "مايتين",
];

const NON_PLURAL_WN_YN_RAW: &[&str] = &[
    "فرعون", "هارون", "قارون", "زيتون", "عرجون", "ماعون",
    "مجنون", "مكنون", "مسنون", "ممنون", "مفتون", "مشحون",
    "موزون", "مرهون", "مسجون", "مديون", "طاعون", "صهيون", "موتون",
    "مسكين", "شياطين", "مساكين", "مداين", "موازين", "مستبين",
    "عليين", "سجين", "غسلين", "يقطين", "بساتين", "فلسطين", "سلاطين", "ملايين",
];

const NON_PLURAL_AT_RAW: &[&str] = &[
    "مات", "فات", "بات", "يات", "ذات", "نبات", "ثبات", "سبات", "شتات",
    "فرات", "رفات", "ممات", "ميقات", "حيات", "امرات", "مرضات", "صلات",
    "زكات", "غدات", "مشكات", "نجات", "منات", "تورات", "هيهات", "لات",
    "عرفات", "اصوات", "اموات", "اقوات", "اوقات", "انبات", "افات", "ابيات", "بيات",
];

const NON_DUAL_TAN_TIN_RAW: &[&str] = &[
    "بهتان", "متين", "وتين", "تين", "ستين", "مايتين", "اثنتين", "هاتين",
    "ياتين", "اتين", "يوتين", "لاوتين", "قانتين", "مخبتين", "ميتين", "اختين",
];

const NO_FEM_EXPANSION_RAW: &[&str] = &[
    "كفار", "فجار", "رحمن", "سلطان", "قران", "شيطان", "انسان", "ميزان",
    "ايمان", "سليمان", "فرعون", "هارون", "قارون",
];

const NO_DUAL_AN_EXPANSION_RAW: &[&str] = &[
    "سليم", "رحمن", "قران", "شيطان", "انسان", "ميزان", "ايمان", "سلطان", "مستعان",
];

const NON_DUAL_AN_RAW: &[&str] = &[
    "سليمان", "مستعان", "سلطان", "سبحان", "رمضان", "قربان", "بنيان", "طغيان",
    "عصيان", "غفران", "كفران", "شكران", "حسبان", "نسيان", "عمران", "فرقان",
    "عدوان", "احسان", "خسران", "مرجان", "طوفان", "ريحان", "حيوان", "شيطان",
    "انسان", "ايمان", "ميزان", "برهان", "بهتان",
];

const FEM_PARTICIPLE_EXCLUDE_RAW: &[&str] = &[
    "مدينة", "سفينة", "حديقة", "خليفة", "ملايكة", "مغفرة", "معذرة", "موعظة",
    "منزلة", "مفسدة", "مخلفة", "طايفة", "قارعة", "واقعة", "حاقة", "غاشية",
    "دابة", "مايدة", "ساعة", "كفارة", "عبادة", "قيامة", "شهادة", "رسالة",
    "تجارة", "عمارة", "ولاية", "هداية", "خيانة", "ضلالة", "جهالة", "سفاهة",
    "شفاعة", "جماعة", "نهاية", "حجارة", "فاحشة", "كبيرة", "بصيرة", "خبيثة",
    "طيبة", "مقبرة", "ناصية", "صاعقة", "دايرة", "قاعدة", "قلادة", "سقاية",
    "سريرة", "شريعة", "قبيلة", "صحيفة", "بهيمة", "زيتونة", "فاكهة", "سنبلة",
    "اريكة", "صومعة", "سلسلة", "نمرقة", "زربية", "مصيبة", "عقوبة", "امانة",
    "خطيية", "مسالة", "معصية", "مشيية", "مرحمة", "متربة", "مسغبة", "مقربة",
];

const ROOT_HA_WORDS_RAW: &[&str] = &[
    "وجه", "اله", "سفيه", "وجيه", "شبيه", "فقه", "شبه", "كره",
    "وجوه", "افواه", "شفاه", "فواكه", "اشباه", "نبيه", "نزيه",
];

const BROKEN_PLURAL_TA_MARBUTA_RAW: &[&str] = &[
    "ملايكة", "كفرة", "فجرة", "سحرة", "اخوة", "فتية", "سادة", "الهة",
    "حجارة", "السنة", "افيدة", "اسورة", "اعمدة", "اودية", "نسوة",
    "اشربة", "اطعمة", "امتعة", "ادوية", "البسة", "اتربة",
];

const NO_TANWIN_YA_RAW: &[&str] = &[
    "موتي", "قري", "مرضي", "قتلي", "يتامي", "اسري", "اساري", "نصاري",
    "انثي", "اعمي", "هوي", "فتي", "ايدي", "موالي", "نواصي", "ليالي", "زرابي",
    "بني", "يدي", "ابي", "اخي", "ابوي",
];

const MAQSURA_PRON_PLURALS_RAW: &[&str] = &[
    "موتي", "مرضي", "قتلي", "يتامي", "اسري", "اساري", "فتي", "نصاري", "هوي", "انثي",
];

const SAFE_3_REV_BASES_RAW: &[&str] = &[
    "نبي", "اخر", "اول", "اهل", "ضال", "مضل", "فرح", "ميت", "طيب",
    "رجل", "ملك", "عين", "زوج", "قرن", "بحر", "شهر", "عام", "قلب",
    "حزب", "عبد", "ذكر", "اجل", "اثم", "الف", "كفل", "ضعف", "حول",
];

const NO_PRON_FAMILY_MEMBERS_RAW: &[&str] = &[
    "فرق", "ظلل", "ورق", "نمل", "كلم", "قري",
];

/// Broken plurals ending in `ون`/`ين`/`ان` where final `ن` is lexical (NOT a sound plural/dual suffix),
/// so possessive pronouns attach directly (`موازينه`, `شياطينهم`, `بطونهم`, `آذانهم`, `ألوانكم`, `إخوانهم`).
const BROKEN_PLURAL_N_PRON_RAW: &[&str] = &[
    "موازين", "شياطين", "مساكين", "بطون", "عيون", "قرون", "ظنون", "سجون", "حصون",
    "اذان", "اذقان", "اوثان", "الوان", "اخوان", "نيران", "غلمان", "ولدان", "فتيان",
    "رهبان", "حيتان", "براهين", "عميان", "ذكران",
];

/// Special member prefixes inside `LEMMA_FAMILIES_RAW`:
/// - `=X`: definite-only (`ال`/`لل` + proclitics, e.g. `=انس` -> `الانس`/`والانس`, never bare verb `آنَسَ`).
/// - `@X`: fixed inflected token with safe proclitics (e.g. `@بيده`, `@قراها`, `@ابواه`, `@يدي`).
/// - `!X`: fixed inflected token with `""`/`و`/`ل` proclitics only (e.g. `!ابي`, never `فأبى`).
const LEMMA_FAMILIES_RAW: &[&[&str]] = &[
    // 1. Prophets, Angels, Worship, Faith
    &["نبي", "انبياء", "نبيون", "نبيين"],
    &["رسول", "رسل"],
    &["ملك", "ملايكة", "ملكان", "ملكين"],
    &["ملك", "ملوك"],
    &["اله", "الهة", "الهين"],
    &["رب", "ارباب"],
    &["عبد", "عباد", "عبيد", "عبدين"],
    &["صلاة", "صلوات"],
    &["زكاة", "زكوات"],
    &["نعمة", "نعمات"],
    &["رحمة", "رحمات"],
    &["سنة", "سنن"],
    &["سنة", "سنين", "سنوات"],
    &["مسجد", "مساجد"],
    &["صومعة", "صوامع"],
    &["صنم", "اصنام"],
    &["وثن", "اوثان"],
    &["شيطان", "شياطين"],
    &["طاغوت", "طواغيت"],
    &["نار", "نيران"],
    // 2. Scripture, Speech, Signs, Knowledge
    &["كتاب", "كتب"],
    &["صحيفة", "صحف"],
    &["سفر", "اسفار"],
    &["لوح", "الواح"],
    &["قلم", "اقلام"],
    &["اية", "ايات", "ايتين"],
    &["سورة", "سور"],
    &["كلمة", "كلمات", "كلم"],
    &["قول", "اقوال"],
    &["حديث", "احاديث"],
    &["نبا", "انباء"],
    &["مثل", "امثال"],
    &["حجة", "حجج"],
    &["برهان", "براهين"],
    &["حكم", "احكام"],
    &["امر", "امور"],
    &["شان", "شوون", "شءون"],
    &["اسم", "اسماء"],
    &["لسان", "السنة"],
    &["صوت", "اصوات"],
    &["حلم", "احلام"],
    &["سر", "اسرار"],
    &["غيب", "غيوب"],
    &["سريرة", "سراير"],
    &["شريعة", "شرايع"],
    &["ملة", "ملل"],
    &["عهد", "عهود"],
    &["ميثاق", "مواثيق"],
    // 3. Deeds, Virtues, Sins, Trials, Rewards
    &["عمل", "اعمال"],
    &["فعل", "افعال"],
    &["ذنب", "ذنوب"],
    &["خطيية", "خطايا", "خطييات"],
    &["اثم", "اثام"],
    &["سيية", "سييات"],
    &["حسنة", "حسنات"],
    &["فاحشة", "فواحش"],
    &["كبيرة", "كباير"],
    &["وزر", "اوزار"],
    &["فتنة", "فتن"],
    &["مرض", "امراض"],
    &["اجر", "اجور"],
    &["درجة", "درجات"],
    &["ميزان", "موازين"],
    &["حد", "حدود"],
    &["ظن", "ظنون"],
    &["هوي", "اهواء"],
    &["شهوة", "شهوات"],
    &["خير", "خيرات", "اخيار"],
    &["طيب", "طيبة", "طيبون", "طيبين", "طيبات"],
    &["خبيث", "خبيثة", "خبيثون", "خبيثين", "خبيثات", "خبايث"],
    &["ظلمة", "ظلمات"],
    &["نور", "انوار"],
    &["ضلال", "ضلالة"],
    &["مصيبة", "مصايب"],
    // 4. Human Beings, Kinship, Communities
    &["انسان", "=انس", "ناس", "اناس", "اناسي"],
    &["رجل", "رجال", "رجلان", "رجلين"],
    &["رجل", "ارجل"],
    &["امراة", "امرات", "نساء", "نسوة", "امراتان", "امراتين"],
    &["ابن", "ابناء", "بني", "بنو", "بنين"],
    &["بنت", "بنات"],
    &[
        "اب", "اباء", "ابوان", "ابوين", "@ابواه", "@ابويه", "@ابويكم", "@ابوي",
        "@ابوه", "@ابوهم", "@ابوك", "@ابوكم", "@ابونا", "@اباه", "@اباكم",
        "@ابيه", "@ابيهم", "@ابيك", "@ابيكم", "!ابي",
    ],
    &[
        "اخ", "اخوة", "اخوان", "اخوين", "@اخوه", "@اخوهم", "@اخوك", "@اخوكم",
        "@اخاه", "@اخاك", "@اخيه", "@اخيهم", "@اخيك", "!اخي",
    ],
    &["اخت", "اخوات", "اختان", "اختين"],
    &["ام", "امهات"],
    &["امة", "امم"],
    &["ولد", "اولاد", "ولدان"],
    &["ذرية", "ذريات"],
    &["زوج", "ازواج", "زوجان", "زوجين"],
    &["اهل", "اهلون", "اهلين"],
    &["قوم", "اقوام"],
    &["قبيلة", "قبايل"],
    &["شعب", "شعوب"],
    &["سبط", "اسباط"],
    &["نسب", "انساب"],
    &["رحم", "ارحام"],
    &["صاحب", "اصحاب", "صاحبة"],
    &["عدو", "اعداء"],
    &["ولي", "اولياء", "موالي"],
    &["صديق", "اصدقاء"],
    &["خليل", "اخلاء"],
    &["شريك", "شركاء"],
    &["قرين", "قرناء"],
    &["خليط", "خلطاء"],
    &["نصير", "انصار"],
    &["شهيد", "شهداء"],
    &["شاهد", "اشهاد", "شهداء"],
    &["شفيع", "شفعاء"],
    &["خليفة", "خلفاء", "خلايف"],
    &["حبر", "احبار"],
    &["راهب", "رهبان"],
    &["حواري", "حواريون", "حواريين"],
    &["رباني", "ربانيون", "ربانيين"],
    &["عربي", "عرب", "اعراب"],
    &["يهودي", "يهود"],
    &["نصراني", "نصاري"],
    // 5. Human Conditions, Roles, Traits
    &["نفس", "انفس", "نفوس"],
    &["روح", "ارواح"],
    &["حي", "احياء"],
    &["ميت", "موتي", "اموات", "ميتون", "ميتين", "ميتة"],
    &["قتيل", "قتلي"],
    &["مريض", "مرضي"],
    &["فقير", "فقراء"],
    &["غني", "اغنياء"],
    &["مسكين", "مساكين"],
    &["يتيم", "يتامي"],
    &["اسير", "اسري", "اساري"],
    &["ضعيف", "ضعفاء", "ضعاف"],
    &["قوي", "اقوياء"],
    &["شديد", "اشداء", "شداد"],
    &["كبير", "كبار", "كبراء", "كبيرة", "كباير"],
    &["صغير", "صغار", "صغيرة"],
    &["عظيم", "عظيمة"],
    &["كريم", "كرام", "كريمة"],
    &["سفيه", "سفهاء"],
    &["عالم", "علماء"],
    &["شاعر", "شعراء"],
    &["كافر", "كفار", "كفرة"],
    &["فاجر", "فجار", "فجرة"],
    &["ساحر", "سحرة"],
    &["غلام", "غلمان"],
    &["فتي", "فتية", "فتيان"],
    &["شيخ", "شيوخ"],
    &["طفل", "اطفال"],
    &["بكر", "ابكار"],
    &["ذكر", "ذكور", "ذكران", "ذكرين"],
    &["انثي", "اناث", "انثيين"],
    &["اعمي", "عمي", "عميان"],
    &["اصم", "صم"],
    &["حنيف", "حنفاء"],
    &["سيد", "سادة"],
    &["جند", "جنود"],
    &["حزب", "احزاب", "حزبان", "حزبين"],
    &["طايفة", "طوايف", "طايفتان", "طايفتين"],
    &["فية", "فيات", "فيتان", "فيتين"],
    &["فريق", "فرق", "فريقان", "فريقين"],
    &["اول", "اولون", "اولين", "اوايل"],
    &["اخر", "اخرون", "اخرين", "اخران"],
    &["ضال", "ضالون", "ضالين"],
    &["مضل", "مضلون", "مضلين"],
    &["تواب", "توابون", "توابين"],
    // 6. Human Body
    &["قلب", "قلوب", "قلبين"],
    &["فواد", "افيدة"],
    &["صدر", "صدور"],
    &["وجه", "وجوه"],
    &["راس", "رءوس", "رووس", "روس"],
    &["ناصية", "نواصي"],
    &["عين", "اعين", "عيون", "عينان", "عينين"],
    &["بصر", "ابصار"],
    &["اذن", "اذان"],
    &["سمع", "اسماع"],
    &["فم", "افواه"],
    &["شفة", "شفاه", "شفتين"],
    &["ذقن", "اذقان"],
    &["عنق", "اعناق"],
    &["رقبة", "رقاب"],
    &["يد", "ايدي", "يدان", "يدين", "@يدي", "@يديه", "@يديك", "@يداه", "@يداك", "@يدا"],
    &["شمال", "شمايل"],
    &["اصبع", "اصابع"],
    &["كعب", "كعبين", "كعاب"],
    &["قدم", "اقدام"],
    &["بطن", "بطون"],
    &["ظهر", "ظهور"],
    &["دبر", "ادبار"],
    &["جنب", "جنوب"],
    &["عظم", "عظام"],
    &["جلد", "جلود"],
    &["دم", "دماء"],
    &["لحم", "لحوم"],
    &["جرح", "جروح"],
    &["فرج", "فروج"],
    // 7. Cosmos, Nature, Time, Flora, Fauna
    &["سماء", "سماوات", "سموات"],
    &["ارض", "ارضين", "اراض"],
    &["يوم", "ايام", "يومان", "يومين"],
    &["ليل", "ليلة", "ليالي", "ليال"],
    &["وقت", "اوقات"],
    &["شهر", "اشهر", "شهور", "شهران", "شهرين"],
    &["عام", "اعوام", "عامان", "عامين"],
    &["حول", "حولين", "احوال"],
    &["قرن", "قرون", "قرنان", "قرنين"],
    &["نجم", "نجوم"],
    &["كوكب", "كواكب"],
    &["برج", "بروج"],
    &["افق", "افاق"],
    &["مشرق", "مشارق", "مشرقين"],
    &["مغرب", "مغارب", "مغربين"],
    &["ريح", "رياح"],
    &["سحاب", "سحابة", "سحب"],
    &["صاعقة", "صواعق"],
    &["ظل", "ظلال", "ظلل"],
    &["نهر", "انهار"],
    &["بحر", "بحار", "ابحر", "بحران", "بحرين"],
    &["موج", "امواج"],
    &["جبل", "جبال"],
    &["حجر", "حجارة", "احجار"],
    &["صخرة", "صخر", "صخور"],
    &["قطر", "اقطار"],
    &["واد", "وادي", "اودية"],
    &["سبيل", "سبل"],
    &["طريق", "طرق", "طرايق"],
    &["شجرة", "شجر", "اشجار"],
    &["نخلة", "نخل", "نخيل"],
    &["ثمرة", "ثمر", "ثمرات", "ثمار"],
    &["فاكهة", "فواكه"],
    &["عنب", "اعناب"],
    &["سنبلة", "سنابل", "سنبلات"],
    &["حبة", "حبات", "حبوب"],
    &["ورقة", "ورق", "اوراق"],
    &["حديقة", "حدايق"],
    &["روضة", "روضات", "رياض"],
    &["سدرة", "سدر"],
    &["زيتونة", "زيتون"],
    &["دابة", "دواب"],
    &["بهيمة", "بهايم"],
    &["وحش", "وحوش"],
    &["طير", "طيور", "طاير"],
    &["نملة", "نمل"],
    &["نحلة", "نحل"],
    &["حوت", "حيتان"],
    &["كلب", "كلاب"],
    &["حمار", "حمير"],
    &["بغل", "بغال"],
    &["ناقة", "نوق"],
    &["بقرة", "بقر", "بقرات"],
    // 8. Places, Buildings, Possessions, Objects
    &["بيت", "بيوت"],
    &["دار", "ديار"],
    &["مدينة", "مداين", "مدن"],
    &["قرية", "قري", "قريتين", "@قراها"],
    &["بلد", "بلدة", "بلاد"],
    &["سوق", "اسواق"],
    &["قصر", "قصور"],
    &["حصن", "حصون"],
    &["غرفة", "غرف", "غرفات"],
    &["منزل", "منازل"],
    &["مجلس", "مجالس"],
    &["مضجع", "مضاجع"],
    &["معرج", "معارج"],
    &["قبر", "قبور"],
    &["جدث", "اجداث"],
    &["مقبرة", "مقابر"],
    &["باب", "ابواب"],
    &["جدار", "جدر"],
    &["قاعدة", "قواعد"],
    &["عمود", "عمد", "اعمدة"],
    &["وتد", "اوتاد"],
    &["خيمة", "خيام"],
    &["سجن", "سجون"],
    &["سفينة", "سفن"],
    &["مال", "اموال"],
    &["كنز", "كنوز"],
    &["ثوب", "ثياب"],
    &["سوار", "اساور", "اسورة"],
    &["قلادة", "قلايد"],
    &["كوب", "اكواب"],
    &["ابريق", "اباريق"],
    &["سرير", "سرر"],
    &["اريكة", "ارايك"],
    &["نمرقة", "نمارق"],
    &["زربية", "زرابي"],
    &["مفتاح", "مفاتح", "مفاتيح"],
    &["قفل", "اقفال"],
    &["سلسلة", "سلاسل"],
    &["غل", "اغلال"],
    &["صفد", "اصفاد"],
    &["نفل", "انفال"],
    &["شيء", "اشياء"],
    &["سبب", "اسباب"],
    &["مرفق", "مرافق"],
    &["دايرة", "دواير"],
    &["حمل", "احمال"],
    &["عقد", "عقود"],
    &["اثر", "اثار"],
    &["اجل", "اجال", "اجلين"],
    &["اصل", "اصول"],
    &["فرع", "فروع"],
    &["جزء", "اجزاء"],
    &["لون", "الوان"],
    &["مكان", "اماكن"],
    &["موضع", "مواضع"],
    &["موطن", "مواطن"],
    &["مشعر", "مشاعر"],
    &["منسك", "مناسك"],
    &["قوت", "اقوات"],
    &["رزق", "ارزاق"],
];

#[derive(Clone, Copy, PartialEq, Eq)]
enum FamilyMemberKind {
    Normal,
    DefOnly,
    FixedSafe,
    FixedWaLam,
}

struct FamilyMember {
    kind: FamilyMemberKind,
    text: Vec<u16>,
}

struct MorphLexicon {
    cardinal_numbers: HashSet<Vec<u16>>,
    non_plural_wn_yn: HashSet<Vec<u16>>,
    non_plural_at: HashSet<Vec<u16>>,
    non_dual_tan_tin: HashSet<Vec<u16>>,
    no_fem_expansion: HashSet<Vec<u16>>,
    no_dual_an_expansion: HashSet<Vec<u16>>,
    non_dual_an: HashSet<Vec<u16>>,
    fem_participle_exclude: HashSet<Vec<u16>>,
    root_ha_words: HashSet<Vec<u16>>,
    broken_plural_ta_marbuta: HashSet<Vec<u16>>,
    no_tanwin_ya: HashSet<Vec<u16>>,
    maqsura_pron_plurals: HashSet<Vec<u16>>,
    safe_3_rev_bases: HashSet<Vec<u16>>,
    no_pron_family_members: HashSet<Vec<u16>>,
    broken_plural_n_pron: HashSet<Vec<u16>>,
    families: Vec<Vec<FamilyMember>>,
    family_index: HashMap<Vec<u16>, Vec<usize>>,
}

impl MorphLexicon {
    fn build() -> Self {
        fn build_set(raw: &[&str]) -> HashSet<Vec<u16>> {
            let mut set = HashSet::with_capacity(raw.len() * 2);
            for &s in raw {
                set.insert(s.encode_utf16().collect());
            }
            set
        }

        let mut families = Vec::with_capacity(LEMMA_FAMILIES_RAW.len());
        let mut family_index: HashMap<Vec<u16>, Vec<usize>> =
            HashMap::with_capacity(LEMMA_FAMILIES_RAW.len() * 3);

        for (fam_idx, &raw_fam) in LEMMA_FAMILIES_RAW.iter().enumerate() {
            let mut members = Vec::with_capacity(raw_fam.len());
            for &entry in raw_fam {
                let (kind, rest) = if let Some(r) = entry.strip_prefix('=') {
                    (FamilyMemberKind::DefOnly, r)
                } else if let Some(r) = entry.strip_prefix('@') {
                    (FamilyMemberKind::FixedSafe, r)
                } else if let Some(r) = entry.strip_prefix('!') {
                    (FamilyMemberKind::FixedWaLam, r)
                } else {
                    (FamilyMemberKind::Normal, entry)
                };
                let text: Vec<u16> = rest.encode_utf16().collect();
                let list = family_index.entry(text.clone()).or_default();
                if list.last().copied() != Some(fam_idx) {
                    list.push(fam_idx);
                }
                members.push(FamilyMember { kind, text });
            }
            families.push(members);
        }

        Self {
            cardinal_numbers: build_set(CARDINAL_NUMBERS_RAW),
            non_plural_wn_yn: build_set(NON_PLURAL_WN_YN_RAW),
            non_plural_at: build_set(NON_PLURAL_AT_RAW),
            non_dual_tan_tin: build_set(NON_DUAL_TAN_TIN_RAW),
            no_fem_expansion: build_set(NO_FEM_EXPANSION_RAW),
            no_dual_an_expansion: build_set(NO_DUAL_AN_EXPANSION_RAW),
            non_dual_an: build_set(NON_DUAL_AN_RAW),
            fem_participle_exclude: build_set(FEM_PARTICIPLE_EXCLUDE_RAW),
            root_ha_words: build_set(ROOT_HA_WORDS_RAW),
            broken_plural_ta_marbuta: build_set(BROKEN_PLURAL_TA_MARBUTA_RAW),
            no_tanwin_ya: build_set(NO_TANWIN_YA_RAW),
            maqsura_pron_plurals: build_set(MAQSURA_PRON_PLURALS_RAW),
            safe_3_rev_bases: build_set(SAFE_3_REV_BASES_RAW),
            no_pron_family_members: build_set(NO_PRON_FAMILY_MEMBERS_RAW),
            broken_plural_n_pron: build_set(BROKEN_PLURAL_N_PRON_RAW),
            families,
            family_index,
        }
    }
}

static MORPH_LEXICON: OnceLock<MorphLexicon> = OnceLock::new();

#[inline]
fn lexicon() -> &'static MorphLexicon {
    MORPH_LEXICON.get_or_init(MorphLexicon::build)
}

#[inline]
fn concat_slices(parts: &[&[u16]]) -> Vec<u16> {
    let total: usize = parts.iter().map(|p| p.len()).sum();
    let mut out = Vec::with_capacity(total);
    for p in parts {
        out.extend_from_slice(p);
    }
    out
}

fn add_with_proclitics(set: &mut HashSet<Vec<u16>>, stem: &[u16], proclitics: &[&[u16]]) {
    for &p in proclitics {
        set.insert(concat_slices(&[p, stem]));
    }
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

#[inline]
fn starts_with_slice(hay: &[u16], pre: &[u16]) -> bool {
    hay.len() >= pre.len() && &hay[..pre.len()] == pre
}

const WORD_YAD: &[u16] = &[CH_YA, CH_DAL]; // يد
const WORD_RABB: &[u16] = &[CH_RA, CH_BA]; // رب
const WORD_UMM: &[u16] = &[CH_ALEF, CH_MIM]; // ام
const WORD_DAM: &[u16] = &[CH_DAL, CH_MIM]; // دم
const WORD_AKH: &[u16] = &[CH_ALEF, 0x062E]; // اخ
const WORD_SUMM: &[u16] = &[0x0635, CH_MIM]; // صم
const WORD_HAYY: &[u16] = &[0x062D, CH_YA]; // حي
const WORD_KUTUB: &[u16] = &[CH_KAF, CH_TA, CH_BA]; // كتب
const WORD_KALIM: &[u16] = &[CH_KAF, CH_LAM, CH_MIM]; // كلم
const WORD_QURA: &[u16] = &[0x0642, CH_RA, CH_YA]; // قري
const WORD_BIN: &[u16] = &[CH_BA, CH_NUN]; // بن
const WORD_SAMAW: &[u16] = &[CH_SIN, CH_MIM, CH_ALEF, CH_WAW]; // سماو
const WORD_AALA: &[u16] = &[CH_ALEF, CH_AIN, CH_LAM, CH_YA]; // اعلي
const WORD_AAL: &[u16] = &[CH_ALEF, CH_AIN, CH_LAM]; // اعل
const WORD_MUSA: &[u16] = &[CH_MIM, CH_WAW, CH_SIN, CH_YA]; // موسي
const WORD_FARIQ: &[u16] = &[CH_FA, CH_RA, CH_YA, 0x0642]; // فريق
const WORD_ALAMIN: &[u16] = &[CH_AIN, CH_ALEF, CH_LAM, CH_MIM, CH_YA, CH_NUN]; // عالمين
const WORD_ALAMUN: &[u16] = &[CH_AIN, CH_ALEF, CH_LAM, CH_MIM, CH_WAW, CH_NUN]; // عالمون

fn add_pronoun_forms(
    set: &mut HashSet<Vec<u16>>,
    stem: &[u16],
    proclitics: &[&[u16]],
    prons: &[&[u16]],
    skip_na: bool,
    lex: &MorphLexicon,
) {
    for &pron in prons {
        if skip_na && pron == [CH_NUN, CH_ALEF] {
            continue;
        }
        let form = concat_slices(&[stem, pron]);
        add_with_proclitics(set, &form, proclitics);
    }
    // Hamza-final اء seat alternation before pronouns: آباءهم / آباؤهم (و) / آبائهم (ي)
    if stem.len() >= 4 && ends_with_slice(stem, &[CH_ALEF, CH_HAMZA]) {
        let base = &stem[..stem.len() - 1];
        for &pron in prons {
            let f_waw = concat_slices(&[base, &[CH_WAW], pron]);
            let f_ya = concat_slices(&[base, &[CH_YA], pron]);
            add_with_proclitics(set, &f_waw, proclitics);
            add_with_proclitics(set, &f_ya, proclitics);
        }
    }
    // Alef-maqsura plurals (ى -> ا before pronoun: موتاهم / يتاماهم / أسراهم / فتاه)
    if lex.maqsura_pron_plurals.contains(stem) {
        let base = &stem[..stem.len() - 1];
        for &pron in prons {
            let form = concat_slices(&[base, &[CH_ALEF], pron]);
            add_with_proclitics(set, &form, proclitics);
        }
    }
}

fn add_fem_paradigm(
    set: &mut HashSet<Vec<u16>>,
    base: &[u16],
    include_singular: bool,
    include_dual_plural: bool,
) {
    if include_singular {
        for &e in &[CH_TA_MARBUTA, CH_TA] {
            let form = concat_slices(&[base, &[e]]);
            add_definite_forms(set, &form);
            add_indefinite_forms(set, &form, false);
        }
        for &pron in PRONOUNS {
            let form = concat_slices(&[base, &[CH_TA], pron]);
            add_indefinite_forms(set, &form, false);
        }
    }
    if include_dual_plural {
        for dual_end in &[&[CH_TA, CH_ALEF, CH_NUN][..], &[CH_TA, CH_YA, CH_NUN][..]] {
            let form = concat_slices(&[base, dual_end]);
            add_definite_forms(set, &form);
            add_indefinite_forms(set, &form, false);
        }
        for dual_c in &[&[CH_TA, CH_ALEF][..], &[CH_TA, CH_YA][..]] {
            for &pron in PRONOUNS {
                let form = concat_slices(&[base, dual_c, pron]);
                add_indefinite_forms(set, &form, false);
            }
        }
        // Guard base == "ام": أمة takes broken plural أمم, NEVER أمات/أماته ("caused to die")!
        if base != WORD_UMM {
            let mut plurals = Vec::with_capacity(2);
            plurals.push(concat_slices(&[base, &[CH_ALEF, CH_TA]]));
            if base.len() >= 3 && base.last() == Some(&CH_ALEF) {
                plurals.push(concat_slices(&[&base[..base.len() - 1], &[CH_WAW, CH_ALEF, CH_TA]]));
            }
            for pl in &plurals {
                add_definite_forms(set, pl);
                add_indefinite_forms(set, pl, false);
                for &pron in PRONOUNS {
                    let form = concat_slices(&[pl, pron]);
                    add_indefinite_forms(set, &form, false);
                }
            }
        }
    }
}

fn add_masculine_inflections(
    t2: &mut HashSet<Vec<u16>>,
    t3: &mut HashSet<Vec<u16>>,
    stem: &[u16],
    is_participle_shape: bool,
    lex: &MorphLexicon,
) {
    if !lex.no_fem_expansion.contains(stem) {
        for &e in &[CH_TA_MARBUTA, CH_TA] {
            let form = concat_slices(&[stem, &[e]]);
            add_definite_forms(t2, &form);
            add_indefinite_forms(t2, &form, false);
        }
        for &pron in PRONOUNS {
            let form = concat_slices(&[stem, &[CH_TA], pron]);
            add_indefinite_forms(t2, &form, false);
        }
        add_fem_paradigm(t3, stem, false, true);
    }

    // Masculine sound plural ون / ين
    for suf in &[&[CH_WAW, CH_NUN][..], &[CH_YA, CH_NUN][..]] {
        let form = concat_slices(&[stem, suf]);
        add_definite_forms(t3, &form);
        add_indefinite_forms(t3, &form, false);
    }

    // Masculine dual ان
    if !lex.no_dual_an_expansion.contains(stem) {
        let dual = concat_slices(&[stem, &[CH_ALEF, CH_NUN]]);
        add_definite_forms(t3, &dual);
        add_indefinite_forms(t3, &dual, false);
    }

    // Construct masculine plural (stem + و / stem + ي + optional pron) for clear participles
    if stem.len() >= 4 && is_participle_shape {
        let c_waw = concat_slices(&[stem, &[CH_WAW]]);
        let c_ya = concat_slices(&[stem, &[CH_YA]]);
        add_indefinite_forms(t3, &c_waw, false);
        add_indefinite_forms(t3, &c_ya, false);
        for &pron in PRONOUNS {
            if pron != [CH_YA] {
                let fw = concat_slices(&[stem, &[CH_WAW], pron]);
                let fy = concat_slices(&[stem, &[CH_YA], pron]);
                add_indefinite_forms(t3, &fw, false);
                add_indefinite_forms(t3, &fy, false);
            }
        }
    }
}

fn add_family_member(target: &mut HashSet<Vec<u16>>, member: &FamilyMember, lex: &MorphLexicon) {
    let m = member.text.as_slice();
    match member.kind {
        FamilyMemberKind::DefOnly => {
            add_definite_forms(target, m);
            return;
        }
        FamilyMemberKind::FixedWaLam => {
            add_with_proclitics(target, m, SAFE_WA_LAM_PROCLITICS);
            return;
        }
        FamilyMemberKind::FixedSafe => {
            let procls = if starts_with_slice(m, WORD_YAD) {
                SAFE_WA_BA_PROCLITICS
            } else {
                SAFE_NO_KAF_PROCLITICS
            };
            add_with_proclitics(target, m, procls);
            return;
        }
        FamilyMemberKind::Normal => {}
    }

    if m.len() == 2 {
        add_definite_forms(target, m);
        if m == WORD_YAD {
            add_with_proclitics(target, m, SAFE_WA_BA_PROCLITICS);
            add_pronoun_forms(target, m, SAFE_WA_BA_PROCLITICS, PRONOUNS, false, lex);
        } else if m == WORD_RABB {
            add_with_proclitics(target, m, SAFE_NO_KAF_PROCLITICS);
            add_pronoun_forms(target, m, SAFE_NO_KAF_PROCLITICS, PRONOUNS, false, lex);
        } else if m == WORD_UMM {
            // Never bare أم (أَمْ 119x), never امنا (آمَنَّا 38x), never امي (أُمِّي)
            add_pronoun_forms(target, m, SAFE_NO_KAF_PROCLITICS, MOTHER_PRONOUNS, false, lex);
        } else if m == WORD_DAM {
            target.insert(m.to_vec());
            target.insert(concat_slices(&[&[CH_WAW], m]));
            target.insert(concat_slices(&[&[CH_BA], m]));
            add_pronoun_forms(target, m, SAFE_WA_BA_PROCLITICS, PRONOUNS, false, lex);
        } else if m == WORD_AKH || m == WORD_SUMM {
            target.insert(m.to_vec());
            target.insert(concat_slices(&[&[CH_WAW], m]));
        } else if m == WORD_HAYY {
            // Never وحي ("revelation")
            target.insert(m.to_vec());
        }
        return;
    }

    if lex.broken_plural_ta_marbuta.contains(m) {
        // Broken plural ending in ة (ملائكة/كفرة/فجرة/سحرة/ألسنة/أفئدة/إخوة/فتية...):
        // Never emit bare open-Ta (كفرت/فجرت/سحرت) or dual/ات; only ة + possessive ت+pron.
        let base = &m[..m.len() - 1];
        add_definite_forms(target, m);
        add_indefinite_forms(target, m, false);
        for &pron in PRONOUNS {
            let form = concat_slices(&[base, &[CH_TA], pron]);
            add_indefinite_forms(target, &form, false);
        }
        return;
    }

    if m.len() >= 3 && m.last() == Some(&CH_TA_MARBUTA) {
        add_fem_paradigm(target, &m[..m.len() - 1], true, true);
        return;
    }

    if m == WORD_KALIM || m == WORD_QURA {
        add_definite_forms(target, m);
        add_with_proclitics(target, m, SAFE_WA_BA_LAM_PROCLITICS);
        return;
    }

    add_definite_forms(target, m);
    add_indefinite_forms(target, m, false);
    let last = *m.last().unwrap_or(&0);
    let ends_at = ends_with_slice(m, &[CH_ALEF, CH_TA]);
    let ends_non_at_infl = ends_with_slice(m, &[CH_WAW, CH_NUN])
        || ends_with_slice(m, &[CH_YA, CH_NUN])
        || ends_with_slice(m, &[CH_ALEF, CH_NUN])
        || ends_with_slice(m, &[CH_WAW, CH_ALEF])
        || ends_with_slice(m, &[CH_TA, CH_MIM])
        || ends_with_slice(m, &[CH_TA, CH_NUN]);
    if last != CH_ALEF
        && last != CH_HAMZA
        && last != CH_HA
        && !ends_at
        && !ends_non_at_infl
        && !lex.no_tanwin_ya.contains(m)
        && m != WORD_RAHMAN
    {
        let tanwin = concat_slices(&[m, &[CH_ALEF]]);
        add_indefinite_forms(target, &tanwin, false);
    }
    // Sound feminine plurals (-ات) and broken plurals with lexical -ن (موازين/شياطين/بطون/آذان/إخوان/ألوان)
    // take direct possessive pronouns!
    if (!ends_non_at_infl || lex.broken_plural_n_pron.contains(m))
        && !lex.no_pron_family_members.contains(m)
        && (last != CH_HA || lex.root_ha_words.contains(m))
    {
        add_pronoun_forms(
            target,
            m,
            INDEF_PROCLITICS,
            PRONOUNS,
            m == WORD_KUTUB,
            lex,
        );
    }
}

#[derive(Clone, Debug)]
struct WordMatcher {
    tier1: HashSet<Vec<u16>>,
    tier2: HashSet<Vec<u16>>,
    tier3: HashSet<Vec<u16>>,
}

impl WordMatcher {
    fn build(orig_w: &[u16], norm_w: &[u16]) -> Self {
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

        let lex = lexicon();
        let orig_first = orig_w.first().copied().unwrap_or(0);
        let orig_last = orig_w.last().copied().unwrap_or(0);

        // Detect definite article ال (orig_first must be plain ا or Alef Wasla ٱ, never أ/إ/آ)
        let has_al = (orig_first == CH_ALEF || orig_first == CH_ALEF_WASLA)
            && norm_w.len() >= 4
            && norm_w[0] == CH_ALEF
            && norm_w[1] == CH_LAM;
        let stem = if has_al { &norm_w[2..] } else { norm_w };
        let orig_stem = if has_al && orig_w.len() >= 2 { &orig_w[2..] } else { orig_w };
        let orig_stem_first = orig_stem.first().copied().unwrap_or(0);
        let orig_stem_second = orig_stem.get(1).copied().unwrap_or(0);

        let ends_wn = ends_with_slice(stem, &[CH_WAW, CH_NUN]);
        let ends_yn = ends_with_slice(stem, &[CH_YA, CH_NUN]);
        let ends_an = ends_with_slice(stem, &[CH_ALEF, CH_NUN]);
        let ends_at = ends_with_slice(stem, &[CH_ALEF, CH_TA]);
        let ends_tan = ends_with_slice(stem, &[CH_TA, CH_ALEF, CH_NUN]);
        let ends_tin = ends_with_slice(stem, &[CH_TA, CH_YA, CH_NUN]);

        // Detect feminine ة-noun (explicit ة, or definite ال + ه lazy typing like الجنه/الصلاه)
        let is_fem = stem.len() >= 3
            && (orig_last == CH_TA_MARBUTA || (has_al && orig_last == CH_HA));

        let is_fa3il_shape = orig_stem_second == CH_ALEF
            && (stem.len() == 4
                || (stem.len() == 5 && orig_last == CH_TA_MARBUTA)
                || (stem.len() == 6 && (ends_wn || ends_yn || ends_an || ends_at)));
        let in_known_nouns = lex.family_index.contains_key(stem);
        let is_verb_like = !has_al
            && !is_fem
            && !in_known_nouns
            && !is_fa3il_shape
            && stem.len() >= 4
            && matches!(stem[0], CH_YA | CH_TA | CH_NUN);

        let mut family_lookup_keys: Vec<Vec<u16>> = Vec::with_capacity(3);
        family_lookup_keys.push(stem.to_vec());

        if is_fem {
            let base = &stem[..stem.len() - 1]; // len >= 2
            let fem_canon = concat_slices(&[base, &[CH_TA_MARBUTA]]);

            if lex.broken_plural_ta_marbuta.contains(fem_canon.as_slice()) {
                let fem_ends: &[u16] = if orig_last == CH_HA {
                    &[CH_TA_MARBUTA, CH_HA]
                } else {
                    &[CH_TA_MARBUTA]
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
                for &pron in PRONOUNS {
                    let form = concat_slices(&[base, &[CH_TA], pron]);
                    add_indefinite_forms(&mut t2, &form, false);
                }
                if orig_last == CH_HA {
                    family_lookup_keys.push(fem_canon);
                }
            } else {
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
                for &pron in PRONOUNS {
                    let form = concat_slices(&[base, &[CH_TA], pron]);
                    add_indefinite_forms(&mut t2, &form, false);
                }
                add_fem_paradigm(&mut t3, base, false, true);
                if orig_last == CH_HA {
                    family_lookup_keys.push(fem_canon.clone());
                }

                // Participle feminine singular (مسلمة/مؤمنة/صالحة/كافرة...) -> link masculine singular/plural in T3
                let base_last = *base.last().unwrap_or(&0);
                if base.len() >= 4
                    && !lex.fem_participle_exclude.contains(fem_canon.as_slice())
                    && !matches!(base_last, CH_ALEF | CH_YA | CH_WAW | CH_HAMZA)
                {
                    let is_base_part =
                        base[0] == CH_MIM || (base.len() == 4 && base[1] == CH_ALEF);
                    if is_base_part {
                        add_definite_forms(&mut t3, base);
                        add_indefinite_forms(&mut t3, base, false);
                        let tanwin = concat_slices(&[base, &[CH_ALEF]]);
                        add_indefinite_forms(&mut t3, &tanwin, false);
                        for suf in &[&[CH_WAW, CH_NUN][..], &[CH_YA, CH_NUN][..]] {
                            let pl = concat_slices(&[base, suf]);
                            add_definite_forms(&mut t3, &pl);
                            add_indefinite_forms(&mut t3, &pl, false);
                        }
                        family_lookup_keys.push(base.to_vec());
                    }
                }
            }
        } else if stem.len() >= 5
            && (ends_tan || ends_tin)
            && !is_verb_like
            && !lex.non_dual_tan_tin.contains(stem)
        {
            // Reverse Feminine Dual: الجنتين / جنتان / القريتين / الطائفتين / آيتين / مرتين
            let base = &stem[..stem.len() - 3];
            if has_al {
                add_definite_forms(&mut t1, stem);
                add_indefinite_forms(&mut t2, stem, false);
            } else {
                add_indefinite_forms(&mut t1, stem, false);
                add_definite_forms(&mut t1, stem);
            }
            for &e in &[CH_TA_MARBUTA, CH_TA] {
                let form = concat_slices(&[base, &[e]]);
                add_definite_forms(&mut t2, &form);
                add_indefinite_forms(&mut t2, &form, false);
            }
            for &pron in PRONOUNS {
                let form = concat_slices(&[base, &[CH_TA], pron]);
                add_indefinite_forms(&mut t2, &form, false);
            }
            add_fem_paradigm(&mut t3, base, false, true);
            family_lookup_keys.push(concat_slices(&[base, &[CH_TA_MARBUTA]]));
        } else if stem.len() >= 4
            && ends_at
            && !is_verb_like
            && !lex.non_plural_at.contains(stem)
        {
            // Reverse Feminine Plural: جنات / مسلمات / مؤمنات / صالحات / آيات / صلوات / حسنات / سيئات
            let base = &stem[..stem.len() - 2];
            if has_al {
                add_definite_forms(&mut t1, stem);
                add_indefinite_forms(&mut t2, stem, false);
            } else {
                add_indefinite_forms(&mut t1, stem, false);
                add_definite_forms(&mut t1, stem);
            }
            for &pron in PRONOUNS {
                let form = concat_slices(&[stem, pron]);
                add_indefinite_forms(&mut t2, &form, false);
            }
            if base.len() >= 2 && base != WORD_BIN && base != WORD_SAMAW && base != WORD_UMM {
                for &e in &[CH_TA_MARBUTA, CH_TA] {
                    let form = concat_slices(&[base, &[e]]);
                    add_definite_forms(&mut t2, &form);
                    add_indefinite_forms(&mut t2, &form, false);
                }
                for &pron in PRONOUNS {
                    let form = concat_slices(&[base, &[CH_TA], pron]);
                    add_indefinite_forms(&mut t2, &form, false);
                }
                if base.len() >= 3 && base.last() == Some(&CH_WAW) {
                    let a_base = concat_slices(&[&base[..base.len() - 1], &[CH_ALEF]]);
                    for &e in &[CH_TA_MARBUTA, CH_TA] {
                        let form = concat_slices(&[&a_base, &[e]]);
                        add_definite_forms(&mut t2, &form);
                        add_indefinite_forms(&mut t2, &form, false);
                    }
                    family_lookup_keys.push(concat_slices(&[&a_base, &[CH_TA_MARBUTA]]));
                }
                family_lookup_keys.push(concat_slices(&[base, &[CH_TA_MARBUTA]]));

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
                if base.len() >= 4
                    && (base[0] == CH_MIM || (base.len() == 4 && base[1] == CH_ALEF))
                    && !lex.no_fem_expansion.contains(base)
                {
                    add_definite_forms(&mut t3, base);
                    add_indefinite_forms(&mut t3, base, false);
                    let base_last = *base.last().unwrap_or(&0);
                    if !matches!(base_last, CH_ALEF | CH_HAMZA | CH_YA) {
                        let tanwin = concat_slices(&[base, &[CH_ALEF]]);
                        add_indefinite_forms(&mut t3, &tanwin, false);
                        add_pronoun_forms(&mut t3, base, INDEF_PROCLITICS, PRONOUNS, false, lex);
                        for suf in &[&[CH_WAW, CH_NUN][..], &[CH_YA, CH_NUN][..]] {
                            let pl = concat_slices(&[base, suf]);
                            add_definite_forms(&mut t3, &pl);
                            add_indefinite_forms(&mut t3, &pl, false);
                        }
                    }
                    family_lookup_keys.push(base.to_vec());
                }
            }
        } else if stem.len() >= 5
            && (ends_wn || ends_yn)
            && !is_verb_like
            && !lex.non_plural_wn_yn.contains(stem)
            && !lex.cardinal_numbers.contains(stem)
        {
            // Reverse Masculine Sound Plural / Dual: المسلمون/المسلمين/المؤمنين/الصالحين/المتقين/الكافرون/النبيين
            let base = &stem[..stem.len() - 2];
            let alt_suf: &[u16] = if ends_wn { &[CH_YA, CH_NUN] } else { &[CH_WAW, CH_NUN] };
            if has_al {
                add_definite_forms(&mut t1, stem);
                add_indefinite_forms(&mut t2, stem, false);
            } else {
                add_indefinite_forms(&mut t1, stem, false);
                add_definite_forms(&mut t1, stem);
            }
            let alt_pl = concat_slices(&[base, alt_suf]);
            add_definite_forms(&mut t3, &alt_pl);
            add_indefinite_forms(&mut t3, &alt_pl, false);

            let base_last = *base.last().unwrap_or(&0);
            if base.len() >= 4 || lex.safe_3_rev_bases.contains(base) {
                add_definite_forms(&mut t2, base);
                add_indefinite_forms(&mut t2, base, false);
                if base_last != CH_ALEF
                    && base_last != CH_HAMZA
                    && base_last != CH_HA
                    && !lex.no_tanwin_ya.contains(base)
                    && base != WORD_RAHMAN
                {
                    let tanwin = concat_slices(&[base, &[CH_ALEF]]);
                    add_indefinite_forms(&mut t2, &tanwin, false);
                }
                if base_last != CH_HA || lex.root_ha_words.contains(base) {
                    add_pronoun_forms(&mut t2, base, INDEF_PROCLITICS, PRONOUNS, false, lex);
                }
                if stem != WORD_ALAMIN && stem != WORD_ALAMUN {
                    family_lookup_keys.push(base.to_vec());
                }
            }

            // Defective weak-ي singular (متقون/متقين -> متقي, مهتدين -> مهتدي, معتدين -> معتدي, باقين -> باقي, أعلون -> أعلى)
            if base.len() >= 3
                && base_last != CH_YA
                && (base[0] == CH_MIM || (base.len() == 3 && base[1] == CH_ALEF) || base == WORD_AAL)
            {
                let weak_sg = concat_slices(&[base, &[CH_YA]]);
                add_definite_forms(&mut t2, &weak_sg);
                add_indefinite_forms(&mut t2, &weak_sg, false);
            }

            if base.len() >= 4
                && !matches!(base_last, CH_ALEF | CH_HAMZA | CH_YA | CH_WAW | CH_HA)
                && !lex.no_fem_expansion.contains(base)
            {
                let is_part = base[0] == CH_MIM || (base.len() == 4 && base[1] == CH_ALEF);
                if is_part {
                    for &e in &[CH_TA_MARBUTA, CH_TA] {
                        let form = concat_slices(&[base, &[e]]);
                        add_definite_forms(&mut t2, &form);
                        add_indefinite_forms(&mut t2, &form, false);
                    }
                    for &pron in PRONOUNS {
                        let form = concat_slices(&[base, &[CH_TA], pron]);
                        add_indefinite_forms(&mut t2, &form, false);
                    }
                    add_fem_paradigm(&mut t3, base, false, true);
                    if !lex.no_dual_an_expansion.contains(base) {
                        let dual = concat_slices(&[base, &[CH_ALEF, CH_NUN]]);
                        add_definite_forms(&mut t3, &dual);
                        add_indefinite_forms(&mut t3, &dual, false);
                    }
                    let c_waw = concat_slices(&[base, &[CH_WAW]]);
                    let c_ya = concat_slices(&[base, &[CH_YA]]);
                    add_indefinite_forms(&mut t3, &c_waw, false);
                    add_indefinite_forms(&mut t3, &c_ya, false);
                    for &pron in PRONOUNS {
                        if pron != [CH_YA] {
                            let fw = concat_slices(&[base, &[CH_WAW], pron]);
                            let fy = concat_slices(&[base, &[CH_YA], pron]);
                            add_indefinite_forms(&mut t3, &fw, false);
                            add_indefinite_forms(&mut t3, &fy, false);
                        }
                    }
                }
            }
        } else if stem.len() >= 6
            && ends_an
            && !is_verb_like
            && !lex.non_dual_an.contains(stem)
        {
            // Reverse Masculine Dual (strictly guarded): مسلمان / مؤمنان / ساحران / والدان / فريقان
            let base = &stem[..stem.len() - 2];
            if has_al {
                add_definite_forms(&mut t1, stem);
                add_indefinite_forms(&mut t2, stem, false);
            } else {
                add_indefinite_forms(&mut t1, stem, false);
                add_definite_forms(&mut t1, stem);
            }
            if base.len() >= 4
                && (base[0] == CH_MIM
                    || (base.len() == 4 && base[1] == CH_ALEF)
                    || base == WORD_FARIQ)
            {
                add_definite_forms(&mut t2, base);
                add_indefinite_forms(&mut t2, base, false);
                let base_last = *base.last().unwrap_or(&0);
                if !matches!(base_last, CH_ALEF | CH_HAMZA | CH_YA | CH_HA) {
                    let tanwin = concat_slices(&[base, &[CH_ALEF]]);
                    add_indefinite_forms(&mut t2, &tanwin, false);
                    add_pronoun_forms(&mut t2, base, INDEF_PROCLITICS, PRONOUNS, false, lex);
                }
                let is_part = base[0] == CH_MIM || (base.len() == 4 && base[1] == CH_ALEF);
                add_masculine_inflections(&mut t2, &mut t3, base, is_part, lex);
                family_lookup_keys.push(base.to_vec());
            }
        } else if has_al {
            // Definite non-feminine noun
            add_definite_forms(&mut t1, stem);
            if stem.len() >= 3 {
                add_indefinite_forms(&mut t2, stem, false);
                let last = *stem.last().unwrap_or(&0);
                if last != CH_ALEF
                    && last != CH_HAMZA
                    && !lex.no_tanwin_ya.contains(stem)
                    && stem != WORD_RAHMAN
                {
                    let tanwin = concat_slices(&[stem, &[CH_ALEF]]);
                    add_indefinite_forms(&mut t2, &tanwin, false);
                }
                if (!ends_wn && !ends_yn) || lex.broken_plural_n_pron.contains(stem) {
                    add_pronoun_forms(
                        &mut t2,
                        stem,
                        INDEF_PROCLITICS,
                        PRONOUNS,
                        stem == WORD_KUTUB,
                        lex,
                    );
                }
                let can_masc = !lex.cardinal_numbers.contains(stem)
                    && !matches!(last, CH_TA_MARBUTA | CH_ALEF | CH_HAMZA | CH_WAW | CH_HA | CH_YA)
                    && !ends_wn
                    && !ends_yn
                    && !ends_an
                    && !ends_at
                    && (stem.len() >= 4 || (stem.len() == 3 && orig_stem_first == CH_ALEF_MADDA));
                if can_masc {
                    let is_part = stem[0] == CH_MIM
                        || (stem.len() == 4 && orig_stem_second == CH_ALEF);
                    add_masculine_inflections(&mut t2, &mut t3, stem, is_part, lex);
                } else if stem.len() >= 4
                    && last == CH_YA
                    && !lex.cardinal_numbers.contains(stem)
                    && ((stem[0] == CH_MIM && stem != WORD_MUSA && !in_known_nouns)
                        || (stem.len() == 4 && orig_stem_second == CH_ALEF)
                        || stem == WORD_AALA)
                {
                    // Defective weak-ي stems (المتقي/المهتدي/المعتدي/الباقي/العالي/الأعلى)
                    let drop_y = &stem[..stem.len() - 1];
                    for suf in &[&[CH_WAW, CH_NUN][..], &[CH_YA, CH_NUN][..]] {
                        let pl = concat_slices(&[drop_y, suf]);
                        add_definite_forms(&mut t3, &pl);
                        add_indefinite_forms(&mut t3, &pl, false);
                    }
                    add_fem_paradigm(&mut t3, stem, true, true);
                }
            } else if stem == WORD_YAD {
                add_with_proclitics(&mut t2, stem, SAFE_WA_BA_PROCLITICS);
                add_pronoun_forms(&mut t2, stem, SAFE_WA_BA_PROCLITICS, PRONOUNS, false, lex);
            } else if stem == WORD_RABB {
                add_with_proclitics(&mut t2, stem, SAFE_NO_KAF_PROCLITICS);
                add_pronoun_forms(&mut t2, stem, SAFE_NO_KAF_PROCLITICS, PRONOUNS, false, lex);
            } else if stem == WORD_UMM {
                add_pronoun_forms(
                    &mut t2,
                    stem,
                    SAFE_NO_KAF_PROCLITICS,
                    MOTHER_PRONOUNS,
                    false,
                    lex,
                );
            } else if stem == WORD_DAM {
                t2.insert(stem.to_vec());
                t2.insert(concat_slices(&[&[CH_WAW], stem]));
                t2.insert(concat_slices(&[&[CH_BA], stem]));
                add_pronoun_forms(&mut t2, stem, SAFE_WA_BA_PROCLITICS, PRONOUNS, false, lex);
            } else {
                // 2-letter definite stems (e.g. الجن/السن/السر): allow only bare & و+stem
                t2.insert(stem.to_vec());
                if stem != WORD_HAYY {
                    t2.insert(concat_slices(&[&[CH_WAW], stem]));
                }
            }
        } else {
            // Indefinite non-feminine Arabic word (len >= 3)
            add_indefinite_forms(&mut t1, stem, is_verb_like);
            add_definite_forms(&mut t1, stem);
            let last = *stem.last().unwrap_or(&0);
            if last == CH_HA && !lex.root_ha_words.contains(stem) {
                let alt_ta = concat_slices(&[&stem[..stem.len() - 1], &[CH_TA_MARBUTA]]);
                add_indefinite_forms(&mut t1, &alt_ta, false);
                add_definite_forms(&mut t1, &alt_ta);
            }

            let ends_wa = ends_with_slice(stem, &[CH_WAW, CH_ALEF]);
            let ends_tm = ends_with_slice(stem, &[CH_TA, CH_MIM]);
            let ends_tn = ends_with_slice(stem, &[CH_TA, CH_NUN]);
            let ends_pron = (last == CH_HA && !lex.root_ha_words.contains(stem))
                || ends_with_slice(stem, &[CH_HA, CH_ALEF])
                || ends_with_slice(stem, &[CH_HA, CH_MIM])
                || ends_with_slice(stem, &[CH_KAF, CH_MIM])
                || ends_with_slice(stem, &[CH_NUN, CH_ALEF]);

            if last != CH_ALEF
                && last != CH_HAMZA
                && !ends_wn
                && !ends_yn
                && !ends_wa
                && !ends_tm
                && !ends_tn
                && !lex.no_tanwin_ya.contains(stem)
                && stem != WORD_RAHMAN
                && stem != WORD_KALIM
            {
                let tanwin = concat_slices(&[stem, &[CH_ALEF]]);
                add_indefinite_forms(&mut t2, &tanwin, false);
            }
            if ((!ends_wa && !ends_wn && !ends_yn && !ends_tm && !ends_tn && !ends_pron)
                || lex.broken_plural_n_pron.contains(stem))
                && stem != WORD_KALIM
                && stem != WORD_QURA
            {
                add_pronoun_forms(
                    &mut t2,
                    stem,
                    INDEF_PROCLITICS,
                    PRONOUNS,
                    stem == WORD_KUTUB,
                    lex,
                );
            }

            let starts_folded_al = starts_with_slice(stem, &[CH_ALEF, CH_LAM]);
            let can_masc = !is_verb_like
                && !starts_folded_al
                && !lex.cardinal_numbers.contains(stem)
                && !matches!(last, CH_TA_MARBUTA | CH_ALEF | CH_HAMZA | CH_WAW | CH_HA | CH_YA)
                && !ends_wn
                && !ends_yn
                && !ends_an
                && !ends_at
                && !ends_wa
                && !ends_tm
                && !ends_tn
                && !ends_pron
                && (stem.len() >= 4 || (stem.len() == 3 && orig_stem_first == CH_ALEF_MADDA));
            if can_masc {
                let is_part = stem[0] == CH_MIM
                    || (stem.len() == 4 && orig_stem_second == CH_ALEF);
                add_masculine_inflections(&mut t2, &mut t3, stem, is_part, lex);
            } else if !is_verb_like
                && !starts_folded_al
                && stem.len() >= 4
                && last == CH_YA
                && !lex.cardinal_numbers.contains(stem)
                && ((stem[0] == CH_MIM && stem != WORD_MUSA && !in_known_nouns)
                    || (stem.len() == 4 && orig_stem_second == CH_ALEF)
                    || stem == WORD_AALA)
            {
                let drop_y = &stem[..stem.len() - 1];
                for suf in &[&[CH_WAW, CH_NUN][..], &[CH_YA, CH_NUN][..]] {
                    let pl = concat_slices(&[drop_y, suf]);
                    add_definite_forms(&mut t3, &pl);
                    add_indefinite_forms(&mut t3, &pl, false);
                }
                add_fem_paradigm(&mut t3, stem, true, true);
            }
        }

        // Curated Quranic Broken-Plural & Irregular-Lemma Family Expansion (Tier 3)
        if !is_verb_like {
            let mut seen_fams: Vec<usize> = Vec::new();
            for key in &family_lookup_keys {
                if let Some(fam_ids) = lex.family_index.get(key.as_slice()) {
                    for &fid in fam_ids {
                        if !seen_fams.contains(&fid) {
                            seen_fams.push(fid);
                            for member in &lex.families[fid] {
                                if member.text.as_slice() != stem {
                                    add_family_member(&mut t3, member, lex);
                                }
                            }
                        }
                    }
                }
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

        // Pure Arabic query: extract words with original unfolded letters preserved alongside folded letters.
        let mut raw_words: Vec<(Vec<u16>, Vec<u16>)> = Vec::new();
        let mut cur_orig: Vec<u16> = Vec::new();
        let mut cur_norm: Vec<u16> = Vec::new();

        for &ch in query_u16 {
            if ch == b'(' as u16 || ch == b')' as u16 || is_arabic_ignorable(ch) {
                continue;
            }
            if is_whitespace_u16(ch) {
                if !cur_norm.is_empty() {
                    raw_words.push((
                        std::mem::take(&mut cur_orig),
                        std::mem::take(&mut cur_norm),
                    ));
                }
                continue;
            }
            let unpresented = map_presentation_form(ch);
            let folded = fold_arabic_letter(ch);
            if is_arabic_letter(folded) {
                cur_orig.push(unpresented);
                cur_norm.push(folded);
            }
        }
        if !cur_norm.is_empty() {
            raw_words.push((cur_orig, cur_norm));
        }

        if raw_words.is_empty() {
            return Some(Self {
                mode: QueryMode::Empty,
                effective_len: 0,
                query_mask: 0,
            });
        }

        if raw_words.len() == 1 {
            let (orig_w, norm_w) = raw_words.into_iter().next().unwrap();
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
            let matcher = WordMatcher::build(&orig_w, &norm_w);
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
        for (idx, (orig_w, norm_w)) in raw_words.into_iter().enumerate() {
            if idx > 0 {
                norm_phrase.push(CH_SPACE);
            }
            norm_phrase.extend_from_slice(&norm_w);
            words.push(WordMatcher::build(&orig_w, &norm_w));
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
