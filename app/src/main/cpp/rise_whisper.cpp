// JNI bridge for on-device subtitles (whisper.cpp). Segments come back as
// "t0Ms\tt1Ms\ttext\n" lines so Kotlin parses them without JNI object juggling.
#include <jni.h>
#include <string>
#include <atomic>
#include "whisper.h"

static std::atomic<bool> g_abort{false};

static bool abort_cb(void *) { return g_abort.load(); }
static void progress_cb(struct whisper_context *, struct whisper_state *, int progress, void *data) {
    auto *p = static_cast<std::atomic<int> *>(data);
    p->store(progress);
}
static std::atomic<int> g_progress{0};

extern "C" JNIEXPORT jlong JNICALL
Java_br_com_riseedit_subtitles_WhisperNative_init(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_riseedit_subtitles_WhisperNative_free(JNIEnv *, jobject, jlong ctx) {
    if (ctx) whisper_free(reinterpret_cast<whisper_context *>(ctx));
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_riseedit_subtitles_WhisperNative_abort(JNIEnv *, jobject) { g_abort = true; }

extern "C" JNIEXPORT jint JNICALL
Java_br_com_riseedit_subtitles_WhisperNative_progress(JNIEnv *, jobject) { return g_progress.load(); }

extern "C" JNIEXPORT jbyteArray JNICALL
Java_br_com_riseedit_subtitles_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong handle, jfloatArray pcm, jstring language, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (!ctx) return nullptr;
    g_abort = false;
    g_progress = 0;
    const char *lang = env->GetStringUTFChars(language, nullptr);
    std::string langCopy(lang);
    env->ReleaseStringUTFChars(language, lang);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.translate = false;
    params.language = langCopy.c_str();
    params.n_threads = threads;
    params.no_context = true;
    params.max_len = 42;
    params.split_on_word = true;
    params.token_timestamps = true;
    params.abort_callback = abort_cb;
    params.progress_callback = progress_cb;
    params.progress_callback_user_data = &g_progress;
    jsize n = env->GetArrayLength(pcm);
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    int rc = whisper_full(ctx, params, samples, n);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (rc != 0) return nullptr;
    std::string out;
    int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        out += std::to_string(whisper_full_get_segment_t0(ctx, i) * 10);
        out += '\t';
        out += std::to_string(whisper_full_get_segment_t1(ctx, i) * 10);
        out += '\t';
        std::string text = whisper_full_get_segment_text(ctx, i);
        for (char &c : text) if (c == '\n' || c == '\t') c = ' ';
        out += text;
        out += '\n';
    }
    jbyteArray bytes = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(bytes, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return bytes;
}
