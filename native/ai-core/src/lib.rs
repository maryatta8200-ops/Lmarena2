use jni::objects::{JByteArray, JClass, JIntArray, JObject, JString};
use jni::sys::{jint, jlong};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use std::collections::HashMap;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::Mutex;
use tokenizers::Tokenizer;

static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);
static TOKENIZERS: Lazy<Mutex<HashMap<jlong, Tokenizer>>> = Lazy::new(|| Mutex::new(HashMap::new()));
const MAX_TOKENIZER_BYTES: usize = 50 * 1024 * 1024;
const MAX_CONTEXT_TOKENS: usize = 8192;
const MAX_INPUT_BYTES: usize = 160_000;

fn truncate_context(ids: &[u32], max_tokens: usize) -> Vec<u32> {
    if ids.len() <= max_tokens {
        return ids.to_vec();
    }
    let prefix_count = (max_tokens + 1) / 2;
    let suffix_count = max_tokens - prefix_count;
    let mut truncated = Vec::with_capacity(max_tokens);
    truncated.extend_from_slice(&ids[..prefix_count]);
    if suffix_count > 0 {
        truncated.extend_from_slice(&ids[ids.len() - suffix_count..]);
    }
    truncated
}

fn throw(env: &mut JNIEnv<'_>, class: &str, message: &str) {
    let _ = env.throw_new(class, message);
}

#[no_mangle]
pub extern "system" fn Java_com_localmed_ai_tokenizer_NativeTokenizerBridge_nativeCreateTokenizer(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    tokenizer_json: JByteArray<'_>,
) -> jlong {
    let bytes = match env.convert_byte_array(&tokenizer_json) {
        Ok(bytes) if !bytes.is_empty() && bytes.len() <= MAX_TOKENIZER_BYTES => bytes,
        Ok(_) => {
            throw(&mut env, "java/lang/IllegalArgumentException", "Tokenizer size is outside supported bounds.");
            return 0;
        }
        Err(error) => {
            throw(&mut env, "java/lang/IllegalArgumentException", &format!("Cannot read tokenizer bytes: {error}"));
            return 0;
        }
    };
    let tokenizer = match Tokenizer::from_bytes(bytes.as_slice()) {
        Ok(tokenizer) => tokenizer,
        Err(error) => {
            throw(&mut env, "java/lang/IllegalArgumentException", &format!("Tokenizer JSON is invalid: {error}"));
            return 0;
        }
    };
    let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed).max(1);
    match TOKENIZERS.lock() {
        Ok(mut guard) => {
            guard.insert(handle, tokenizer);
            handle
        }
        Err(_) => {
            throw(&mut env, "java/lang/IllegalStateException", "Tokenizer registry is unavailable.");
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_localmed_ai_tokenizer_NativeTokenizerBridge_nativeGetVocabularySize(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
) -> jint {
    match TOKENIZERS.lock() {
        Ok(guard) => match guard.get(&handle) {
            Some(tokenizer) => tokenizer.get_vocab_size(true).min(i32::MAX as usize) as jint,
            None => {
                throw(&mut env, "java/lang/IllegalStateException", "Tokenizer handle is invalid or closed.");
                0
            }
        },
        Err(_) => {
            throw(&mut env, "java/lang/IllegalStateException", "Tokenizer registry is unavailable.");
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_localmed_ai_tokenizer_NativeTokenizerBridge_nativeEncode(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
    text: JString<'_>,
    max_tokens: jint,
) -> jni::sys::jintArray {
    if max_tokens <= 0 || max_tokens as usize > MAX_CONTEXT_TOKENS {
        throw(&mut env, "java/lang/IllegalArgumentException", "Token limit is outside supported bounds.");
        return std::ptr::null_mut();
    }
    let text: String = match env.get_string(&text) {
        Ok(value) => value.into(),
        Err(error) => {
            throw(&mut env, "java/lang/IllegalArgumentException", &format!("Cannot read tokenizer input: {error}"));
            return std::ptr::null_mut();
        }
    };
    if text.len() > MAX_INPUT_BYTES {
        throw(&mut env, "java/lang/IllegalArgumentException", "Tokenizer input exceeds supported bounds.");
        return std::ptr::null_mut();
    }
    let encoded = match TOKENIZERS.lock() {
        Ok(guard) => match guard.get(&handle) {
            Some(tokenizer) => tokenizer.encode(text, true),
            None => {
                throw(&mut env, "java/lang/IllegalStateException", "Tokenizer handle is invalid or closed.");
                return std::ptr::null_mut();
            }
        },
        Err(_) => {
            throw(&mut env, "java/lang/IllegalStateException", "Tokenizer registry is unavailable.");
            return std::ptr::null_mut();
        }
    };
    let ids = match encoded {
        Ok(value) => truncate_context(value.get_ids(), max_tokens as usize)
            .into_iter()
            .map(|id| id as jint)
            .collect::<Vec<_>>(),
        Err(error) => {
            throw(&mut env, "java/lang/IllegalArgumentException", &format!("Tokenization failed: {error}"));
            return std::ptr::null_mut();
        }
    };
    let output: JIntArray<'_> = match env.new_int_array(ids.len() as jint) {
        Ok(array) => array,
        Err(error) => {
            throw(&mut env, "java/lang/OutOfMemoryError", &format!("Cannot allocate token array: {error}"));
            return std::ptr::null_mut();
        }
    };
    if let Err(error) = env.set_int_array_region(&output, 0, &ids) {
        throw(&mut env, "java/lang/IllegalStateException", &format!("Cannot fill token array: {error}"));
        return std::ptr::null_mut();
    }
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_localmed_ai_tokenizer_NativeTokenizerBridge_nativeDecode(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
    token_ids: JIntArray<'_>,
) -> jni::sys::jstring {
    let length = match env.get_array_length(&token_ids) {
        Ok(length) if length >= 0 && length as usize <= MAX_CONTEXT_TOKENS => length,
        Ok(_) => {
            throw(&mut env, "java/lang/IllegalArgumentException", "Token array exceeds supported bounds.");
            return std::ptr::null_mut();
        }
        Err(error) => {
            throw(&mut env, "java/lang/IllegalArgumentException", &format!("Cannot read token array: {error}"));
            return std::ptr::null_mut();
        }
    };
    let mut values = vec![0 as jint; length as usize];
    if let Err(error) = env.get_int_array_region(&token_ids, 0, &mut values) {
        throw(&mut env, "java/lang/IllegalArgumentException", &format!("Cannot read token IDs: {error}"));
        return std::ptr::null_mut();
    }
    if values.iter().any(|id| *id < 0) {
        throw(&mut env, "java/lang/IllegalArgumentException", "Token IDs must be non-negative.");
        return std::ptr::null_mut();
    }
    let decoded = match TOKENIZERS.lock() {
        Ok(guard) => match guard.get(&handle) {
            Some(tokenizer) => tokenizer.decode(&values.into_iter().map(|id| id as u32).collect::<Vec<_>>(), true),
            None => {
                throw(&mut env, "java/lang/IllegalStateException", "Tokenizer handle is invalid or closed.");
                return std::ptr::null_mut();
            }
        },
        Err(_) => {
            throw(&mut env, "java/lang/IllegalStateException", "Tokenizer registry is unavailable.");
            return std::ptr::null_mut();
        }
    };
    match decoded {
        Ok(text) => match env.new_string(text) {
            Ok(value) => value.into_raw(),
            Err(error) => {
                throw(&mut env, "java/lang/OutOfMemoryError", &format!("Cannot allocate decoded text: {error}"));
                std::ptr::null_mut()
            }
        },
        Err(error) => {
            throw(&mut env, "java/lang/IllegalArgumentException", &format!("Detokenization failed: {error}"));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_localmed_ai_tokenizer_NativeTokenizerBridge_nativeDestroyTokenizer(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    handle: jlong,
) {
    match TOKENIZERS.lock() {
        Ok(mut guard) => { guard.remove(&handle); }
        Err(_) => throw(&mut env, "java/lang/IllegalStateException", "Tokenizer registry is unavailable."),
    }
}

#[cfg(test)]
mod tests {
    use super::{truncate_context, MAX_CONTEXT_TOKENS, MAX_INPUT_BYTES, MAX_TOKENIZER_BYTES};

    #[test]
    fn context_truncation_keeps_both_prompt_prefix_and_question_suffix() {
        assert_eq!(truncate_context(&[0, 1, 2, 3, 4, 5, 6, 7], 6), vec![0, 1, 2, 5, 6, 7]);
        assert_eq!(truncate_context(&[0, 1, 2], 8), vec![0, 1, 2]);
    }

    #[test]
    fn native_limits_are_finite_and_match_contract() {
        assert_eq!(MAX_CONTEXT_TOKENS, 8192);
        assert!(MAX_INPUT_BYTES >= 40_000);
        assert!(MAX_TOKENIZER_BYTES >= 10 * 1024 * 1024);
    }
}
