package br.com.riseedit.core.perf

import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Marker
import br.com.riseedit.core.Project
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.VideoOverlay
import kotlin.random.Random

/**
 * Marce's typical timeline (synthetic, seeded): ~45 min main track of ~2000 multicam cuts
 * (0.5–5 s, skewed short) alternating 4 cameras, 2 overlay lanes of b-roll, texts, a continuous
 * 45-min voice/board audio, music beds and SFX → 7 visible lanes.
 */
object PerfFixture {
    const val CLIPS = 2000
    private const val CAM_MS = 50L * 60_000

    fun marce(seed: Int = 22): Project {
        val r = Random(seed)
        val cams = (1..4).map { "content://media/external/video/media/cam$it" }
        val cursor = LongArray(4)
        var last = -1
        val clips = (0 until CLIPS).map { i ->
            var cam: Int
            do { cam = r.nextInt(if (i % 7 == 0) 4 else 3) } while (cam == last)
            last = cam
            val x = r.nextDouble()
            val len = (500 + 4500 * x * x * x * x).toLong()
            val start = (cursor[cam] + r.nextLong(0, 1500)).coerceAtMost(CAM_MS - len - 1)
            cursor[cam] = start + len
            Clip(id = "c$i", uri = cams[cam], name = "CAM${cam + 1}", sourceDurationMs = CAM_MS, startMs = start, endMs = start + len)
        }
        val total = clips.sumOf { it.endMs - it.startMs }
        val overlays = (0 until 80).map { i ->
            val len = r.nextLong(2_000, 8_000)
            VideoOverlay(
                id = "o$i",
                clip = Clip(id = "oc$i", uri = "content://media/broll$i", name = "B-roll $i", sourceDurationMs = 20_000, endMs = len),
                offsetMs = total * i / 80,
                track = i % 2
            )
        }
        val texts = (0 until 40).map { i ->
            val s = total * i / 40 + 500
            TextLayer(id = "t$i", text = "Lower third $i", startMs = s, endMs = s + 4000)
        }
        val voice = AudioClip(id = "a-voice", uri = "content://media/audio/board", name = "Mesa (contínuo)", sourceDurationMs = total + 60_000, endMs = total, track = 0, volume = 1f)
        val music = (0 until 12).map { i ->
            val len = total / 12
            AudioClip(id = "m$i", uri = "content://media/audio/music$i", name = "Trilha $i", sourceDurationMs = len + 10_000, offsetMs = len * i, endMs = len, track = 1)
        }
        val sfx = (0 until 120).map { i ->
            AudioClip(id = "s$i", uri = "content://media/audio/sfx${i % 10}", name = "SFX $i", sourceDurationMs = 1500, offsetMs = total * i / 120 + 200, track = 2)
        }
        val markers = (0 until 60).map { Marker(id = "k$it", timeMs = total * it / 60) }
        return Project(
            clips = clips, id = "perf-marce-45min", name = "Perf · Marce 45 min", modifiedAt = 1_792_000_000_000L,
            audio = listOf(voice) + music + sfx, texts = texts, overlays = overlays, markers = markers
        )
    }
}
