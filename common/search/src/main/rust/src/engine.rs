//! Pure-Rust, zero-unsafe Arabic Quran search engine operating directly on UTF-16
//! code units (`u16`) for 100% exact parity with `ArabicCharacterHelper`,
//! `ArabicCompiledQuery`, and Java/Android `String` UTF-16 span offsets.

/// Maximum verses allowed in a single loaded index (~16x full Quran of 6,236 verses).
pub const MAX_VERSES: usize = 100_000;
/// Maximum total UTF-16 code units in the contiguous verse text arena (~32 MiB cap;
/// full 6,236-verse Arabic Quran uses ~404,846 code units = ~791 KiB).
pub const MAX_ARENA_CODE_UNITS: usize = 16_000_000;

/// Packed integer stride returned per match by `VerseIndex::search`:
/// `[verse_index, row_id, sura, ayah]`.
pub const MATCH_STRIDE: usize = 4;

// All Arabic equivalence-class target characters fall within U+0621..=U+064A (42 codepoints),
// allowing branchless O(1) bitmask membership testing in a single `u64`.
const EQUIV_BASE: u16 = 0x0621;
const EQUIV_MAX: u16 = 0x064A;
const EQUIV_SPAN: u16 = EQUIV_MAX - EQUIV_BASE;

#[inline(always)]
const fn equiv_bit(ch: u16) -> u64 {
    1u64 << ((ch - EQUIV_BASE) as u32)
}

// Exact parity with com.quran.common.search.arabic.ArabicCharacterHelper:
//   ا (0627) -> آأإاى (0622, 0623, 0625, 0627, 0649)
//   و (0648) -> وؤ    (0648, 0624)
//   أ (0623) -> ءأؤئا (0621, 0623, 0624, 0626, 0627)
//   ء (0621) -> ءأؤئا (0621, 0623, 0624, 0626, 0627)
//   ت (062A) -> تة    (062A, 0629)
//   ة (0629) -> ةته   (0629, 062A, 0647)
//   ه (0647) -> هة    (0647, 0629)
//   ى (0649) -> ىي    (0649, 064A)
//   ي (064A) -> يى    (064A, 0649)
//   ئ (0626) -> ئىي   (0626, 0649, 064A)
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

// Precomputed equivalence-group presence bits stored in bits 42..=50 of `VerseMeta.letter_mask`
// so `VerseIndex::search` rejects non-matching verses in a single `(meta.letter_mask & query_mask) != query_mask` check.
const Q_BIT_ALEF: u64 = 1u64 << 42;
const Q_BIT_WAW: u64 = 1u64 << 43;
const Q_BIT_HAMZA_ALEF: u64 = 1u64 << 44;
const Q_BIT_TA: u64 = 1u64 << 45;
const Q_BIT_TA_MARBUTA: u64 = 1u64 << 46;
const Q_BIT_HA: u64 = 1u64 << 47;
const Q_BIT_ALEF_MAQSURA_YA: u64 = 1u64 << 48;
const Q_BIT_YA_HAMZA: u64 = 1u64 << 49;
const Q_BIT_SPACE: u64 = 1u64 << 50;

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

/// Computes the 64-bit character & equivalence-group presence mask for a UTF-16 verse slice.
#[inline]
pub fn compute_verse_letter_mask(u16s: &[u16]) -> u64 {
    let mut raw_mask = 0u64;
    for &ch in u16s {
        let delta = ch.wrapping_sub(EQUIV_BASE);
        if delta <= EQUIV_SPAN {
            raw_mask |= 1u64 << (delta as u32);
        } else if ch == 0x0020 {
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
    /// Two consecutive exact code units at `(offset, offset + 1)` where at least one is non-space.
    ExactPair { offset: usize, first: u16, second: u16 },
    /// Single exact code unit at `offset`.
    ExactSingle { offset: usize, ch: u16 },
    /// All pattern units are equivalence masks; scan on unit 0's mask.
    FirstMask(u64),
}

/// Fixed-length compiled Arabic search query over UTF-16 code units.
#[derive(Clone, Debug)]
pub struct CompiledArabicQuery {
    units: Vec<PatternUnit>,
    anchor: Anchor,
    query_mask: u64,
}

impl CompiledArabicQuery {
    /// Compiles a UTF-16 query using exact `ArabicCharacterHelper` rules:
    /// - Strips `(` (`0x0028`) and `)` (`0x0029`)
    /// - Maps the 10 Arabic equivalence classes to bitmasks
    /// - Treats all other UTF-16 code units (including regex metacharacters and surrogates) literally
    ///
    /// Returns `None` if no pattern units remain (empty or parentheses-only query).
    pub fn compile(query_u16: &[u16]) -> Option<Self> {
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
                } else if ch == 0x0020 {
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

    #[inline(always)]
    pub fn query_mask(&self) -> u64 {
        self.query_mask
    }

    /// Convenience helper for Rust tests using UTF-8 `&str`.
    pub fn compile_str(query: &str) -> Option<Self> {
        let u16s: Vec<u16> = query.encode_utf16().collect();
        Self::compile(&u16s)
    }

    pub fn len(&self) -> usize {
        self.units.len()
    }

    pub fn is_empty(&self) -> bool {
        self.units.is_empty()
    }

    fn select_anchor(units: &[PatternUnit]) -> Anchor {
        // 1. Prefer an adjacent pair of exact code units (skipping pairs of spaces).
        //    For queries like "الله" ([Mask(ا), Exact(ل), Exact(ل), Mask(ه)]), this scans
        //    directly for the exact bigram "لل", rejecting >99% of positions in one check.
        let mut best_pair: Option<(usize, u16, u16, u8)> = None;
        for i in 0..units.len().saturating_sub(1) {
            if let (PatternUnit::Exact(a), PatternUnit::Exact(b)) = (units[i], units[i + 1]) {
                if a == 0x0020 && b == 0x0020 {
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

        // 2. Otherwise pick the rarest exact character (preferring non-space).
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

        // 3. All pattern units are equivalence masks; unit 0 is guaranteed to be Mask.
        match units[0] {
            PatternUnit::Mask(mask) => Anchor::FirstMask(mask),
            PatternUnit::Exact(ch) => Anchor::ExactSingle { offset: 0, ch },
        }
    }

    #[inline]
    fn exact_rarity_score(ch: u16) -> u8 {
        match ch {
            0x0020 => 0,             // space is the most common code unit in Quran text
            0x0644 | 0x0645 | 0x0646 => 1, // ل م ن are the highest-frequency exact Arabic letters
            0x0631 | 0x0628 | 0x0643 => 2, // ر ب ك medium frequency
            _ => 3,                  // rarer exact letters & symbols
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

    /// Finds the first UTF-16 start offset `>= from_index` where the query matches `text`.
    #[inline]
    pub fn find_first(&self, text: &[u16], from_index: usize) -> Option<usize> {
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
                // Anchor positions span [from_index + offset ..= max_start + offset].
                // Note max_start + offset + 1 <= text.len() - 1 because offset + 1 < pat_len.
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

    /// Finds all non-overlapping left-to-right UTF-16 match spans `[start0, end0, start1, end1, ...]`,
    /// matching `java.util.regex.Matcher.find()` and `ArabicCompiledQuery.highlightTo` semantics.
    pub fn find_all_spans(&self, text: &[u16]) -> Vec<i32> {
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

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct VerseMeta {
    pub row_id: i32,
    pub sura: i32,
    pub ayah: i32,
    pub offset: u32,
    pub len: u32,
    pub letter_mask: u64,
}

/// Cache-friendly contiguous UTF-16 verse arena holding the Arabic Quran index in ~0.9 MiB.
#[derive(Debug)]
pub struct VerseIndex {
    verses: Vec<VerseMeta>,
    arena: Vec<u16>,
}

impl VerseIndex {
    pub fn new(verses: Vec<VerseMeta>, arena: Vec<u16>) -> Self {
        Self { verses, arena }
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
        Ok(Self { verses, arena })
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

    /// Returns the UTF-16 slice for `verse_index`, or `None` if out of bounds.
    #[inline]
    pub fn verse_slice(&self, verse_index: usize) -> Option<&[u16]> {
        let meta = self.verses.get(verse_index)?;
        let start = meta.offset as usize;
        let end = start.checked_add(meta.len as usize)?;
        self.arena.get(start..end)
    }

    /// Searches all indexed verses for `query`, returning up to `max_results` matches
    /// packed as `[verse_index, row_id, sura, ayah, ...]`.
    pub fn search(&self, query: &CompiledArabicQuery, max_results: usize) -> Vec<i32> {
        if max_results == 0 || query.is_empty() || self.verses.is_empty() {
            return Vec::new();
        }
        let initial_cap = max_results.min(2048).saturating_mul(MATCH_STRIDE);
        let mut results = Vec::with_capacity(initial_cap);
        let mut hits = 0usize;
        let query_mask = query.query_mask();

        for (idx, meta) in self.verses.iter().enumerate() {
            if (meta.letter_mask & query_mask) != query_mask {
                continue;
            }
            let start = meta.offset as usize;
            let Some(end) = start.checked_add(meta.len as usize) else {
                continue;
            };
            let Some(slice) = self.arena.get(start..end) else {
                continue;
            };
            if query.find_first(slice, 0).is_some() {
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
}
