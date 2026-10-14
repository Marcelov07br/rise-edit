package br.com.riseedit.subtitles

/** whisper.cpp JNI (app/src/main/cpp). One transcription at a time. */
internal object WhisperNative {
    val available: Boolean = runCatching { System.loadLibrary("rise_whisper") }.isSuccess

    external fun init(modelPath: String): Long
    external fun free(context: Long)
    external fun abort()
    external fun progress(): Int
    external fun transcribe(
        context: Long,
        pcm: FloatArray,
        language: String,
        threads: Int
    ): ByteArray?
}
