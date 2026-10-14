package br.com.riseedit.edit

import android.content.Context
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import br.com.riseedit.core.Clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Eyedropper: average color around (u, v) of the clip frame at [sourceMs]. */
internal object FrameSampler {
    suspend fun sample(context: Context, clip: Clip, sourceMs: Long, u: Float, v: Float): Int? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(clip.effectiveUri(true)))
                val frame = retriever.getScaledFrameAtTime(
                    sourceMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    320,
                    320
                ) ?: return@withContext null
                val cx = (u.coerceIn(0f, 1f) * (frame.width - 1)).toInt()
                val cy = (v.coerceIn(0f, 1f) * (frame.height - 1)).toInt()
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (y in (cy - 2)..(cy + 2)) {
                    for (x in (cx - 2)..(cx + 2)) {
                        if (x !in 0 until frame.width || y !in 0 until frame.height) continue
                        val p = frame.getPixel(x, y)
                        r += Color.red(p)
                        g += Color.green(p)
                        b += Color.blue(p)
                        n++
                    }
                }
                frame.recycle()
                if (n == 0) null else Color.rgb(r / n, g / n, b / n)
            } catch (error: RuntimeException) {
                null
            } finally {
                retriever.release()
            }
        }
}
