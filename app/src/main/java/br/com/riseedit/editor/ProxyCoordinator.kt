package br.com.riseedit.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.Project
import br.com.riseedit.core.ProxyPolicy
import br.com.riseedit.media.ProxyTranscoder
import br.com.riseedit.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 0.24.0: background proxy generation progress (drawn as the render bubble). */
object ProxyBus {
    private val mutable = MutableStateFlow(ExportBus.State())
    val state = mutable.asStateFlow()
    /** Bubble expanded into the small card (Cancelar). */
    val expanded = MutableStateFlow(false)
    fun set(s: ExportBus.State) { mutable.value = s }
    fun progress(value: Float, phase: String) { mutable.value = mutable.value.copy(progress = value, phase = phase) }
    var cancel: () -> Unit = {}
}

/**
 * 0.24.0: optional preview proxies for heavy media (4K, 10 bits, 4:2:2, high bitrate) built in
 * the background, one at a time, without blocking the editor. The proxy is keyed to the original
 * file (uri + size + mtime + spec) in the proxy cache and attached only to clips whose
 * [Clip.uri] still matches; [Clip.proxyRequired] stays false, so export reads the original.
 */
internal class ProxyCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val document: EditorDocument
) {
    private var job: Job? = null
    private val failed = mutableSetOf<String>()

    init { ProxyBus.cancel = ::cancel }

    /** Re-scan whenever the set of proxy-less clips changes (import, load, undo). */
    fun watch() = scope.launch {
        document.state.map { s -> s.project.id to pending(s.project).map { it.uri } }
            .distinctUntilChanged()
            .collect { (_, uris) -> if (uris.isNotEmpty()) scan() }
    }

    private fun pending(p: Project) = (p.clips + p.overlays.map { it.clip })
        .filter { !it.proxyRequired && it.proxyUri.isNullOrBlank() && it.sourceInfo != null && it.uri !in failed }
        .distinctBy { it.uri }

    fun scan(force: Boolean = false) {
        if (job?.isActive == true) return
        if (!force && !SettingsStore.current.autoProxy4k) return
        job = scope.launch { run(force) }
    }

    fun cancel() { job?.cancel() }

    private suspend fun run(force: Boolean) {
        val settings = SettingsStore.current
        val todo = pending(document.current.project).filter { c ->
            force || ProxyPolicy.heavy(c.sourceInfo!!, bitrate(c))
        }
        if (todo.isEmpty()) return
        val transcoder = ProxyTranscoder(context)
        ProxyBus.set(ExportBus.State(active = true, startedAt = System.currentTimeMillis(), minimized = true,
            phase = "Proxy 1/${todo.size}"))
        var made = 0
        try {
            todo.forEachIndexed { i, clip ->
                val info = clip.sourceInfo!!
                val spec = ProxyPolicy.spec(info, settings.proxyResolution, settings.proxyCodec)
                val label = "Proxy ${i + 1}/${todo.size} · ${clip.name}"
                ProxyBus.progress(i.toFloat() / todo.size, label)
                try {
                    val uri = transcoder.prepare(Uri.parse(clip.uri), info, spec) { p, _ ->
                        ProxyBus.progress((i + p) / todo.size, label)
                    }
                    attach(clip.uri, uri.toString())
                    made++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += clip.uri
                    document.message("Proxy de \"${clip.name}\" falhou: ${(e as? MediaFault)?.userMessage ?: "erro do codificador"}. A prévia usa o original.")
                }
            }
            if (made > 0) document.message("$made proxy(s) ${settings.proxyResolution.label} ${settings.proxyCodec.label} prontos · a exportação usa o original.")
        } finally {
            ProxyBus.set(ExportBus.State())
            ProxyBus.expanded.value = false
        }
    }

    /** No undo step: attaching a preview copy is not an edit. */
    private fun attach(original: String, proxy: String) {
        val p = document.current.project
        fun Clip.with() = if (uri == original && !proxyRequired) copy(proxyUri = proxy) else this
        document.attach(p.copy(clips = p.clips.map { it.with() }, overlays = p.overlays.map { it.copy(clip = it.clip.with()) }))
    }

    private suspend fun bitrate(c: Clip): Long = withContext(Dispatchers.IO) {
        val size = runCatching {
            val u = Uri.parse(c.uri)
            if (u.scheme == "file") java.io.File(u.path.orEmpty()).length()
            else context.contentResolver.query(u, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
        if (size <= 0 || c.sourceDurationMs <= 0) 0L else size * 8_000 / c.sourceDurationMs
    }
}
