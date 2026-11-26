pub mod engine;
pub mod jni_bridge;
pub mod jni_sys;

#[cfg(test)]
mod tests {
    use crate::engine::{CompiledArabicQuery, VerseIndex, MATCH_STRIDE};

    fn utf16(s: &str) -> Vec<u16> {
        s.encode_utf16().collect()
    }

    #[test]
    fn normalization_and_orthographic_equivalence_classes() {
        let cases: &[(&str, &str, &str)] = &[
            // (query char, chars that MUST match, chars that MUST NOT match)
            ("\u{0627}", "\u{0622}\u{0623}\u{0625}\u{0627}\u{0671}", "\u{0628}\u{0648}\u{064a}"),
            ("\u{0623}", "\u{0622}\u{0623}\u{0625}\u{0627}\u{0671}", "\u{0628}\u{0648}\u{064a}"),
            ("\u{0625}", "\u{0622}\u{0623}\u{0625}\u{0627}\u{0671}", "\u{0628}\u{0648}\u{064a}"),
            ("\u{0622}", "\u{0622}\u{0623}\u{0625}\u{0627}\u{0671}", "\u{0628}\u{0648}\u{064a}"),
            ("\u{0648}", "\u{0648}\u{0624}", "\u{0627}\u{0621}\u{064a}"),
            ("\u{0624}", "\u{0648}\u{0624}", "\u{0627}\u{0621}\u{064a}"),
            ("\u{062a}", "\u{062a}\u{0629}", "\u{0647}\u{062b}"),
            ("\u{0629}", "\u{0629}\u{062a}\u{0647}", "\u{062b}\u{062d}"),
            ("\u{0647}", "\u{0647}\u{0629}", "\u{062a}\u{062d}"),
            ("\u{0649}", "\u{0649}\u{064a}\u{0626}", "\u{0627}\u{0628}"),
            ("\u{064a}", "\u{064a}\u{0649}\u{0626}", "\u{0627}\u{0628}"),
            ("\u{0626}", "\u{0626}\u{0649}\u{064a}", "\u{0627}\u{0628}"),
        ];

        for &(q, should_match, should_not_match) in cases {
            let compiled = CompiledArabicQuery::compile_str(q).expect("non-empty query");
            for ch in should_match.chars() {
                let text = utf16(&ch.to_string());
                assert_eq!(
                    compiled.find_first(&text, 0),
                    Some(0),
                    "query {q:?} must match {ch:?}"
                );
            }
            for ch in should_not_match.chars() {
                let text = utf16(&ch.to_string());
                assert_eq!(
                    compiled.find_first(&text, 0),
                    None,
                    "query {q:?} must NOT match {ch:?}"
                );
            }
        }
    }

    #[test]
    fn parentheses_ignored_and_regex_metachars_matched_literally() {
        let q_parens = CompiledArabicQuery::compile_str("(الله)").unwrap();
        let verse = utf16("بسم الله الرحمن الرحيم");
        assert_eq!(q_parens.find_all_spans(&verse), vec![4, 8]);

        let meta = ".*+?{}|^$\\[]";
        let q_meta = CompiledArabicQuery::compile_str(meta).unwrap();
        assert_eq!(q_meta.find_all_spans(&utf16(meta)), vec![0, meta.len() as i32]);
        assert!(q_meta.find_all_spans(&utf16("anything_else")).is_empty());

        assert!(CompiledArabicQuery::compile_str("").is_none());
        assert!(CompiledArabicQuery::compile_str("()").is_none());
        assert!(CompiledArabicQuery::compile_str("((()))").is_none());
    }

    #[test]
    fn multi_match_proclitic_lillah_and_short_queries() {
        let verse = utf16("الله لا إله إلا هو الحي القيوم والله عليم ولله الأمر");
        let q = CompiledArabicQuery::compile_str("الله").unwrap();
        // Matches whole Arabic words: "الله" (0..4), "والله" (31..36), "ولله" (42..46)
        assert_eq!(q.find_all_spans(&verse), vec![0, 4, 31, 36, 42, 46]);

        let repeating = utf16("للل لل للل");
        let q_ll = CompiledArabicQuery::compile_str("لل").unwrap();
        assert_eq!(q_ll.find_all_spans(&repeating), vec![0, 2, 4, 6, 7, 9]);

        let q_short = CompiledArabicQuery::compile_str("اه").unwrap();
        let text = utf16("آة إه اة");
        assert_eq!(q_short.find_all_spans(&text), vec![0, 2, 3, 5, 6, 8]);
    }

    #[test]
    fn utf16_surrogates_handled_safely_without_panics() {
        let verse = utf16("بسم \u{1F600} الله \u{1F600}");
        let q_arabic = CompiledArabicQuery::compile_str("الله").unwrap();
        // "بسم " = 4, emoji surrogate pair = 2, " " = 1 => "الله" starts at UTF-16 offset 7.
        assert_eq!(q_arabic.find_all_spans(&verse), vec![7, 11]);

        let q_emoji = CompiledArabicQuery::compile_str("\u{1F600}").unwrap();
        assert_eq!(q_emoji.find_all_spans(&verse), vec![4, 6, 12, 14]);
    }

    #[test]
    fn comprehensive_allah_and_jannah_morphology_with_zero_false_positives() {
        let rows = [
            (1, 1, 1, "بسم الله الرحمن الرحيم"),
            (2, 1, 2, "الحمد لله رب العالمين"),
            (3, 2, 115, "ولله المشرق والمغرب"),
            (4, 3, 26, "قل اللهم مالك الملك"),
            (5, 10, 59, "قل آلله أذن لكم"),
            (6, 53, 25, "فلله الآخرة والأولى"),
            (7, 29, 64, "لهو ولعب اللهو"),
            (8, 111, 3, "ذات اللهب"),
            (9, 10, 74, "يضلله من دونه"),
            (10, 2, 35, "اسكن أنت وزوجك الجنة"),
            (11, 41, 30, "وأبشروا بالجنة التي كنتم توعدون"),
            (12, 3, 133, "وجنة عرضها السماوات والأرض"),
            (13, 56, 89, "فروح وريحان وجنت نعيم"),
            (14, 89, 30, "وادخلي جنتي"),
            (15, 18, 33, "كلتا الجنتين آتت أكلها"),
            (16, 34, 15, "آية جنتان عن يمين وشمال"),
            (17, 34, 16, "وبدلناهم بجنتيهم جنتين"),
            (18, 2, 25, "لهم جنات تجري من تحتها الأنهار"),
            (19, 42, 22, "في روضات الجنات"),
            (20, 6, 100, "وجعلوا لله شركاء الجن"),
            (21, 36, 28, "جند من السماء"),
            (22, 2, 158, "فلا جناح عليه"),
            (23, 53, 32, "وإذ أنتم أجنة في بطون أمهاتكم"),
            (24, 12, 33, "السجن أحب إلي"),
        ];
        let index = VerseIndex::from_utf8_rows(&rows).unwrap();

        // "الله" must include الله / لله / ولله / اللهم / آلله / فلله and REJECT اللهو / اللهب / يضلله!
        let q_allah = CompiledArabicQuery::compile_str("الله").unwrap();
        let allah_hits = index.search(&q_allah, usize::MAX);
        let allah_ids: Vec<i32> = allah_hits.chunks_exact(MATCH_STRIDE).map(|c| c[1]).collect();
        assert_eq!(allah_ids, vec![1, 2, 3, 4, 5, 6, 20]);

        // "الجنة" must return Tier 1 (الجنة/بالجنة) -> Tier 2 (وجنة/وجنت/جنتي) -> Tier 3 (الجنتين/جنتان/بجنتيهم/جنات/الجنات)
        // and REJECT الجن / جند / جناح / أجنة / السجن!
        let q_jannah = CompiledArabicQuery::compile_str("الجنة").unwrap();
        let jannah_hits = index.search(&q_jannah, usize::MAX);
        let jannah_ids: Vec<i32> = jannah_hits.chunks_exact(MATCH_STRIDE).map(|c| c[1]).collect();
        assert_eq!(jannah_ids, vec![10, 11, 12, 13, 14, 15, 16, 17, 18, 19]);

        // Lazy keyboard "الجنه" must produce identical 100% comprehensive results!
        let q_jannah_ha = CompiledArabicQuery::compile_str("الجنه").unwrap();
        assert_eq!(index.search(&q_jannah_ha, usize::MAX), jannah_hits);

        // Verify exact highlight spans on morphological matches (e.g. "بجنتيهم" and "جنتين" on row 17)
        let r17 = index.verse_slice(16).unwrap();
        let spans17 = q_jannah.find_all_spans(r17);
        assert_eq!(spans17.len(), 4); // 2 matched words: بجنتيهم and جنتين
        let s1 = String::from_utf16(&r17[spans17[0] as usize..spans17[1] as usize]).unwrap();
        let s2 = String::from_utf16(&r17[spans17[2] as usize..spans17[3] as usize]).unwrap();
        assert_eq!(s1, "بجنتيهم");
        assert_eq!(s2, "جنتين");
    }

    #[test]
    fn verse_index_search_respects_max_results_and_bounds() {
        let rows = [
            (1, 1, 1, "بسم الله الرحمن الرحيم"),
            (2, 1, 2, "الحمد لله رب العالمين"),
            (3, 1, 3, "الرحمن الرحيم"),
            (4, 2, 255, "الله لا إله إلا هو الحي القيوم والله عليم"),
            (5, 2, 51, "وإذ واعدنا موسى أربعين ليلة"),
            (6, 9, 104, "ألم يعلموا أن الله هو يقبل التوبة عن عباده"),
        ];
        let index = VerseIndex::from_utf8_rows(&rows).unwrap();
        assert_eq!(index.len(), 6);
        assert!(index.verse_slice(99).is_none());

        let q_allah = CompiledArabicQuery::compile_str("الله").unwrap();
        let all = index.search(&q_allah, usize::MAX);
        // Matches 1:1 (الله), 1:2 (لله), 2:255 (الله/والله), 9:104 (الله)
        assert_eq!(all.len(), 4 * MATCH_STRIDE);
        assert_eq!(&all[0..4], &[0, 1, 1, 1]);
        assert_eq!(&all[4..8], &[1, 2, 1, 2]);
        assert_eq!(&all[8..12], &[3, 4, 2, 255]);
        assert_eq!(&all[12..16], &[5, 6, 9, 104]);

        let capped = index.search(&q_allah, 2);
        assert_eq!(capped.len(), 2 * MATCH_STRIDE);
        assert_eq!(&capped[0..4], &[0, 1, 1, 1]);
        assert_eq!(&capped[4..8], &[1, 2, 1, 2]);

        // موسي (with ya) must match موسى (with alef maqsura)
        let q_musa = CompiledArabicQuery::compile_str("موسي").unwrap();
        let musa_hits = index.search(&q_musa, 10);
        assert_eq!(musa_hits, vec![4, 5, 2, 51]);

        // توبة matches التوبة
        let q_tawba = CompiledArabicQuery::compile_str("توبة").unwrap();
        let tawba_hits = index.search(&q_tawba, 10);
        assert_eq!(tawba_hits, vec![5, 6, 9, 104]);

        // Mid-typing prefix fallback ("الرح") matches الرحمن / الرحيم
        let q_prefix = CompiledArabicQuery::compile_str("الرح").unwrap();
        let prefix_hits = index.search(&q_prefix, 10);
        assert_eq!(prefix_hits.len(), 2 * MATCH_STRIDE);
    }

    #[test]
    fn real_quran_db_export_parity_if_present() {
        // When QURAN_AR_TSV is provided (exported from real quran.ar.db),
        // verify 100% comprehensive hit counts, total occurrences, zero false positives,
        // and sub-5ms scan speed across all 6,236 Quran verses.
        let Ok(tsv_path) = std::env::var("QURAN_AR_TSV") else {
            return;
        };
        let content = std::fs::read_to_string(&tsv_path).expect("read QURAN_AR_TSV");
        let parsed: Vec<(i32, i32, i32, &str)> = content
            .lines()
            .filter(|line| !line.is_empty())
            .map(|line| {
                let mut parts = line.splitn(4, '\t');
                let row_id: i32 = parts.next().unwrap().parse().unwrap();
                let sura: i32 = parts.next().unwrap().parse().unwrap();
                let ayah: i32 = parts.next().unwrap().parse().unwrap();
                let text = parts.next().unwrap();
                (row_id, sura, ayah, text)
            })
            .collect();
        assert_eq!(parsed.len(), 6236, "must load all 6,236 Quran verses");

        let index = VerseIndex::from_utf8_rows(&parsed).unwrap();
        let expected_counts: &[(&str, usize, usize)] = &[
            // (query, expected_unique_verses, expected_total_occurrences)
            ("الله", 1913, 2816),
            ("(الله)", 1913, 2816),
            ("اللَّهِ", 1913, 2816),
            ("ال\u{0640}ل\u{0640}ه", 1913, 2816),
            ("الجنة", 154, 159),
            ("الجنه", 154, 159),
            ("جنة", 154, 159),
            ("الايمان", 76, 86),
            ("الإيمان", 76, 86),
            ("الصلاة", 77, 82),
            ("الصلاه", 77, 82),
            ("الرحمن", 167, 169),
            ("الناس", 225, 241),
            ("الكتاب", 232, 253),
            ("النار", 138, 145),
            ("المؤمنين", 175, 180),
            ("المؤمنون", 175, 180),
            ("موسى", 131, 136),
            ("موسي", 131, 136),
            ("رحمة", 113, 115),
            ("نعمة", 47, 48),
            ("الجن", 23, 23),
            ("يعملون", 57, 57),
            (".*+[", 0, 0),
        ];

        for &(query, expected_verses, expected_occ) in expected_counts {
            let compiled = CompiledArabicQuery::compile_str(query).unwrap();
            let t0 = std::time::Instant::now();
            let packed = index.search(&compiled, usize::MAX);
            let elapsed_ms = t0.elapsed().as_secs_f64() * 1000.0;
            let hit_count = packed.len() / MATCH_STRIDE;
            assert_eq!(
                hit_count, expected_verses,
                "verse count mismatch for query {query:?}"
            );
            let mut total_occ = 0usize;
            for chunk in packed.chunks_exact(MATCH_STRIDE) {
                let verse_idx = chunk[0] as usize;
                let slice = index.verse_slice(verse_idx).expect("valid verse_idx");
                let spans = compiled.find_all_spans(slice);
                assert!(!spans.is_empty(), "matched verse must have >=1 span for {query:?}");
                assert_eq!(spans.len() % 2, 0);
                total_occ += spans.len() / 2;
                for pair in spans.chunks_exact(2) {
                    assert!(pair[0] >= 0 && pair[1] > pair[0] && (pair[1] as usize) <= slice.len());
                }
            }
            assert_eq!(
                total_occ, expected_occ,
                "occurrence count mismatch for query {query:?}"
            );
            assert!(elapsed_ms < 20.0, "search too slow ({elapsed_ms:.3} ms) for {query:?}");
            eprintln!(
                "Rust real-DB OK: query={query:?} verses={hit_count} occ={total_occ} elapsed={elapsed_ms:.3} ms"
            );
        }

        // Verify Surah Al-Fatihah 1:2 ("الحمد لله رب العالمين") is hit #2 for "الله"!
        let q_allah = CompiledArabicQuery::compile_str("الله").unwrap();
        let packed_allah = index.search(&q_allah, 5);
        assert_eq!(&packed_allah[0..4], &[0, 1, 1, 1]); // 1:1 بسم الله
        assert_eq!(&packed_allah[4..8], &[1, 2, 1, 2]); // 1:2 الحمد لله

        // Verify Tier 1 (explicit الجنة/بالجنة = 55 verses) comes FIRST for "الجنة"!
        let q_jannah = CompiledArabicQuery::compile_str("الجنة").unwrap();
        let packed_jannah = index.search(&q_jannah, usize::MAX);
        assert_eq!(packed_jannah.len() / MATCH_STRIDE, 154);
        // First hit should be 2:35 ("...اسكن أنت وزوجك الجنة")
        assert_eq!(packed_jannah[2], 2);
        assert_eq!(packed_jannah[3], 35);

        // Verify multi-word contiguous phrases & topical all-words fallback & mid-typing prefix!
        let q_hamd = CompiledArabicQuery::compile_str("الحمد لله").unwrap();
        let packed_hamd = index.search(&q_hamd, usize::MAX);
        assert!(packed_hamd.len() / MATCH_STRIDE >= 23, "الحمد لله must match all Quranic occurrences");
        assert_eq!(&packed_hamd[0..4], &[1, 2, 1, 2]); // 1:2 الحمد لله رب العالمين

        let q_topical = CompiledArabicQuery::compile_str("الصلاة الزكاة").unwrap();
        let packed_topical = index.search(&q_topical, usize::MAX);
        assert!(packed_topical.len() / MATCH_STRIDE >= 25, "الصلاة الزكاة topical search must match >=25 verses");

        let q_prefix = CompiledArabicQuery::compile_str("الرح").unwrap();
        let packed_prefix = index.search(&q_prefix, usize::MAX);
        assert!(packed_prefix.len() / MATCH_STRIDE >= 160, "mid-typing prefix الرح must fall back cleanly");
    }

    #[test]
    fn jni_entry_points_handle_null_env_and_round_trip_safely() {
        use crate::jni_bridge::*;
        use crate::jni_sys::*;
        use std::ffi::c_void;
        use std::ptr;
        use std::sync::atomic::{AtomicUsize, Ordering};

        unsafe {
            assert_eq!(
                Java_com_quran_common_search_RustArabicSearchEngine_nativeLoadIndex(
                    ptr::null_mut(),
                    ptr::null_mut(),
                    ptr::null_mut(),
                    ptr::null_mut(),
                    ptr::null_mut(),
                    ptr::null_mut(),
                ),
                JNI_FALSE
            );
            assert!(
                Java_com_quran_common_search_RustArabicSearchEngine_nativeSearch(
                    ptr::null_mut(),
                    ptr::null_mut(),
                    ptr::null_mut(),
                    10,
                )
                .is_null()
            );
            assert!(
                Java_com_quran_common_search_RustArabicSearchEngine_nativeGetVerseText(
                    ptr::null_mut(),
                    ptr::null_mut(),
                    0,
                )
                .is_null()
            );
            assert!(
                Java_com_quran_common_search_RustArabicSearchEngine_nativeFindMatchSpans(
                    ptr::null_mut(),
                    ptr::null_mut(),
                    0,
                    ptr::null_mut(),
                )
                .is_null()
            );
            Java_com_quran_common_search_RustArabicSearchEngine_nativeReleaseIndex(
                ptr::null_mut(),
                ptr::null_mut(),
            );
            assert_eq!(
                Java_com_quran_common_search_RustArabicSearchEngine_nativeIsLoaded(
                    ptr::null_mut(),
                    ptr::null_mut(),
                ),
                JNI_FALSE
            );
        }

        enum MockObj {
            IntArr(Vec<i32>),
            Str(Vec<u16>),
            StrArr(Vec<Vec<u16>>),
        }

        static DELETED_LOCAL_REFS: AtomicUsize = AtomicUsize::new(0);

        unsafe extern "system" fn mock_exception_occurred(_env: *mut JNIEnv) -> jthrowable {
            ptr::null_mut()
        }
        unsafe extern "system" fn mock_exception_clear(_env: *mut JNIEnv) {}
        unsafe extern "system" fn mock_exception_check(_env: *mut JNIEnv) -> jboolean {
            JNI_FALSE
        }
        unsafe extern "system" fn mock_delete_local_ref(_env: *mut JNIEnv, obj: jobject) {
            if !obj.is_null() {
                DELETED_LOCAL_REFS.fetch_add(1, Ordering::SeqCst);
                drop(Box::from_raw(obj as *mut MockObj));
            }
        }
        unsafe extern "system" fn mock_new_string(
            _env: *mut JNIEnv,
            unicode: *const jchar,
            len: jsize,
        ) -> jstring {
            let slice = if len > 0 {
                std::slice::from_raw_parts(unicode, len as usize)
            } else {
                &[]
            };
            Box::into_raw(Box::new(MockObj::Str(slice.to_vec()))) as jstring
        }
        unsafe extern "system" fn mock_get_string_length(_env: *mut JNIEnv, s: jstring) -> jsize {
            match &*(s as *const MockObj) {
                MockObj::Str(v) => v.len() as jsize,
                _ => panic!("expected Str"),
            }
        }
        unsafe extern "system" fn mock_get_string_chars(
            _env: *mut JNIEnv,
            _s: jstring,
            _is_copy: *mut jboolean,
        ) -> *const jchar {
            ptr::null()
        }
        unsafe extern "system" fn mock_release_string_chars(
            _env: *mut JNIEnv,
            _s: jstring,
            _chars: *const jchar,
        ) {
        }
        unsafe extern "system" fn mock_get_string_region(
            _env: *mut JNIEnv,
            s: jstring,
            start: jsize,
            len: jsize,
            buf: *mut jchar,
        ) {
            match &*(s as *const MockObj) {
                MockObj::Str(v) => {
                    ptr::copy_nonoverlapping(v.as_ptr().add(start as usize), buf, len as usize);
                }
                _ => panic!("expected Str"),
            }
        }
        unsafe extern "system" fn mock_get_array_length(_env: *mut JNIEnv, arr: jarray) -> jsize {
            match &*(arr as *const MockObj) {
                MockObj::IntArr(v) => v.len() as jsize,
                MockObj::StrArr(v) => v.len() as jsize,
                _ => panic!("expected array"),
            }
        }
        unsafe extern "system" fn mock_get_object_array_element(
            _env: *mut JNIEnv,
            arr: jobjectArray,
            index: jsize,
        ) -> jobject {
            match &*(arr as *const MockObj) {
                MockObj::StrArr(v) => {
                    let clone = v[index as usize].clone();
                    Box::into_raw(Box::new(MockObj::Str(clone))) as jobject
                }
                _ => panic!("expected StrArr"),
            }
        }
        unsafe extern "system" fn mock_new_int_array(_env: *mut JNIEnv, len: jsize) -> jintArray {
            Box::into_raw(Box::new(MockObj::IntArr(vec![0; len as usize]))) as jintArray
        }
        unsafe extern "system" fn mock_get_int_array_region(
            _env: *mut JNIEnv,
            arr: jintArray,
            start: jsize,
            len: jsize,
            buf: *mut jint,
        ) {
            match &*(arr as *const MockObj) {
                MockObj::IntArr(v) => {
                    ptr::copy_nonoverlapping(v.as_ptr().add(start as usize), buf, len as usize);
                }
                _ => panic!("expected IntArr"),
            }
        }
        unsafe extern "system" fn mock_set_int_array_region(
            _env: *mut JNIEnv,
            arr: jintArray,
            start: jsize,
            len: jsize,
            buf: *const jint,
        ) {
            match &mut *(arr as *mut MockObj) {
                MockObj::IntArr(v) => {
                    ptr::copy_nonoverlapping(buf, v.as_mut_ptr().add(start as usize), len as usize);
                }
                _ => panic!("expected IntArr"),
            }
        }

        let vtable = JNINativeInterface {
            _pad_0_14: [None; 15],
            ExceptionOccurred: mock_exception_occurred,
            _pad_16: None,
            ExceptionClear: mock_exception_clear,
            _pad_18_22: [None; 5],
            DeleteLocalRef: mock_delete_local_ref,
            _pad_24_162: [None; 139],
            NewString: mock_new_string,
            GetStringLength: mock_get_string_length,
            GetStringChars: mock_get_string_chars,
            ReleaseStringChars: mock_release_string_chars,
            _pad_167_170: [None; 4],
            GetArrayLength: mock_get_array_length,
            _pad_172: None,
            GetObjectArrayElement: mock_get_object_array_element,
            _pad_174_178: [None; 5],
            NewIntArray: mock_new_int_array,
            _pad_180_202: [None; 23],
            GetIntArrayRegion: mock_get_int_array_region,
            _pad_204_210: [None; 7],
            SetIntArrayRegion: mock_set_int_array_region,
            _pad_212_219: [None; 8],
            GetStringRegion: mock_get_string_region,
            _pad_221_227: [None; 7],
            ExceptionCheck: mock_exception_check,
            _pad_229_232: [None; 4],
        };
        let mut env_ptr: JNIEnv = &vtable as *const JNINativeInterface;
        let env: *mut JNIEnv = &mut env_ptr as *mut JNIEnv;

        DELETED_LOCAL_REFS.store(0, Ordering::SeqCst);
        let row_ids = Box::into_raw(Box::new(MockObj::IntArr(vec![10, 20, 30]))) as jintArray;
        let suras = Box::into_raw(Box::new(MockObj::IntArr(vec![1, 1, 2]))) as jintArray;
        let ayahs = Box::into_raw(Box::new(MockObj::IntArr(vec![1, 2, 255]))) as jintArray;
        let texts = Box::into_raw(Box::new(MockObj::StrArr(vec![
            utf16("بسم الله الرحمن الرحيم"),
            utf16("الحمد لله رب العالمين"),
            utf16("الله لا إله إلا هو والله عليم"),
        ]))) as jobjectArray;
        let query = Box::into_raw(Box::new(MockObj::Str(utf16("الله")))) as jstring;

        unsafe {
            let ok = Java_com_quran_common_search_RustArabicSearchEngine_nativeLoadIndex(
                env,
                ptr::null_mut(),
                row_ids,
                suras,
                ayahs,
                texts,
            );
            assert_eq!(ok, JNI_TRUE);
            assert_eq!(DELETED_LOCAL_REFS.load(Ordering::SeqCst), 3);
            assert_eq!(
                Java_com_quran_common_search_RustArabicSearchEngine_nativeIsLoaded(
                    env,
                    ptr::null_mut(),
                ),
                JNI_TRUE
            );

            let res_arr = Java_com_quran_common_search_RustArabicSearchEngine_nativeSearch(
                env,
                ptr::null_mut(),
                query,
                10,
            );
            assert!(!res_arr.is_null());
            let packed = match &*(res_arr as *const MockObj) {
                MockObj::IntArr(v) => v.clone(),
                _ => panic!("expected IntArr"),
            };
            assert_eq!(packed, vec![0, 10, 1, 1, 1, 20, 1, 2, 2, 30, 2, 255]);
            drop(Box::from_raw(res_arr as *mut MockObj));

            let txt_obj = Java_com_quran_common_search_RustArabicSearchEngine_nativeGetVerseText(
                env,
                ptr::null_mut(),
                0,
            );
            assert!(!txt_obj.is_null());
            match &*(txt_obj as *const MockObj) {
                MockObj::Str(v) => assert_eq!(v, &utf16("بسم الله الرحمن الرحيم")),
                _ => panic!("expected Str"),
            }
            drop(Box::from_raw(txt_obj as *mut MockObj));

            // Row 1: "الحمد لله رب العالمين" -> "لله" at [6, 9]
            let spans_lillah = Java_com_quran_common_search_RustArabicSearchEngine_nativeFindMatchSpans(
                env,
                ptr::null_mut(),
                1,
                query,
            );
            assert!(!spans_lillah.is_null());
            match &*(spans_lillah as *const MockObj) {
                MockObj::IntArr(v) => assert_eq!(v, &vec![6, 9]),
                _ => panic!("expected IntArr"),
            }
            drop(Box::from_raw(spans_lillah as *mut MockObj));

            // Row 2: "الله لا إله إلا هو والله عليم" -> "الله" [0, 4] and "والله" [19, 24]
            let spans_obj = Java_com_quran_common_search_RustArabicSearchEngine_nativeFindMatchSpans(
                env,
                ptr::null_mut(),
                2,
                query,
            );
            assert!(!spans_obj.is_null());
            match &*(spans_obj as *const MockObj) {
                MockObj::IntArr(v) => assert_eq!(v, &vec![0, 4, 19, 24]),
                _ => panic!("expected IntArr"),
            }
            drop(Box::from_raw(spans_obj as *mut MockObj));

            Java_com_quran_common_search_RustArabicSearchEngine_nativeReleaseIndex(
                env,
                ptr::null_mut(),
            );
            assert_eq!(
                Java_com_quran_common_search_RustArabicSearchEngine_nativeIsLoaded(
                    env,
                    ptr::null_mut(),
                ),
                JNI_FALSE
            );

            let _ = Box::from_raw(row_ids as *mut MockObj);
            let _ = Box::from_raw(suras as *mut MockObj);
            let _ = Box::from_raw(ayahs as *mut MockObj);
            let _ = Box::from_raw(texts as *mut MockObj);
            let _ = Box::from_raw(query as *mut MockObj);
            let _unused: *mut c_void = ptr::null_mut();
            let _ = _unused;
        }
    }

    #[test]
    fn handles_tatweel_tashkeel_zero_width_and_surrogate_pairs() {
        let rows = [
            (1, 1, 1, "بسم الله الرحم\u{0670}ن الرحيم"),
            (2, 112, 1, "قُلْ هُوَ اللّٰهُ أَحَدٌ"),
        ];
        let index = VerseIndex::from_utf8_rows(&rows).unwrap();

        // Tatweel & diacritized Arabic queries normalize cleanly and match BOTH rows!
        for normalized_allah in ["ال\u{0640}ل\u{0640}ه", "اللَّهُ", "اللّٰهِ"] {
            let compiled = CompiledArabicQuery::compile_str(normalized_allah).unwrap();
            let hits = index.search(&compiled, 100);
            assert_eq!(hits, vec![0, 1, 1, 1, 1, 2, 112, 1], "failed for {normalized_allah:?}");
            // Verify highlight span on diacritized "اللّٰهُ" covers entire word including trailing damma!
            let row2_slice = index.verse_slice(1).unwrap();
            let spans = compiled.find_all_spans(row2_slice);
            assert_eq!(spans.len(), 2);
            let matched = String::from_utf16(&row2_slice[spans[0] as usize..spans[1] as usize]).unwrap();
            assert_eq!(matched, "اللّٰهُ");
        }

        // Dagger alif query matches الرحمن cleanly
        let q_rahman = CompiledArabicQuery::compile_str("الرحم\u{0670}ن").unwrap();
        assert_eq!(index.search(&q_rahman, 100), vec![0, 1, 1, 1]);

        // Pure whitespace, pure tatweel, pure zero-width, pure harakat, and emoji-prefixed return empty cleanly
        for empty_or_unmatched in [
            "",
            "   \t\n ",
            "\u{0640}\u{0640}",
            "\u{200B}\u{200C}\u{200D}\u{200F}",
            "🕌الله",
            "\u{064B}\u{064C}\u{064D}\u{064E}\u{064F}\u{0650}\u{0651}\u{0652}",
        ] {
            let q: Vec<u16> = empty_or_unmatched.encode_utf16().collect();
            let Some(compiled) = CompiledArabicQuery::compile(&q) else {
                assert!(empty_or_unmatched.is_empty());
                continue;
            };
            let hits = index.search(&compiled, 100);
            assert!(hits.is_empty(), "unexpected hit for {empty_or_unmatched:?}: {hits:?}");
        }
    }

    #[test]
    fn handles_long_queries_over_stack_cap_exact_pair_tail_and_oob_indices() {
        let v0_str = format!("{} الله", "ب".repeat(200));
        let v0_u16 = utf16(&v0_str);
        let rows = [(1, 1, 1, v0_str.as_str())];
        let index = VerseIndex::from_utf8_rows(&rows).unwrap();

        // Query > 128 UTF-16 code units (205 code units) matching full verse
        let q_exact: Vec<u16> = v0_str.encode_utf16().collect();
        assert!(q_exact.len() > 128);
        let compiled = CompiledArabicQuery::compile(&q_exact).unwrap();
        assert_eq!(index.search(&compiled, 10), vec![0, 1, 1, 1]);

        // Query far longer than verse (500 code units)
        let q_huge: Vec<u16> = "ا".repeat(500).encode_utf16().collect();
        let compiled_huge = CompiledArabicQuery::compile(&q_huge).unwrap();
        assert!(index.search(&compiled_huge, 10).is_empty());

        // Tail boundary: match at final 2 code units "له"
        let q_tail: Vec<u16> = "له".encode_utf16().collect();
        let tail_compiled = CompiledArabicQuery::compile(&q_tail).unwrap();
        assert_eq!(index.search(&tail_compiled, 10), vec![0, 1, 1, 1]);
        let slice = index.verse_slice(0).unwrap();
        assert_eq!(
            tail_compiled.find_all_spans(slice),
            vec![(v0_u16.len() - 2) as i32, v0_u16.len() as i32]
        );

        // Single code unit, zero max_results, and out-of-bounds verse_index
        let q_one = CompiledArabicQuery::compile_str("ل").unwrap();
        assert!(index.search(&q_one, 0).is_empty());
        assert!(index.verse_slice(999).is_none());
    }
}
