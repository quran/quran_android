//! Minimal, zero-dependency JNI C ABI definitions matching Android NDK `<jni.h>`.
//!
//! Every slot index in `JNINativeInterface` is frozen by the JNI 1.1–1.6 ABI specification
//! and verified by unit tests against the exact offsets in Android NDK 27 `jni.h`.

#![allow(non_camel_case_types, non_snake_case, dead_code)]

use std::ffi::c_void;

pub type jboolean = u8;
pub type jchar = u16;
pub type jint = i32;
pub type jsize = jint;

pub const JNI_FALSE: jboolean = 0;
pub const JNI_TRUE: jboolean = 1;

pub type jobject = *mut c_void;
pub type jclass = jobject;
pub type jstring = jobject;
pub type jarray = jobject;
pub type jobjectArray = jarray;
pub type jintArray = jarray;
pub type jthrowable = jobject;

pub type JNIEnv = *const JNINativeInterface;

type ReservedFn = Option<unsafe extern "C" fn()>;

#[repr(C)]
pub struct JNINativeInterface {
    // Slots 0..=14 (reserved0..3, GetVersion..ThrowNew)
    pub _pad_0_14: [ReservedFn; 15],
    // Slot 15
    pub ExceptionOccurred: unsafe extern "system" fn(env: *mut JNIEnv) -> jthrowable,
    // Slot 16 (ExceptionDescribe)
    pub _pad_16: ReservedFn,
    // Slot 17
    pub ExceptionClear: unsafe extern "system" fn(env: *mut JNIEnv),
    // Slots 18..=22 (FatalError..DeleteGlobalRef)
    pub _pad_18_22: [ReservedFn; 5],
    // Slot 23
    pub DeleteLocalRef: unsafe extern "system" fn(env: *mut JNIEnv, obj: jobject),
    // Slots 24..=162
    pub _pad_24_162: [ReservedFn; 139],
    // Slot 163
    pub NewString:
        unsafe extern "system" fn(env: *mut JNIEnv, unicode: *const jchar, len: jsize) -> jstring,
    // Slot 164
    pub GetStringLength: unsafe extern "system" fn(env: *mut JNIEnv, str: jstring) -> jsize,
    // Slot 165
    pub GetStringChars: unsafe extern "system" fn(
        env: *mut JNIEnv,
        str: jstring,
        is_copy: *mut jboolean,
    ) -> *const jchar,
    // Slot 166
    pub ReleaseStringChars:
        unsafe extern "system" fn(env: *mut JNIEnv, str: jstring, chars: *const jchar),
    // Slots 167..=170 (NewStringUTF..ReleaseStringUTFChars)
    pub _pad_167_170: [ReservedFn; 4],
    // Slot 171
    pub GetArrayLength: unsafe extern "system" fn(env: *mut JNIEnv, array: jarray) -> jsize,
    // Slot 172 (NewObjectArray)
    pub _pad_172: ReservedFn,
    // Slot 173
    pub GetObjectArrayElement:
        unsafe extern "system" fn(env: *mut JNIEnv, array: jobjectArray, index: jsize) -> jobject,
    // Slots 174..=178 (SetObjectArrayElement, New{Boolean,Byte,Char,Short}Array)
    pub _pad_174_178: [ReservedFn; 5],
    // Slot 179
    pub NewIntArray: unsafe extern "system" fn(env: *mut JNIEnv, len: jsize) -> jintArray,
    // Slots 180..=202
    pub _pad_180_202: [ReservedFn; 23],
    // Slot 203
    pub GetIntArrayRegion: unsafe extern "system" fn(
        env: *mut JNIEnv,
        array: jintArray,
        start: jsize,
        len: jsize,
        buf: *mut jint,
    ),
    // Slots 204..=210
    pub _pad_204_210: [ReservedFn; 7],
    // Slot 211
    pub SetIntArrayRegion: unsafe extern "system" fn(
        env: *mut JNIEnv,
        array: jintArray,
        start: jsize,
        len: jsize,
        buf: *const jint,
    ),
    // Slots 212..=219
    pub _pad_212_219: [ReservedFn; 8],
    // Slot 220
    pub GetStringRegion: unsafe extern "system" fn(
        env: *mut JNIEnv,
        str: jstring,
        start: jsize,
        len: jsize,
        buf: *mut jchar,
    ),
    // Slots 221..=227
    pub _pad_221_227: [ReservedFn; 7],
    // Slot 228
    pub ExceptionCheck: unsafe extern "system" fn(env: *mut JNIEnv) -> jboolean,
    // Slots 229..=232 (NewDirectByteBuffer..GetObjectRefType)
    pub _pad_229_232: [ReservedFn; 4],
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::mem::{offset_of, size_of};

    #[test]
    fn jni_native_interface_offsets_match_ndk_abi() {
        let ptr_size = size_of::<*const c_void>();
        assert_eq!(size_of::<JNINativeInterface>(), 233 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, ExceptionOccurred), 15 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, ExceptionClear), 17 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, DeleteLocalRef), 23 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, NewString), 163 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, GetStringLength), 164 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, GetStringChars), 165 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, ReleaseStringChars), 166 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, GetArrayLength), 171 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, GetObjectArrayElement), 173 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, NewIntArray), 179 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, GetIntArrayRegion), 203 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, SetIntArrayRegion), 211 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, GetStringRegion), 220 * ptr_size);
        assert_eq!(offset_of!(JNINativeInterface, ExceptionCheck), 228 * ptr_size);
    }
}
