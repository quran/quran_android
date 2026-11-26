//! Panic-safe JNI boundary for `com.quran.common.search.RustArabicSearchEngine`.
//!
//! Every exported FFI symbol wraps its logic in `std::panic::catch_unwind` and checks
//! `ExceptionCheck` after every fallible JNI call, guaranteeing we never unwind across FFI
//! or trigger ART CheckJNI aborts.

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;
use std::sync::RwLock;

use crate::engine::{
    compute_verse_letter_mask, CompiledArabicQuery, VerseIndex, VerseMeta, MAX_ARENA_CODE_UNITS,
    MAX_VERSES,
};
use crate::jni_sys::{
    jboolean, jclass, jint, jintArray, jobjectArray, jsize, jstring, JNIEnv, JNINativeInterface,
    JNI_FALSE, JNI_TRUE,
};

static INDEX: RwLock<Option<VerseIndex>> = RwLock::new(None);

/// Stack buffer threshold for query UTF-16 code units (zero heap allocation for typical queries).
const QUERY_STACK_CAP: usize = 128;

#[inline]
unsafe fn vtable(env: *mut JNIEnv) -> Option<&'static JNINativeInterface> {
    if env.is_null() {
        return None;
    }
    let funcs = *env;
    if funcs.is_null() {
        return None;
    }
    Some(&*funcs)
}

#[inline]
unsafe fn has_pending_exception(env: *mut JNIEnv, fns: &JNINativeInterface) -> bool {
    (fns.ExceptionCheck)(env) != JNI_FALSE
}

unsafe fn new_int_array(env: *mut JNIEnv, fns: &JNINativeInterface, data: &[i32]) -> jintArray {
    if has_pending_exception(env, fns) {
        return ptr::null_mut();
    }
    let Ok(len) = jsize::try_from(data.len()) else {
        return ptr::null_mut();
    };
    let arr = (fns.NewIntArray)(env, len);
    if arr.is_null() || has_pending_exception(env, fns) {
        return ptr::null_mut();
    }
    if len > 0 {
        (fns.SetIntArrayRegion)(env, arr, 0, len, data.as_ptr());
        if has_pending_exception(env, fns) {
            return ptr::null_mut();
        }
    }
    arr
}

unsafe fn read_int_array(
    env: *mut JNIEnv,
    fns: &JNINativeInterface,
    array: jintArray,
    expected_len: usize,
) -> Option<Vec<i32>> {
    if array.is_null() || has_pending_exception(env, fns) {
        return None;
    }
    let len = (fns.GetArrayLength)(env, array);
    if len < 0 || len as usize != expected_len || has_pending_exception(env, fns) {
        return None;
    }
    let mut out = vec![0i32; expected_len];
    if expected_len > 0 {
        (fns.GetIntArrayRegion)(env, array, 0, len, out.as_mut_ptr());
        if has_pending_exception(env, fns) {
            return None;
        }
    }
    Some(out)
}

/// Executes `f` with the UTF-16 code units of `jstr`, using a stack buffer when small.
unsafe fn with_jstring_u16<R>(
    env: *mut JNIEnv,
    fns: &JNINativeInterface,
    jstr: jstring,
    f: impl FnOnce(&[u16]) -> R,
) -> Option<R> {
    if jstr.is_null() || has_pending_exception(env, fns) {
        return None;
    }
    let len = (fns.GetStringLength)(env, jstr);
    if len < 0 || has_pending_exception(env, fns) {
        return None;
    }
    let len_usize = len as usize;
    if len_usize == 0 {
        return Some(f(&[]));
    }
    if len_usize <= QUERY_STACK_CAP {
        let mut stack_buf = [0u16; QUERY_STACK_CAP];
        (fns.GetStringRegion)(env, jstr, 0, len, stack_buf.as_mut_ptr());
        if has_pending_exception(env, fns) {
            return None;
        }
        Some(f(&stack_buf[..len_usize]))
    } else {
        if len_usize > MAX_ARENA_CODE_UNITS {
            return None;
        }
        let mut heap_buf = vec![0u16; len_usize];
        (fns.GetStringRegion)(env, jstr, 0, len, heap_buf.as_mut_ptr());
        if has_pending_exception(env, fns) {
            return None;
        }
        Some(f(&heap_buf))
    }
}

unsafe fn load_index_impl(
    env: *mut JNIEnv,
    row_ids_arr: jintArray,
    suras_arr: jintArray,
    ayahs_arr: jintArray,
    texts_arr: jobjectArray,
) -> jboolean {
    let Some(fns) = vtable(env) else {
        return JNI_FALSE;
    };
    if row_ids_arr.is_null()
        || suras_arr.is_null()
        || ayahs_arr.is_null()
        || texts_arr.is_null()
        || has_pending_exception(env, fns)
    {
        return JNI_FALSE;
    }

    let count_raw = (fns.GetArrayLength)(env, row_ids_arr);
    if count_raw < 0 || has_pending_exception(env, fns) {
        return JNI_FALSE;
    }
    let count = count_raw as usize;
    if count > MAX_VERSES {
        return JNI_FALSE;
    }

    let texts_len = (fns.GetArrayLength)(env, texts_arr);
    if texts_len != count_raw || has_pending_exception(env, fns) {
        return JNI_FALSE;
    }

    let Some(row_ids) = read_int_array(env, fns, row_ids_arr, count) else {
        return JNI_FALSE;
    };
    let Some(suras) = read_int_array(env, fns, suras_arr, count) else {
        return JNI_FALSE;
    };
    let Some(ayahs) = read_int_array(env, fns, ayahs_arr, count) else {
        return JNI_FALSE;
    };

    let mut verses = Vec::with_capacity(count);
    let mut arena = Vec::with_capacity(count.saturating_mul(68).min(MAX_ARENA_CODE_UNITS));

    for i in 0..count {
        let elem = (fns.GetObjectArrayElement)(env, texts_arr, i as jsize);
        if has_pending_exception(env, fns) {
            if !elem.is_null() {
                (fns.DeleteLocalRef)(env, elem);
            }
            return JNI_FALSE;
        }

        let offset_usize = arena.len();
        let Ok(offset) = u32::try_from(offset_usize) else {
            if !elem.is_null() {
                (fns.DeleteLocalRef)(env, elem);
            }
            return JNI_FALSE;
        };

        let mut str_len_usize = 0usize;
        let mut letter_mask = 0u64;
        if !elem.is_null() {
            let jstr = elem as jstring;
            let raw_len = (fns.GetStringLength)(env, jstr);
            if raw_len < 0 || has_pending_exception(env, fns) {
                (fns.DeleteLocalRef)(env, elem);
                return JNI_FALSE;
            }
            str_len_usize = raw_len as usize;
            let Some(new_total) = offset_usize.checked_add(str_len_usize) else {
                (fns.DeleteLocalRef)(env, elem);
                return JNI_FALSE;
            };
            if new_total > MAX_ARENA_CODE_UNITS || u32::try_from(str_len_usize).is_err() {
                (fns.DeleteLocalRef)(env, elem);
                return JNI_FALSE;
            }
            if str_len_usize > 0 {
                arena.resize(new_total, 0u16);
                let dst = arena.as_mut_ptr().add(offset_usize);
                (fns.GetStringRegion)(env, jstr, 0, raw_len, dst);
                if has_pending_exception(env, fns) {
                    (fns.DeleteLocalRef)(env, elem);
                    return JNI_FALSE;
                }
                letter_mask = compute_verse_letter_mask(&arena[offset_usize..new_total]);
            }
            // Crucial on Android ART: free each local ref inside the 6,236-row loop
            // so we never exhaust the 512-entry JNI local reference table.
            (fns.DeleteLocalRef)(env, elem);
        }

        verses.push(VerseMeta {
            row_id: row_ids[i],
            sura: suras[i],
            ayah: ayahs[i],
            offset,
            len: str_len_usize as u32,
            letter_mask,
        });
    }

    let index = VerseIndex::new(verses, arena);
    let mut guard = INDEX.write().unwrap_or_else(|e| e.into_inner());
    *guard = Some(index);
    JNI_TRUE
}

unsafe fn is_loaded_impl() -> jboolean {
    let guard = INDEX.read().unwrap_or_else(|e| e.into_inner());
    if guard.is_some() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

unsafe fn search_impl(env: *mut JNIEnv, query: jstring, max_results: jint) -> jintArray {
    let Some(fns) = vtable(env) else {
        return ptr::null_mut();
    };
    if max_results <= 0 {
        return new_int_array(env, fns, &[]);
    }
    let compiled = match with_jstring_u16(env, fns, query, CompiledArabicQuery::compile) {
        Some(Some(c)) => c,
        Some(None) => {
            let guard = INDEX.read().unwrap_or_else(|e| e.into_inner());
            if guard.is_none() {
                return ptr::null_mut();
            }
            return new_int_array(env, fns, &[]);
        }
        None => return ptr::null_mut(),
    };

    let packed = {
        let guard = INDEX.read().unwrap_or_else(|e| e.into_inner());
        match guard.as_ref() {
            Some(index) => index.search(&compiled, max_results as usize),
            None => return ptr::null_mut(),
        }
    };

    new_int_array(env, fns, &packed)
}

unsafe fn get_verse_text_impl(env: *mut JNIEnv, verse_index: jint) -> jstring {
    let Some(fns) = vtable(env) else {
        return ptr::null_mut();
    };
    if verse_index < 0 || has_pending_exception(env, fns) {
        return ptr::null_mut();
    }
    let guard = INDEX.read().unwrap_or_else(|e| e.into_inner());
    let Some(index) = guard.as_ref() else {
        return ptr::null_mut();
    };
    let Some(slice) = index.verse_slice(verse_index as usize) else {
        return ptr::null_mut();
    };
    let Ok(len) = jsize::try_from(slice.len()) else {
        return ptr::null_mut();
    };
    let result = (fns.NewString)(env, slice.as_ptr(), len);
    if has_pending_exception(env, fns) {
        return ptr::null_mut();
    }
    result
}

unsafe fn find_match_spans_impl(
    env: *mut JNIEnv,
    verse_index: jint,
    query: jstring,
) -> jintArray {
    let Some(fns) = vtable(env) else {
        return ptr::null_mut();
    };
    if verse_index < 0 {
        return new_int_array(env, fns, &[]);
    }
    let compiled = match with_jstring_u16(env, fns, query, CompiledArabicQuery::compile) {
        Some(Some(c)) => c,
        _ => return new_int_array(env, fns, &[]),
    };

    let spans = {
        let guard = INDEX.read().unwrap_or_else(|e| e.into_inner());
        let Some(index) = guard.as_ref() else {
            return ptr::null_mut();
        };
        match index.verse_slice(verse_index as usize) {
            Some(slice) => compiled.find_all_spans(slice),
            None => Vec::new(),
        }
    };

    new_int_array(env, fns, &spans)
}

unsafe fn release_index_impl() {
    let mut guard = INDEX.write().unwrap_or_else(|e| e.into_inner());
    *guard = None;
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_quran_common_search_RustArabicSearchEngine_nativeLoadIndex(
    env: *mut JNIEnv,
    _class: jclass,
    row_ids: jintArray,
    suras: jintArray,
    ayahs: jintArray,
    texts: jobjectArray,
) -> jboolean {
    catch_unwind(AssertUnwindSafe(|| {
        load_index_impl(env, row_ids, suras, ayahs, texts)
    }))
    .unwrap_or(JNI_FALSE)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_quran_common_search_RustArabicSearchEngine_nativeIsLoaded(
    _env: *mut JNIEnv,
    _class: jclass,
) -> jboolean {
    catch_unwind(AssertUnwindSafe(|| is_loaded_impl())).unwrap_or(JNI_FALSE)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_quran_common_search_RustArabicSearchEngine_nativeSearch(
    env: *mut JNIEnv,
    _class: jclass,
    query: jstring,
    max_results: jint,
) -> jintArray {
    catch_unwind(AssertUnwindSafe(|| search_impl(env, query, max_results)))
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_quran_common_search_RustArabicSearchEngine_nativeGetVerseText(
    env: *mut JNIEnv,
    _class: jclass,
    verse_index: jint,
) -> jstring {
    catch_unwind(AssertUnwindSafe(|| get_verse_text_impl(env, verse_index)))
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_quran_common_search_RustArabicSearchEngine_nativeFindMatchSpans(
    env: *mut JNIEnv,
    _class: jclass,
    verse_index: jint,
    query: jstring,
) -> jintArray {
    catch_unwind(AssertUnwindSafe(|| {
        find_match_spans_impl(env, verse_index, query)
    }))
    .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_quran_common_search_RustArabicSearchEngine_nativeReleaseIndex(
    _env: *mut JNIEnv,
    _class: jclass,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| release_index_impl()));
}
