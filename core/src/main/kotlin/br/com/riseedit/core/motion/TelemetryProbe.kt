package br.com.riseedit.core.motion

/**
 * Gyro experiment (P3, behind a flag): finds motion telemetry inside MP4/MOV files without
 * decoding video. Known carriers: CAMM ('camm' sample entry, Google/Insta360/some Android),
 * GoPro GPMF ('gpmd'), Sony 'rtmd'/'mett' metadata tracks, DJI 'djmd', Samsung top-level
 * 'sefd'/'svss' extension boxes (contents undocumented).
 */
enum class TelemetryKind(val label: String) {
    CAMM("CAMM (giroscópio/acelerômetro)"),
    GPMF("GoPro GPMF"),
    SONY_RTMD("Sony rtmd"),
    DJI("DJI djmd"),
    TIMED_METADATA("Trilha de metadados (mett)"),
    SAMSUNG_SEF("Samsung SEF (formato fechado)"),
    SAMSUNG_ACGY("Samsung acgy (Gear 360)")
}

data class TelemetryReport(val kinds: Set<TelemetryKind>, val boxes: List<String>) {
    val hasGyro get() = TelemetryKind.CAMM in kinds ||
        TelemetryKind.GPMF in kinds ||
        TelemetryKind.SONY_RTMD in kinds ||
        TelemetryKind.DJI in kinds ||
        TelemetryKind.SAMSUNG_ACGY in kinds
}

/** Random-access reader so the app can back it with a FileChannel or a ParcelFileDescriptor. */
fun interface ByteSource {
    /** Reads up to [length] bytes at [position]; returns fewer at EOF. */
    fun read(position: Long, length: Int): ByteArray
}

object TelemetryProbe {
    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "udta", "meta")
    private const val MAX_BOXES = 4000

    fun scan(source: ByteSource, size: Long): TelemetryReport {
        val kinds = mutableSetOf<TelemetryKind>()
        val seen = mutableListOf<String>()
        walk(source, 0, size, 0, kinds, seen)
        return TelemetryReport(kinds, seen)
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun walk(
        source: ByteSource,
        start: Long,
        end: Long,
        depth: Int,
        kinds: MutableSet<TelemetryKind>,
        seen: MutableList<String>
    ) {
        var pos = start
        while (pos + 8 <= end && seen.size < MAX_BOXES) {
            val header = source.read(pos, 16)
            if (header.size < 8) return
            var boxSize = u32(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            if (boxSize == 1L && header.size >= 16) {
                boxSize = u64(header, 8)
                headerSize = 16
            } else if (boxSize == 0L) {
                boxSize = end - pos
            }
            if (boxSize < headerSize || pos + boxSize > end + 8) return
            seen += "  ".repeat(depth) + type
            when (type) {
                "sefd", "svss" -> kinds += TelemetryKind.SAMSUNG_SEF
                "acgy" -> kinds += TelemetryKind.SAMSUNG_ACGY
                "stsd" -> sampleEntries(source, pos + headerSize, kinds)
            }
            if (type in containers) {
                val skip = if (type == "meta") 4 else 0
                walk(source, pos + headerSize + skip, pos + boxSize, depth + 1, kinds, seen)
            }
            pos += boxSize
        }
    }

    private fun sampleEntries(source: ByteSource, at: Long, kinds: MutableSet<TelemetryKind>) {
        val b = source.read(at, 16)
        if (b.size < 16) return
        when (String(b, 12, 4, Charsets.ISO_8859_1)) {
            "camm" -> kinds += TelemetryKind.CAMM
            "gpmd" -> kinds += TelemetryKind.GPMF
            "rtmd" -> kinds += TelemetryKind.SONY_RTMD
            "djmd" -> kinds += TelemetryKind.DJI
            "mett" -> kinds += TelemetryKind.TIMED_METADATA
        }
    }

    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF shl 24) or (b[o + 1].toLong() and 0xFF shl 16) or
            (b[o + 2].toLong() and 0xFF shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, o: Int): Long = (u32(b, o) shl 32) or u32(b, o + 4)
}
