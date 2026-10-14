package br.com.riseedit

import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.diagnostics.Diagnostics
import br.com.riseedit.diagnostics.LogRing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ringKeepsNewestLines() {
        val ring = LogRing(3)
        (1..5).forEach { ring.add("l$it") }
        assertEquals(listOf("l3", "l4", "l5"), ring.snapshot())
    }

    @Test
    fun reportContainsRingAndShareIntentUsesFileProvider() {
        Diagnostics.install(context)
        Diagnostics.log("teste", "marcador-diagnostico")
        val report = Diagnostics.buildReport(context, "teste")
        assertTrue(report.contains("marcador-diagnostico"))
        assertTrue(report.contains("=== logcat"))
        val chooser = Diagnostics.shareIntent(context, "teste")
        val send = chooser.getParcelableExtra<android.content.Intent>(
            android.content.Intent.EXTRA_INTENT
        )!!
        val uri = send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        context.contentResolver.openInputStream(uri)!!.use {
            assertTrue(it.readBytes().isNotEmpty())
        }
    }
}
