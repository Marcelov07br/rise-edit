package br.com.riseedit.subtitles

import java.io.File

/** whisper.cpp JNI (app/src/main/cpp). One transcription at a time. */
internal object WhisperNative {
    /** 0.25.0: arm64 lib is built for ARMv8.2-A dotprod+fp16; older CPUs would crash (SIGILL). */
    val cpuSupported: Boolean by lazy {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        if (abi != "arm64-v8a") return@lazy true
        val features = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")
        features.isEmpty() || ("asimddp" in features && "fphp" in features)
    }

    val available: Boolean by lazy {
        cpuSupported && runCatching { System.loadLibrary("rise_whisper") }.isSuccess
    }

    /** Big cores only (cpuinfo_max_freq); little cores stall ggml's per-op barrier. */
    val threads: Int by lazy {
        val freqs = (0 until Runtime.getRuntime().availableProcessors()).map { cpu ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            }.getOrDefault(0L)
        }
        br.com.riseedit.core.CpuTopology.bigCores(freqs, Runtime.getRuntime().availableProcessors())
    }

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
