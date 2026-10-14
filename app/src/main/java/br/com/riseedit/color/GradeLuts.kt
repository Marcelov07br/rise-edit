@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.color

import android.content.Context
import android.net.Uri
import androidx.media3.effect.SingleColorLut
import br.com.riseedit.core.color.ColorGrade
import br.com.riseedit.core.color.CubeLut
import br.com.riseedit.diagnostics.Diagnostics
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Loads user .cube files (copied into app storage) and bakes grades into GPU LUTs. */
internal object GradeLuts {
    private const val SIZE = 33
    private var root: File? = null
    private val luts = ConcurrentHashMap<String, CubeLut>()
    private val cubes = object : LinkedHashMap<ColorGrade, FloatArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ColorGrade, FloatArray>) =
            size > 12
    }

    fun install(context: Context) {
        root = File(context.filesDir, "luts").apply { mkdirs() }
    }

    /** Copies a picked .cube into app storage, validates it and returns (fileUri, title). */
    fun import(context: Context, uri: Uri): Pair<String, String> {
        val text =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Não foi possível ler o LUT.")
        val lut = CubeLut.parse(text)
        val hash = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        val dir = root ?: File(context.filesDir, "luts").apply { mkdirs() }
        val file = File(dir, "$hash.cube")
        if (!file.exists()) file.writeText(text)
        luts[file.toURI().toString()] = lut
        val name = lut.title.ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "LUT" }
        return file.toURI().toString() to name
    }

    fun lut(uri: String?): CubeLut? {
        if (uri == null) return null
        luts[uri]?.let { return it }
        return runCatching { CubeLut.parse(File(java.net.URI(uri)).readText()) }
            .onFailure { Diagnostics.log("cor", "LUT ilegível: $uri", it) }
            .getOrNull()?.also { luts[uri] = it }
    }

    fun cube(grade: ColorGrade): FloatArray = synchronized(cubes) {
        cubes.getOrPut(grade) { grade.cube(SIZE, lut(grade.lutUri)) }
    }

    /** Media3 effect; null for the identity grade so untouched clips cost nothing. */
    fun effect(grade: ColorGrade): SingleColorLut? {
        if (grade.isIdentity) return null
        val data = cube(grade)
        val argb = Array(SIZE) { r ->
            Array(SIZE) { g ->
                IntArray(SIZE) { b ->
                    val i = ((b * SIZE + g) * SIZE + r) * 3
                    (0xFF shl 24) or (channel(data[i]) shl 16) or (channel(data[i + 1]) shl 8) or
                        channel(data[i + 2])
                }
            }
        }
        return SingleColorLut.createFromCube(argb)
    }

    /** CPU version for meters. */
    fun apply(grade: ColorGrade, argb: Int): Int {
        if (grade.isIdentity) return argb
        val o = grade.apply(
            doubleArrayOf(
                (argb shr 16 and 0xFF) / 255.0,
                (argb shr 8 and 0xFF) / 255.0,
                (argb and 0xFF) / 255.0
            ),
            lut(grade.lutUri)
        )
        return (0xFF shl 24) or (
            channel(
                o[0].toFloat()
            ) shl 16
            ) or (channel(o[1].toFloat()) shl 8) or
            channel(o[2].toFloat())
    }

    private fun channel(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
}
