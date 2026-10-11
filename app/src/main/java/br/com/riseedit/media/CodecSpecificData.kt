package br.com.riseedit.media

import android.annotation.SuppressLint
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.os.Build
import androidx.media3.container.NalUnitUtil
import br.com.riseedit.core.ChromaFormat
import br.com.riseedit.core.ColorDepth

internal data class VideoDetails(
    val depth: ColorDepth = ColorDepth.UNKNOWN,
    val chroma: ChromaFormat = ChromaFormat.UNKNOWN,
    val profile: Int = 0,
    val level: Int = 0,
    val transfer: Int? = null
)

internal object CodecSpecificData {
    @SuppressLint("UnsafeOptInUsageError")
    fun parse(format: MediaFormat, mime: String): VideoDetails? {
        if (mime != MediaFormat.MIMETYPE_VIDEO_AVC && mime != MediaFormat.MIMETYPE_VIDEO_HEVC) return null
        for (index in 0..2) {
            val bytes = safely {
                val buffer = format.getByteBuffer("csd-$index")?.duplicate() ?: return@safely null
                if (buffer.remaining() !in 1..MAX_CSD_BYTES) return@safely null
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            } ?: continue
            val hevc = mime == MediaFormat.MIMETYPE_VIDEO_HEVC
            val nal = safely { findSps(bytes, hevc) } ?: continue
            val details = safely { if (hevc) parseHevcSps(nal) else parseAvcSps(nal) }
            if (details != null) {
                // Media3 also reads SPS VUI color transfer when the container omitted its color tags.
                val transfer = safely {
                    if (hevc) NalUnitUtil.parseH265SpsNalUnit(nal, 0, nal.size, null).colorTransfer
                    else NalUnitUtil.parseSpsNalUnit(nal, 0, nal.size).colorTransfer
                }
                return details.copy(transfer = transfer)
            }
        }
        return null
    }

    private fun findSps(data: ByteArray, hevc: Boolean): ByteArray? {
        fun isSps(offset: Int) = offset < data.size && if (hevc)
            offset + 1 < data.size && (data[offset].toInt() and 0x7e) shr 1 == 33
        else data[offset].toInt() and 0x1f == 7
        fun startCode(offset: Int): Int = when {
            offset + 3 < data.size && data[offset] == 0.toByte() && data[offset + 1] == 0.toByte() &&
                data[offset + 2] == 0.toByte() && data[offset + 3] == 1.toByte() -> 4
            offset + 2 < data.size && data[offset] == 0.toByte() && data[offset + 1] == 0.toByte() &&
                data[offset + 2] == 1.toByte() -> 3
            else -> 0
        }
        var offset = 0
        while (offset < data.size - 2) {
            val prefix = startCode(offset)
            if (prefix == 0) { offset++; continue }
            val begin = offset + prefix
            var end = begin
            while (end < data.size && startCode(end) == 0) end++
            if (isSps(begin)) return data.copyOfRange(begin, end)
            offset = end
        }
        if (isSps(0)) return data
        // Some extractors expose AVCDecoderConfigurationRecord / HEVCDecoderConfigurationRecord.
        if (data.isEmpty() || data[0] != 1.toByte()) return null
        fun byte(at: Int) = data[at].toInt() and 0xff
        fun short(at: Int) = (byte(at) shl 8) or byte(at + 1)
        if (!hevc && data.size >= 7) {
            offset = 6
            repeat(byte(5) and 0x1f) {
                val size = short(offset)
                offset += 2
                require(size > 0 && offset + size <= data.size)
                if (isSps(offset)) return data.copyOfRange(offset, offset + size)
                offset += size
            }
        } else if (hevc && data.size >= 23) {
            offset = 23
            repeat(byte(22)) {
                require(offset + 3 <= data.size)
                val type = byte(offset++) and 0x3f
                val count = short(offset)
                offset += 2
                repeat(count) {
                    val size = short(offset)
                    offset += 2
                    require(size > 0 && offset + size <= data.size)
                    if (type == 33 && isSps(offset)) return data.copyOfRange(offset, offset + size)
                    offset += size
                }
            }
        }
        return null
    }

    private fun parseAvcSps(nal: ByteArray): VideoDetails {
        require(nal.size >= 5 && nal[0].toInt() and 0x80 == 0)
        val bits = RbspBits(nal, 1)
        val profileIdc = bits.read(8)
        val constraints = bits.read(8)
        val levelIdc = bits.read(8)
        require(bits.ue() <= 31)
        var chromaIdc = 1
        var lumaBits = 8
        var chromaBits = 8
        if (profileIdc in setOf(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135)) {
            chromaIdc = bits.ue()
            require(chromaIdc in 0..3)
            if (chromaIdc == 3) bits.skip(1)
            lumaBits = 8 + bits.ue()
            chromaBits = 8 + bits.ue()
            require(lumaBits in 8..14 && chromaBits in 8..14)
        } else if (profileIdc !in setOf(66, 77, 88)) {
            return VideoDetails()
        }
        val profile = when (profileIdc) {
            // Constrained variants are subsets of these profiles. Querying the base profile avoids
            // rejecting decoders whose profileLevels omit a separate constrained-profile entry.
            66 -> CodecProfileLevel.AVCProfileBaseline
            77 -> CodecProfileLevel.AVCProfileMain
            88 -> CodecProfileLevel.AVCProfileExtended
            100 -> CodecProfileLevel.AVCProfileHigh
            110 -> CodecProfileLevel.AVCProfileHigh10
            122 -> CodecProfileLevel.AVCProfileHigh422
            244 -> CodecProfileLevel.AVCProfileHigh444
            else -> 0
        }
        val level = if (levelIdc == 11 && constraints and 0x10 != 0 && profileIdc in setOf(66, 77, 88)) CodecProfileLevel.AVCLevel1b
            else AVC_LEVELS[levelIdc] ?: 0
        return VideoDetails(depth(lumaBits, chromaBits), chroma(chromaIdc), profile, level)
    }

    private fun parseHevcSps(nal: ByteArray): VideoDetails {
        require(nal.size >= 4 && nal[0].toInt() and 0x80 == 0 && nal[1].toInt() and 7 != 0)
        val bits = RbspBits(nal, 2)
        bits.skip(4)
        val subLayers = bits.read(3)
        require(subLayers <= 6)
        bits.skip(1)
        bits.skip(2)
        val highTier = bits.read(1) == 1
        val profileIdc = bits.read(5)
        bits.skip(32 + 48)
        val levelIdc = bits.read(8)
        val profilePresent = BooleanArray(subLayers)
        val levelPresent = BooleanArray(subLayers)
        for (index in 0 until subLayers) {
            profilePresent[index] = bits.read(1) == 1
            levelPresent[index] = bits.read(1) == 1
        }
        if (subLayers > 0) bits.skip((8 - subLayers) * 2)
        for (index in 0 until subLayers) {
            if (profilePresent[index]) bits.skip(88)
            if (levelPresent[index]) bits.skip(8)
        }
        require(bits.ue() <= 15)
        val chromaIdc = bits.ue()
        require(chromaIdc in 0..3)
        if (chromaIdc == 3) bits.skip(1)
        require(bits.ue() > 0 && bits.ue() > 0)
        if (bits.read(1) == 1) repeat(4) { bits.ue() }
        val lumaBits = 8 + bits.ue()
        val chromaBits = 8 + bits.ue()
        require(lumaBits in 8..16 && chromaBits in 8..16)
        val profile = when (profileIdc) {
            1 -> CodecProfileLevel.HEVCProfileMain
            2 -> CodecProfileLevel.HEVCProfileMain10
            3 -> if (Build.VERSION.SDK_INT >= 28) CodecProfileLevel.HEVCProfileMainStill else 0
            else -> 0
        }
        val mainLevel = HEVC_LEVELS[levelIdc] ?: 0
        return VideoDetails(depth(lumaBits, chromaBits), chroma(chromaIdc), profile, if (highTier) mainLevel shl 1 else mainLevel)
    }

    /** Bounded RBSP reader: malformed/truncated CSD remains unknown rather than guessing a layout. */
    private class RbspBits(nal: ByteArray, headerBytes: Int) {
        private val bytes: ByteArray
        private var position = 0
        init {
            val output = ByteArray(nal.size - headerBytes)
            var size = 0
            var zeros = 0
            for (index in headerBytes until nal.size) {
                val value = nal[index].toInt() and 0xff
                if (zeros >= 2 && value == 3 && index + 1 < nal.size && nal[index + 1].toInt() and 0xff <= 3) {
                    zeros = 0
                    continue
                }
                output[size++] = nal[index]
                zeros = if (value == 0) zeros + 1 else 0
            }
            bytes = output.copyOf(size)
        }
        fun read(count: Int): Int {
            require(count in 0..31 && position + count <= bytes.size * 8)
            var result = 0
            repeat(count) {
                result = (result shl 1) or ((bytes[position / 8].toInt() ushr (7 - position % 8)) and 1)
                position++
            }
            return result
        }
        fun skip(count: Int) {
            require(count >= 0 && position + count <= bytes.size * 8)
            position += count
        }
        fun ue(): Int {
            var zeros = 0
            while (read(1) == 0) { zeros++; require(zeros < 31) }
            return ((1L shl zeros) - 1 + read(zeros)).also { require(it <= Int.MAX_VALUE) }.toInt()
        }
    }

    private fun depth(luma: Int, chroma: Int): ColorDepth = if (luma != chroma) ColorDepth.UNKNOWN else when (luma) {
        8 -> ColorDepth.EIGHT
        10 -> ColorDepth.TEN
        12 -> ColorDepth.TWELVE
        else -> ColorDepth.UNKNOWN
    }
    private fun chroma(idc: Int): ChromaFormat = when (idc) {
        1 -> ChromaFormat.YUV420
        2 -> ChromaFormat.YUV422
        3 -> ChromaFormat.YUV444
        else -> ChromaFormat.UNKNOWN
    }

    private const val MAX_CSD_BYTES = 65_536
    private val AVC_LEVELS = mapOf(
        10 to CodecProfileLevel.AVCLevel1, 11 to CodecProfileLevel.AVCLevel11, 12 to CodecProfileLevel.AVCLevel12,
        13 to CodecProfileLevel.AVCLevel13, 20 to CodecProfileLevel.AVCLevel2, 21 to CodecProfileLevel.AVCLevel21,
        22 to CodecProfileLevel.AVCLevel22, 30 to CodecProfileLevel.AVCLevel3, 31 to CodecProfileLevel.AVCLevel31,
        32 to CodecProfileLevel.AVCLevel32, 40 to CodecProfileLevel.AVCLevel4, 41 to CodecProfileLevel.AVCLevel41,
        42 to CodecProfileLevel.AVCLevel42, 50 to CodecProfileLevel.AVCLevel5, 51 to CodecProfileLevel.AVCLevel51,
        52 to CodecProfileLevel.AVCLevel52
    )
    private val HEVC_LEVELS = mapOf(
        30 to CodecProfileLevel.HEVCMainTierLevel1, 60 to CodecProfileLevel.HEVCMainTierLevel2,
        63 to CodecProfileLevel.HEVCMainTierLevel21, 90 to CodecProfileLevel.HEVCMainTierLevel3,
        93 to CodecProfileLevel.HEVCMainTierLevel31, 120 to CodecProfileLevel.HEVCMainTierLevel4,
        123 to CodecProfileLevel.HEVCMainTierLevel41, 150 to CodecProfileLevel.HEVCMainTierLevel5,
        153 to CodecProfileLevel.HEVCMainTierLevel51, 156 to CodecProfileLevel.HEVCMainTierLevel52,
        180 to CodecProfileLevel.HEVCMainTierLevel6, 183 to CodecProfileLevel.HEVCMainTierLevel61,
        186 to CodecProfileLevel.HEVCMainTierLevel62
    )
}
