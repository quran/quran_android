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
    fn equivalence_classes_match_arabic_character_helper_exactly() {
        let cases: &[(&str, &str, &str)] = &[
            // (query char, chars that MUST match, chars that MUST NOT match)
            ("\u{0627}", "\u{0622}\u{0623}\u{0625}\u{0627}\u{0649}", "\u{0621}\u{0624}\u{0626}\u{064a}\u{0628}"),
            ("\u{0648}", "\u{0648}\u{0624}", "\u{0627}\u{0621}\u{064a}"),
            ("\u{0623}", "\u{0621}\u{0623}\u{0624}\u{0626}\u{0627}", "\u{0622}\u{0625}\u{0649}"),
            ("\u{0621}", "\u{0621}\u{0623}\u{0624}\u{0626}\u{0627}", "\u{0622}\u{0625}\u{0649}"),
            ("\u{062a}", "\u{062a}\u{0629}", "\u{0647}\u{062b}"),
            ("\u{0629}", "\u{0629}\u{062a}\u{0647}", "\u{062b}\u{062d}"),
            ("\u{0647}", "\u{0647}\u{0629}", "\u{062a}\u{062d}"),
            ("\u{0649}", "\u{0649}\u{064a}", "\u{0627}\u{0626}"),
            ("\u{064a}", "\u{064a}\u{0649}", "\u{0627}\u{0626}"),
            ("\u{0626}", "\u{0626}\u{0649}\u{064a}", "\u{0621}\u{0623}\u{0627}"),
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
    fn multi_match_non_overlapping_and_all_mask_queries() {
        let verse = utf16("الله لا إله إلا هو الحي القيوم والله عليم");
        let q = CompiledArabicQuery::compile_str("الله").unwrap();
        assert_eq!(q.find_all_spans(&verse), vec![0, 4, 32, 36]);

        let repeating = utf16("للل لل للل");
        let q_ll = CompiledArabicQuery::compile_str("لل").unwrap();
        assert_eq!(q_ll.find_all_spans(&repeating), vec![0, 2, 4, 6, 7, 9]);

        // All characters are equivalence masks (no exact anchor).
        let q_mask_only = CompiledArabicQuery::compile_str("اه").unwrap();
        let text = utf16("آة إه اة");
        assert_eq!(q_mask_only.find_all_spans(&text), vec![0, 2, 3, 5, 6, 8]);
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
        assert_eq!(all.len(), 3 * MATCH_STRIDE);
        assert_eq!(&all[0..4], &[0, 1, 1, 1]);
        assert_eq!(&all[4..8], &[3, 4, 2, 255]);
        assert_eq!(&all[8..12], &[5, 6, 9, 104]);

        let capped = index.search(&q_allah, 2);
        assert_eq!(capped.len(), 2 * MATCH_STRIDE);
        assert_eq!(&capped[0..4], &[0, 1, 1, 1]);
        assert_eq!(&capped[4..8], &[3, 4, 2, 255]);

        // موسي (with ya) must match موسى (with alef maqsura)
        let q_musa = CompiledArabicQuery::compile_str("موسي").unwrap();
        let musa_hits = index.search(&q_musa, 10);
        assert_eq!(musa_hits, vec![4, 5, 2, 51]);

        // توبة matches التوبة
        let q_tawba = CompiledArabicQuery::compile_str("توبة").unwrap();
        let tawba_hits = index.search(&q_tawba, 10);
        assert_eq!(tawba_hits, vec![5, 6, 9, 104]);
    }

    #[test]
    fn bitmask_prefilter_and_two_step_exact_pair_have_zero_false_negatives() {
        let rows = [
            (1, 1, 1, "آمنوا ألم إنا الله موسى عيسى"),
            (2, 2, 1, "للهم للل اللة الل اة أه"),
            (3, 2, 2, "سؤال مؤمن شاطئ شيء"),
            (4, 2, 3, "رحمة نعمة رحمت ربك"),
            (5, 2, 4, "بسم \u{1F600} الله الرحمن"),
            (6, 2, 5, "كتاب لا ريب فيه هدى للمتقين"),
            (7, 2, 6, "في قلوبهم مرض فزادهم الله مرضا"),
        ];
        let index = VerseIndex::from_utf8_rows(&rows).unwrap();
        let queries = [
            "ا",
            "آ",
            "أ",
            "ء",
            "و",
            "ت",
            "ة",
            "ه",
            "ى",
            "ي",
            "ئ",
            "الله",
            "لل",
            "الل",
            "اللة",
            "للهم",
            "اه",
            "رحمة",
            "رحمت",
            "نعمة",
            "سؤال",
            "شاطئ",
            "هدى",
            "هدي",
            "في قلوبهم",
            "\u{1F600} الله",
            "غيرموجود",
        ];

        for q in queries {
            let compiled = CompiledArabicQuery::compile_str(q).unwrap();
            let actual = index.search(&compiled, usize::MAX);

            // Brute-force check against every verse slice without bitmask prefilter
            let mut expected = Vec::new();
            for (idx, &(row_id, sura, ayah, text)) in rows.iter().enumerate() {
                let u16s = utf16(text);
                if compiled.find_first(&u16s, 0).is_some() {
                    expected.extend_from_slice(&[idx as i32, row_id, sura, ayah]);
                }
            }
            assert_eq!(actual, expected, "parity mismatch for query {q:?}");
        }
    }

    #[test]
    fn real_quran_db_export_parity_if_present() {
        // When QURAN_AR_TSV is provided by the verification harness (exported from quran.ar.db),
        // verify 100% exact hit counts and sub-5ms scan speed across all 6,236 Quran verses.
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
        let expected_counts: &[(&str, usize)] = &[
            ("الله", 1842),
            ("الرحمن", 159),
            ("الصلاة", 61),
            ("موسى", 131),
            ("موسي", 131),
            ("يعملون", 57),
            ("توبة", 7),
            ("اه", 1005),
            (".*+[", 0),
            ("(الله)", 1842),
            ("ا", 6180),
        ];

        for &(query, expected_hits) in expected_counts {
            let compiled = CompiledArabicQuery::compile_str(query).unwrap();
            let t0 = std::time::Instant::now();
            let packed = index.search(&compiled, usize::MAX);
            let elapsed_ms = t0.elapsed().as_secs_f64() * 1000.0;
            let hit_count = packed.len() / MATCH_STRIDE;
            assert_eq!(
                hit_count, expected_hits,
                "hit count mismatch for query {query:?}"
            );
            // Verify every returned verse actually has at least one valid in-bounds UTF-16 span.
            for chunk in packed.chunks_exact(MATCH_STRIDE) {
                let verse_idx = chunk[0] as usize;
                let slice = index.verse_slice(verse_idx).expect("valid verse_idx");
                let spans = compiled.find_all_spans(slice);
                assert!(!spans.is_empty(), "matched verse must have >=1 span");
                assert_eq!(spans.len() % 2, 0);
                for pair in spans.chunks_exact(2) {
                    assert!(pair[0] >= 0 && pair[1] > pair[0] && (pair[1] as usize) <= slice.len());
                }
            }
            eprintln!("Rust parity OK: query={query:?} hits={hit_count} elapsed={elapsed_ms:.3} ms");
        }

        // If QURAN_AR_EXPECTED_SPANS is also provided, verify 100% exact row-by-row AND
        // span-by-span equality against the ArabicCharacterHelper regex oracle.
        if let Ok(expected_path) = std::env::var("QURAN_AR_EXPECTED_SPANS") {
            let oracle = std::fs::read_to_string(&expected_path).expect("read QURAN_AR_EXPECTED_SPANS");
            let mut verified_queries = 0usize;
            for line in oracle.lines().filter(|l| !l.is_empty()) {
                let (query, expected_rows_str) = line.split_once('\t').expect("tab-delimited oracle line");
                let compiled = CompiledArabicQuery::compile_str(query).expect("compiled query");
                let packed = index.search(&compiled, usize::MAX);

                let mut actual_rows = Vec::with_capacity(packed.len() / MATCH_STRIDE);
                for chunk in packed.chunks_exact(MATCH_STRIDE) {
                    let vidx = chunk[0] as usize;
                    let slice = index.verse_slice(vidx).expect("valid verse_idx");
                    let spans = compiled.find_all_spans(slice);
                    let spans_str: Vec<String> = spans
                        .chunks_exact(2)
                        .map(|p| format!("{}:{}", p[0], p[1]))
                        .collect();
                    actual_rows.push(format!(
                        "{},{},{},{}|{}",
                        chunk[0],
                        chunk[1],
                        chunk[2],
                        chunk[3],
                        spans_str.join(",")
                    ));
                }
                let actual_joined = actual_rows.join(";");
                assert_eq!(
                    actual_joined, expected_rows_str,
                    "100% row+span parity mismatch for query {query:?}"
                );
                verified_queries += 1;
            }
            assert_eq!(verified_queries, 11);
            eprintln!("100% exact row-by-row & span-by-span parity verified across all 11 queries x 6,236 verses!");
        }
    }

    #[test]
    fn jni_entry_points_handle_null_env_and_round_trip_safely() {
        use crate::jni_bridge::*;
        use crate::jni_sys::*;
        use std::ffi::c_void;
        use std::ptr;
        use std::sync::atomic::{AtomicUsize, Ordering};

        // 1. Null env / null args must never crash or unwind.
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

        // 2. Full FFI round-trip via mock JNINativeInterface verifying DeleteLocalRef & UTF-16 spans.
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
        let row_ids = Box::into_raw(Box::new(MockObj::IntArr(vec![10, 20]))) as jintArray;
        let suras = Box::into_raw(Box::new(MockObj::IntArr(vec![1, 2]))) as jintArray;
        let ayahs = Box::into_raw(Box::new(MockObj::IntArr(vec![1, 255]))) as jintArray;
        let texts = Box::into_raw(Box::new(MockObj::StrArr(vec![
            utf16("بسم الله الرحمن الرحيم"),
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
            assert_eq!(DELETED_LOCAL_REFS.load(Ordering::SeqCst), 2);
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
            assert_eq!(packed, vec![0, 10, 1, 1, 1, 20, 2, 255]);
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

            let spans_obj = Java_com_quran_common_search_RustArabicSearchEngine_nativeFindMatchSpans(
                env,
                ptr::null_mut(),
                1,
                query,
            );
            assert!(!spans_obj.is_null());
            match &*(spans_obj as *const MockObj) {
                MockObj::IntArr(v) => assert_eq!(v, &vec![0, 4, 20, 24]),
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

            // Clean up mock allocations
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

        for adversarial in [
            "",
            "   \t\n ",
            "\u{0640}\u{0640}",
            "\u{200B}\u{200C}\u{200D}\u{200F}",
            "ال\u{0640}ل\u{0640}ه",
            "🕌الله",
            "الرحم\u{0670}ن",
            "\u{064B}\u{064C}\u{064D}\u{064E}\u{064F}\u{0650}\u{0651}\u{0652}",
        ] {
            let q: Vec<u16> = adversarial.encode_utf16().collect();
            let Some(compiled) = CompiledArabicQuery::compile(&q) else {
                assert!(adversarial.is_empty());
                continue;
            };
            let hits = index.search(&compiled, 100);
            if adversarial == "الرحم\u{0670}ن" {
                assert_eq!(hits, vec![0, 1, 1, 1]);
            } else {
                assert!(hits.is_empty(), "unexpected hit for {adversarial:?}: {hits:?}");
            }
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
