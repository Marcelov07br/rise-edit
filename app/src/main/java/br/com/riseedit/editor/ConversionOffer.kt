package br.com.riseedit.editor

import android.net.Uri
import br.com.riseedit.core.MediaFault

/** 0.21: "Converter para edição" offer shown after an import hit a 10-bit/4:2:2 file. */
data class ConversionOffer(val uris: List<String>, val reason: String) {
    val count get() = uris.size

    companion object {
        fun canConvert(error: Throwable): Boolean =
            (error is MediaFault.UnsupportedChroma || error is MediaFault.Unsupported10Bit) &&
                br.com.riseedit.transcode.FfmpegIngest.available

        fun of(items: List<Pair<Uri, String>>) =
            ConversionOffer(items.map { it.first.toString() }, items.first().second)
    }
}
