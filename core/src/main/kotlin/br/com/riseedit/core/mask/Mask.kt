package br.com.riseedit.core.mask

import br.com.riseedit.core.color.LocalColorMath
import br.com.riseedit.core.color.Qualifier
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Mask shapes (0.17). [COLOR] reuses the qualifier key (hue/sat/luma ranges) as a mask. */
enum class MaskType(val id: String, val label: String) {
    RECT("rect", "Retângulo"),
    ELLIPSE("ellipse", "Elipse"),
    LINEAR("linear", "Gradiente linear"),
    RADIAL("radial", "Gradiente radial"),
    COLOR("color", "Cor");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** How a mask combines with the ones above it in the list (the first one always starts the union). */
enum class MaskOp(val id: String, val label: String) {
    ADD("add", "Somar"),
    SUBTRACT("sub", "Subtrair"),
    INTERSECT("and", "Intersecção");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: ADD
    }
}

/**
 * Animatable geometry, frame-normalized with y DOWN (top-left = 0,0): center [cx], [cy]; [w], [h] =
 * full size as a fraction of frame width/height; [rotation] degrees (clockwise on screen);
 * [feather] 0..1 = soft edge width as a fraction of the frame height.
 */
data class MaskGeom(
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    val w: Float = 0.5f,
    val h: Float = 0.5f,
    val rotation: Float = 0f,
    val feather: Float = 0.1f
) {
    fun clamped() = MaskGeom(
        cx.coerceIn(-0.5f, 1.5f),
        cy.coerceIn(-0.5f, 1.5f),
        w.coerceIn(MIN_SIZE, 3f),
        h.coerceIn(MIN_SIZE, 3f),
        rotation.let { ((it + 180f) % 360f + 360f) % 360f - 180f },
        feather.coerceIn(0f, 1f)
    )

    companion object {
        const val MIN_SIZE = 0.02f

        /** Smoothstep ease between [a] and [b] (C1, same as color keys); rotation by the short way. */
        fun lerp(a: MaskGeom, b: MaskGeom, t: Float): MaskGeom {
            fun l(x: Float, y: Float) = x + (y - x) * t
            var dr = (b.rotation - a.rotation) % 360f
            if (dr > 180f) dr -= 360f
            if (dr < -180f) dr += 360f
            return MaskGeom(
                l(a.cx, b.cx),
                l(a.cy, b.cy),
                l(a.w, b.w),
                l(a.h, b.h),
                a.rotation + dr * t,
                l(a.feather, b.feather)
            )
        }
    }
}

/** A geometry snapshot at [timeMs] (owner time: clip SOURCE time, layer-local time, overlay clip source). */
data class MaskKey(val timeMs: Long, val geom: MaskGeom)

/**
 * One mask. [roundness] 0..1 = corner radius of [MaskType.RECT] as a fraction of its smaller half
 * side. [opacity] 0..1 scales the coverage. [qualifier] is only used by [MaskType.COLOR] (its
 * ranges; the adjustment fields are ignored). [keys] animate [geom] (position/size/rotation/feather).
 */
data class Mask(
    val id: String = UUID.randomUUID().toString(),
    val type: MaskType = MaskType.ELLIPSE,
    val geom: MaskGeom = MaskGeom(),
    val roundness: Float = 0.2f,
    val invert: Boolean = false,
    val opacity: Float = 1f,
    val op: MaskOp = MaskOp.ADD,
    val qualifier: Qualifier = Qualifier(),
    val keys: List<MaskKey> = emptyList(),
    val enabled: Boolean = true
) {
    val isGeometric get() = type != MaskType.COLOR

    /** Geometry at [timeMs]: no keys = [geom]; one key = constant; else smoothstep between neighbours. */
    fun geomAt(timeMs: Long): MaskGeom {
        if (keys.isEmpty()) return geom
        val k = keys.sortedBy { it.timeMs }
        if (timeMs <= k.first().timeMs) return k.first().geom
        if (timeMs >= k.last().timeMs) return k.last().geom
        val i = k.indexOfLast { it.timeMs <= timeMs }
        val a = k[i]
        val b = k[i + 1]
        val t = (
            (timeMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs).coerceAtLeast(
                1
            )
            ).coerceIn(0f, 1f)
        return MaskGeom.lerp(a.geom, b.geom, t * t * (3f - 2f * t))
    }

    fun hasKeyAt(timeMs: Long, frameMs: Long = FRAME_MS) = keys.any {
        abs(it.timeMs - timeMs) <
            frameMs / 2 + 1
    }

    /**
     * Sets the geometry while editing at [timeMs]: with keys, adds/replaces the key there (auto-key),
     * otherwise changes the static [geom].
     */
    fun withGeomAt(timeMs: Long, g: MaskGeom, frameMs: Long = FRAME_MS): Mask {
        val c = g.clamped()
        if (keys.isEmpty()) return copy(geom = c)
        val kept = keys.filterNot { abs(it.timeMs - timeMs) < frameMs / 2 + 1 }
        return copy(keys = (kept + MaskKey(timeMs, c)).sortedBy { it.timeMs })
    }

    /** Diamond toggle: add a key with the current value, or remove it (last key bakes into [geom]). */
    fun toggleKey(timeMs: Long, frameMs: Long = FRAME_MS): Mask {
        val now = geomAt(timeMs)
        if (!hasKeyAt(timeMs, frameMs)) {
            return copy(
                keys = (keys + MaskKey(timeMs, now)).sortedBy {
                    it.timeMs
                }
            )
        }
        val kept = keys.filterNot { abs(it.timeMs - timeMs) < frameMs / 2 + 1 }
        return if (kept.isEmpty()) copy(geom = now, keys = emptyList()) else copy(keys = kept)
    }

    /** Static mask (keys evaluated at [timeMs]); this is what the renderer receives. */
    fun at(timeMs: Long) =
        if (keys.isEmpty()) this else copy(geom = geomAt(timeMs), keys = emptyList())

    fun shifted(deltaMs: Long) = if (deltaMs == 0L || keys.isEmpty()) {
        this
    } else {
        copy(keys = keys.map { it.copy(timeMs = (it.timeMs + deltaMs).coerceAtLeast(0)) })
    }

    companion object {
        const val FRAME_MS = 33L

        /** Defaults per type (gradients cover more of the frame). */
        fun new(type: MaskType) = when (type) {
            MaskType.RECT -> Mask(type = type, geom = MaskGeom(w = 0.5f, h = 0.5f, feather = 0.05f))
            MaskType.ELLIPSE -> Mask(
                type = type,
                geom = MaskGeom(w = 0.4f, h = 0.5f, feather = 0.08f)
            )
            MaskType.LINEAR -> Mask(type = type, geom = MaskGeom(w = 1f, h = 0.3f, feather = 0f))
            MaskType.RADIAL -> Mask(
                type = type,
                geom = MaskGeom(w = 0.6f, h = 0.8f, feather = 0.6f)
            )
            MaskType.COLOR -> Mask(
                type = type,
                qualifier = Qualifier(active = true, hue = 30f, satLow = 0.2f)
            )
        }
    }
}

/**
 * Up to [MAX] masks attached to a target (clip color, adjustment layer, overlay alpha). Empty = no
 * mask (target applies everywhere). Combination is sequential, top of the list first; see [MaskMath.combine].
 */
data class MaskSet(val masks: List<Mask> = emptyList()) {
    val isEmpty get() = masks.none { it.enabled }
    val isAnimated get() = masks.any { it.keys.size > 1 }

    /** Enabled masks evaluated at [timeMs] (keys applied, keys dropped). */
    fun at(timeMs: Long): List<Mask> = masks.filter { it.enabled }.take(MAX).map { it.at(timeMs) }

    fun add(m: Mask) = if (masks.size >= MAX) this else copy(masks = masks + m)
    fun replace(m: Mask) = copy(masks = masks.map { if (it.id == m.id) m else it })
    fun remove(id: String) = copy(masks = masks.filterNot { it.id == id })
    fun shifted(deltaMs: Long) = copy(masks = masks.map { it.shifted(deltaMs) })

    /** Key times of every mask (diamond marks). */
    fun times(): List<Long> = masks.flatMap { m -> m.keys.map { it.timeMs } }.distinct().sorted()

    /** Fresh ids (paste: the copy must not share ids with the source). */
    fun withNewIds() = copy(masks = masks.map { it.copy(id = UUID.randomUUID().toString()) })

    companion object {
        const val MAX = 4
        const val VERSION = 1
        val EMPTY = MaskSet()

        fun toMap(s: MaskSet): Map<String, Any> = mapOf(
            "v" to VERSION,
            "masks" to s.masks.map(::maskMap)
        )

        private fun geomMap(g: MaskGeom) = mapOf(
            "cx" to g.cx.toDouble(),
            "cy" to g.cy.toDouble(),
            "w" to g.w.toDouble(),
            "h" to g.h.toDouble(),
            "rot" to g.rotation.toDouble(),
            "feather" to g.feather.toDouble()
        )

        private fun maskMap(m: Mask): Map<String, Any> = buildMap {
            put("id", m.id)
            put("type", m.type.id)
            put("geom", geomMap(m.geom))
            put("round", m.roundness.toDouble())
            put("invert", m.invert)
            put("opacity", m.opacity.toDouble())
            put("op", m.op.id)
            put("on", m.enabled)
            if (m.type == MaskType.COLOR) put("qualifier", Qualifier.toMap(m.qualifier))
            if (m.keys.isNotEmpty()) {
                put(
                    "keys",
                    m.keys.map {
                        mapOf(
                            "t" to it.timeMs,
                            "geom" to geomMap(it.geom)
                        )
                    }
                )
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun maskOf(o: Map<String, Any?>): Mask? {
            val type = MaskType.of(o["type"] as? String) ?: return null
            val q = Qualifier.fromMap(o["qualifier"] as? Map<String, Any?>)
            return Mask(
                id = (o["id"] as? String) ?: UUID.randomUUID().toString(),
                type = type,
                geom = geomOf(o["geom"] as? Map<*, *>),
                roundness = ((o["round"] as? Number)?.toFloat() ?: 0.2f).coerceIn(0f, 1f),
                invert = o["invert"] as? Boolean ?: false,
                opacity = ((o["opacity"] as? Number)?.toFloat() ?: 1f).coerceIn(0f, 1f),
                op = MaskOp.of(o["op"] as? String),
                qualifier = if (type == MaskType.COLOR) q.copy(active = true) else q,
                keys = keysOf(o["keys"] as? List<*>),
                enabled = o["on"] as? Boolean ?: true
            )
        }

        private fun keysOf(list: List<*>?): List<MaskKey> = list.orEmpty().mapNotNull { k ->
            val km = k as? Map<*, *> ?: return@mapNotNull null
            val t = (km["t"] as? Number)?.toLong() ?: return@mapNotNull null
            MaskKey(t.coerceAtLeast(0), geomOf(km["geom"] as? Map<*, *>))
        }.sortedBy { it.timeMs }

        private fun geomOf(m: Map<*, *>?): MaskGeom {
            val d = MaskGeom()
            if (m == null) return d
            fun f(k: String, def: Float) = (m[k] as? Number)?.toFloat() ?: def
            return MaskGeom(
                f("cx", d.cx),
                f("cy", d.cy),
                f("w", d.w),
                f("h", d.h),
                f("rot", d.rotation),
                f("feather", d.feather)
            ).clamped()
        }

        /**
         * Reads any known version. v absent/0 (never written, defensive) or unknown future fields are
         * tolerated; unknown types are dropped; more than [MAX] masks are cut. null → empty.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<String, Any?>?): MaskSet {
            if (m == null) return EMPTY
            val v = (m["v"] as? Number)?.toInt() ?: 1
            if (v > VERSION + 100) return EMPTY
            val list = (m["masks"] as? List<*>).orEmpty().mapNotNull { raw ->
                (raw as? Map<String, Any?>)?.let(::maskOf)
            }.take(MAX)
            return MaskSet(list)
        }
    }
}

/**
 * Mask math: CPU reference ([coverage]) and the shader source ([GLSL]) — change both together.
 * Coordinates: uv in 0..1 with y DOWN, [aspect] = frame width / height. Shapes are evaluated in an
 * aspect-correct space (x scaled by aspect) so circles stay round and rotation is rigid.
 */
object MaskMath {
    private fun sstep(e0: Float, e1: Float, x: Float): Float {
        if (e1 <= e0) return if (x >= e1) 1f else 0f
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Raw coverage 0..1 of one (static) mask before invert/opacity. [key] = original color (COLOR masks). */
    fun shape(m: Mask, u: Float, v: Float, aspect: Float, key: FloatArray? = null): Float {
        if (m.type ==
            MaskType.COLOR
        ) {
            return if (key ==
                null
            ) {
                0f
            } else {
                LocalColorMath.mask(m.qualifier.copy(active = true), key)
            }
        }
        val g = m.geom
        val a = g.rotation * (PI.toFloat() / 180f)
        val ca = cos(a)
        val sa = sin(a)
        val dx = (u - g.cx) * aspect
        val dy = v - g.cy
        // Inverse rotation (screen clockwise, y down).
        val px = dx * ca + dy * sa
        val py = -dx * sa + dy * ca
        val hx = max(g.w * aspect * 0.5f, 1e-4f)
        val hy = max(g.h * 0.5f, 1e-4f)
        val fw = max(g.feather * 0.5f, 0.0015f)
        return when (m.type) {
            MaskType.RECT -> {
                val r = m.roundness.coerceIn(0f, 1f) * min(hx, hy)
                val qx = abs(px) - (hx - r)
                val qy = abs(py) - (hy - r)
                val d =
                    sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f)) +
                        min(max(qx, qy), 0f) -
                        r
                1f - sstep(-fw, fw, d)
            }
            MaskType.ELLIPSE -> {
                val k = sqrt((px / hx) * (px / hx) + (py / hy) * (py / hy))
                val d = (k - 1f) * min(hx, hy)
                1f - sstep(-fw, fw, d)
            }
            // Full on the top side (before rotation), fading across the height h (+ feather).
            MaskType.LINEAR -> 1f - sstep(-hy - fw, hy + fw, py)
            MaskType.RADIAL -> {
                val k = sqrt((px / hx) * (px / hx) + (py / hy) * (py / hy))
                1f - sstep((1f - g.feather.coerceIn(0f, 1f)) * 0.999f, 1f, k)
            }
            MaskType.COLOR -> 0f
        }
    }

    /** Coverage of one mask with invert and opacity. */
    fun single(m: Mask, u: Float, v: Float, aspect: Float, key: FloatArray? = null): Float {
        val s = shape(m, u, v, aspect, key)
        return (if (m.invert) 1f - s else s) * m.opacity.coerceIn(0f, 1f)
    }

    /** Sequential combination; the first mask always starts the selection. Empty list = 1. */
    fun combine(values: List<Pair<MaskOp, Float>>): Float {
        if (values.isEmpty()) return 1f
        var acc = 0f
        values.forEachIndexed { i, (op, m) ->
            acc = when (if (i == 0) MaskOp.ADD else op) {
                MaskOp.ADD -> acc + m - acc * m
                MaskOp.SUBTRACT -> acc * (1f - m)
                MaskOp.INTERSECT -> acc * m
            }
        }
        return acc.coerceIn(0f, 1f)
    }

    /** Final coverage of [masks] (already evaluated with [MaskSet.at]); empty = 1 (no mask). */
    fun coverage(
        masks: List<Mask>,
        u: Float,
        v: Float,
        aspect: Float,
        key: FloatArray? = null
    ): Float = combine(masks.take(MaskSet.MAX).map { it.op to single(it, u, v, aspect, key) })

    /**
     * Shader functions. Needs `sstep`, `rgbToHsv` and `qualMaskP` (LocalColorMath.GLSL, after
     * ColorAdjustMath.GLSL). Uniforms per mask i (0..3): uMkA{i} = (cx, cy, w, h), uMkB{i} =
     * (rotation rad, feather, roundness, type), uMkC{i} = (op, invert, opacity, 0), uMkQH/QS/QL{i}
     * = qualifier ranges. uMkN = active count (0 = no mask → coverage 1).
     */
    val GLSL: String = buildString {
        append("uniform float uMkN;\n")
        for (i in 0 until MaskSet.MAX) {
            append("uniform vec4 uMkA$i;\nuniform vec4 uMkB$i;\nuniform vec4 uMkC$i;\n")
            append("uniform vec3 uMkQH$i;\nuniform vec3 uMkQS$i;\nuniform vec3 uMkQL$i;\n")
        }
        append(
            """
float maskShape(vec2 uv, float aspect, vec3 key, vec4 A, vec4 B, vec3 QH, vec3 QS, vec3 QL) {
  float type = B.w;
  if (type > 3.5) return qualMaskP(key, QH, QS, QL);
  float ca = cos(B.x); float sa = sin(B.x);
  float dx = (uv.x - A.x) * aspect;
  float dy = uv.y - A.y;
  float px = dx * ca + dy * sa;
  float py = -dx * sa + dy * ca;
  float hx = max(A.z * aspect * 0.5, 0.0001);
  float hy = max(A.w * 0.5, 0.0001);
  float fw = max(B.y * 0.5, 0.0015);
  if (type < 0.5) {
    float r = clamp(B.z, 0.0, 1.0) * min(hx, hy);
    vec2 q = abs(vec2(px, py)) - vec2(hx - r, hy - r);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    return 1.0 - sstep(-fw, fw, d);
  }
  float k = length(vec2(px / hx, py / hy));
  if (type < 1.5) return 1.0 - sstep(-fw, fw, (k - 1.0) * min(hx, hy));
  if (type < 2.5) return 1.0 - sstep(-hy - fw, hy + fw, py);
  return 1.0 - sstep((1.0 - clamp(B.y, 0.0, 1.0)) * 0.999, 1.0, k);
}
float maskStep(float acc, float first, vec2 uv, float aspect, vec3 key, vec4 A, vec4 B, vec4 C, vec3 QH, vec3 QS, vec3 QL) {
  float s = maskShape(uv, aspect, key, A, B, QH, QS, QL);
  float m = (C.y > 0.5 ? 1.0 - s : s) * clamp(C.z, 0.0, 1.0);
  float op = first > 0.5 ? 0.0 : C.x;
  if (op < 0.5) return acc + m - acc * m;
  if (op < 1.5) return acc * (1.0 - m);
  return acc * m;
}
float maskCoverage(vec2 uv, float aspect, vec3 key) {
  if (uMkN < 0.5) return 1.0;
  float acc = 0.0;
  acc = maskStep(acc, 1.0, uv, aspect, key, uMkA0, uMkB0, uMkC0, uMkQH0, uMkQS0, uMkQL0);
  if (uMkN > 1.5) acc = maskStep(acc, 0.0, uv, aspect, key, uMkA1, uMkB1, uMkC1, uMkQH1, uMkQS1, uMkQL1);
  if (uMkN > 2.5) acc = maskStep(acc, 0.0, uv, aspect, key, uMkA2, uMkB2, uMkC2, uMkQH2, uMkQS2, uMkQL2);
  if (uMkN > 3.5) acc = maskStep(acc, 0.0, uv, aspect, key, uMkA3, uMkB3, uMkC3, uMkQH3, uMkQS3, uMkQL3);
  return clamp(acc, 0.0, 1.0);
}
"""
        )
    }

    /** Uniform values for [GLSL] (vectors) + "uMkN" as a 1-element array. Unused slots are zeroed. */
    fun uniforms(masks: List<Mask>): Map<String, FloatArray> = buildMap {
        val list = masks.take(MaskSet.MAX)
        put("uMkN", floatArrayOf(list.size.toFloat()))
        for (i in 0 until MaskSet.MAX) {
            val m = list.getOrNull(i)
            val g = m?.geom ?: MaskGeom()
            val q = m?.qualifier ?: Qualifier()
            put("uMkA$i", floatArrayOf(g.cx, g.cy, g.w, g.h))
            put(
                "uMkB$i",
                floatArrayOf(
                    g.rotation * (PI.toFloat() / 180f),
                    g.feather,
                    m?.roundness ?: 0f,
                    (m?.type?.ordinal ?: 0).toFloat()
                )
            )
            put(
                "uMkC$i",
                floatArrayOf(
                    (m?.op?.ordinal ?: 0).toFloat(),
                    if (m?.invert ==
                        true
                    ) {
                        1f
                    } else {
                        0f
                    },
                    m?.opacity ?: 0f,
                    0f
                )
            )
            put("uMkQH$i", floatArrayOf(q.hue, q.hueWidth, q.hueSoft))
            put("uMkQS$i", floatArrayOf(q.satLow, q.satHigh, q.satSoft))
            put("uMkQL$i", floatArrayOf(q.lumLow, q.lumHigh, q.lumSoft))
        }
    }
}
