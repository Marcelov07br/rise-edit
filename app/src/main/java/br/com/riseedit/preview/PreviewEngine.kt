@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit.preview

import android.content.Context
import androidx.annotation.MainThread
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import br.com.riseedit.VideoEngine
import br.com.riseedit.core.*
import br.com.riseedit.media.DeviceCapability
import kotlinx.coroutines.*

enum class PreviewQuality(val label: String) { Fast("Rápida"), Faithful("Fiel") }

/** Main-thread owner. Only one engine holds decoding resources; fast is the default, without a GL graph. */
@MainThread
class PreviewEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
    private val onMessage: (String) -> Unit
) {
    private val context = context.applicationContext
    private var project = Project()
    private var timeline = PreviewTimeline(project.clips)
    private var analyses = emptyMap<String, MotionAnalysis>()
    private var suppliedFaults = emptyMap<String, MediaFault>()
    private val runtimeFaults = mutableMapOf<String, MediaFault>()
    /** False after the color effect failed on this device: fast preview runs without it. */
    var colorPreviewAvailable = true
        private set
    private var fast: FastPreview? = FastPreview(this.context)
    private var faithful: FaithfulPreview? = null
    private var lastPlayer: Player = fast!!.player
    private var lastPosition = 0L
    private var playing = false
    private var muted = false
    private var scrubbing = false
    private var released = false
    private var revision = 0L
    private var pending: Job? = null
    private val ticker = scope.launch(Dispatchers.Main.immediate) { while (isActive) { updatePlayback(); delay(50) } }
    var quality: PreviewQuality = PreviewQuality.Fast
        private set
    /** Fiel is the default; Rápida is an optional scrub mode. */
    var requestedQuality: PreviewQuality = PreviewQuality.Faithful
        private set
    /** Why Fiel is not running although it was requested; shown explicitly instead of silently dropping layers. */
    var degraded: String? = null
        private set
    /** Number of CompositionPlayer instances created; lets tests prove edits reuse the player. */
    var faithfulPlayersCreated = 0
        private set
    val player: Player get() = lastPlayer
    val positionMs: Long get() = runCatching { fast?.positionMs ?: faithful?.player?.currentPosition ?: lastPosition }
        .getOrDefault(lastPosition).coerceIn(0, timeline.durationMs)
    val durationMs: Long get() = timeline.durationMs
    val activeClip: Clip? get() = project.clips.getOrNull(timeline.indexAt(positionMs))
    val activeSourceMs: Long get() {
        val index = timeline.indexAt(positionMs)
        return timeline.clips.getOrNull(index)?.sourceAt(timeline.localOutput(index, positionMs)) ?: 0
    }
    val fault: MediaFault? get() = activeClip?.let { suppliedFaults[it.id] ?: runtimeFaults[it.id] }
    init { listen(lastPlayer) }

    fun update(project: Project, analyses: Map<String, MotionAnalysis>, faults: Map<String, MediaFault>,
               positionMs: Long, quality: PreviewQuality = this.quality) {
        if (released) return
        pending?.cancel(); revision++
        requestedQuality = quality
        val projectChanged = this.project.id != project.id
        if (projectChanged) runtimeFaults.clear()
        else runtimeFaults.keys.toList().forEach { id ->
            if (this.project.clips.find { it.id == id }?.effectiveUri(true) != project.clips.find { it.id == id }?.effectiveUri(true)) runtimeFaults.remove(id)
        }
        this.project = project; timeline = PreviewTimeline(project.clips)
        this.analyses = analyses.toMap(); suppliedFaults = faults.toMap()
        lastPosition = positionMs.coerceIn(0, project.durationMs)
        if (project.clips.isEmpty()) playing = false
        if (quality == PreviewQuality.Fast || project.clips.isEmpty()) guard {
            activateFast(lastPosition)
            if (projectChanged) fast?.seek(lastPosition)
        }
        else {
            fast?.let { guard { it.update(project, allFaults(), lastPosition); it.tick(muted) } }
            val requestedRevision = revision
            val snapshotAnalyses = this.analyses
            pending = scope.launch(Dispatchers.Main.immediate) {
                delay(200)
                try {
                    val missing = (project.clips + project.overlays.map { it.clip }).any { allFaults().containsKey(it.id) }
                    if (missing) { fallback("A prévia fiel está indisponível enquanto houver arquivos ausentes."); return@launch }
                    val needed = 1 + project.overlays.count { it.offsetMs < project.durationMs }
                    // An idle renderer releases its decoder before a first-use capacity measurement.
                    lastPosition = this@PreviewEngine.positionMs
                    fast?.player?.pause(); fast?.player?.stop()
                    val capacity = DeviceCapability.profile(context).maxDecoders
                    if (needed > capacity) { fallback("Este aparelho não tem recursos para a prévia fiel com essas camadas."); return@launch }
                    val composition = withContext(Dispatchers.Default) { VideoEngine.composition(project, snapshotAnalyses, preview = true) }
                    ensureActive()
                    if (requestedRevision != revision || released) return@launch
                    val resumeAt = this@PreviewEngine.positionMs
                    val reused = faithful
                    val next = if (reused != null) {
                        // An edit replaces the composition on the same player: no new decoders or surface.
                        reused
                    } else {
                        releasePlayers()
                        FaithfulPreview(context).also { faithfulPlayersCreated++; listen(it.player) }
                    }
                    faithful = next; lastPlayer = next.player; this@PreviewEngine.quality = PreviewQuality.Faithful
                    degraded = null
                    next.prepare(composition, resumeAt)
                    next.player.volume = if (muted) 0f else 1f
                    next.player.setScrubbingModeEnabled(scrubbing)
                    next.player.playWhenReady = playing
                    onChanged()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { fallback("A prévia fiel não pôde ser preparada. A prévia rápida continua disponível.") }
            }
        }
    }

    fun seek(ms: Long) {
        if (released) return
        lastPosition = ms.coerceIn(0, project.durationMs)
        guard { fast?.seek(lastPosition); faithful?.seek(lastPosition); fast?.tick(muted) }
        onChanged()
    }
    fun togglePlay() {
        if (released || project.clips.isEmpty()) return
        if (!playing && (positionMs >= project.durationMs - 1 || player.playbackState == Player.STATE_ENDED)) seek(0)
        playing = !playing
        guard { player.playWhenReady = playing; faithful?.resetWatchdog() }
        onChanged()
    }
    fun pause() {
        if (released) return
        val wasPreparing = pending?.isActive == true
        lastPosition = positionMs
        pending?.cancel(); revision++; playing = false
        guard {
            player.pause(); faithful?.resetWatchdog()
            if (wasPreparing && fast != null && player.playbackState == Player.STATE_IDLE) activateFast(lastPosition)
        }
    }
    /** Reprepare after returning from a lifecycle/processing suspension, preserving the requested mode. */
    fun resume() {
        if (!released) update(project, analyses, suppliedFaults, lastPosition, requestedQuality)
    }
    /** Free renderer resources while import, proxy conversion or motion analysis owns the codecs. */
    fun pauseForProcessing() {
        if (released) return
        pending?.cancel(); revision++; playing = false
        lastPosition = positionMs
        guard { player.pause(); player.stop(); faithful?.resetWatchdog() }
    }
    fun scrubbing(enabled: Boolean) {
        if (released) return
        scrubbing = enabled
        guard { fast?.player?.setScrubbingModeEnabled(enabled); faithful?.player?.setScrubbingModeEnabled(enabled); faithful?.resetWatchdog() }
    }
    fun setMuted(enabled: Boolean) {
        muted = enabled
        if (!released) guard { fast?.tick(muted); faithful?.player?.volume = if (muted) 0f else 1f }
    }
    fun updatePlayback() {
        if (released) return
        guard {
            fast?.tick(muted)
            lastPosition = positionMs
            if (player.playbackState == Player.STATE_ENDED) playing = false
            if (faithful?.stalled(playing, scrubbing) == true) fallback("A prévia Fiel parou de responder neste vídeo; usando a Rápida.")
        }
    }
    fun release() {
        if (released) return
        released = true; pending?.cancel(); ticker.cancel(); releasePlayers()
    }

    private fun allFaults() = suppliedFaults + runtimeFaults
    private fun activateFast(positionMs: Long) {
        val changed = fast == null
        if (changed) { releasePlayers(); fast = FastPreview(context, colorPreviewAvailable); lastPlayer = fast!!.player; listen(lastPlayer) }
        quality = PreviewQuality.Fast
        fast!!.update(project, allFaults(), positionMs)
        fast!!.tick(muted)
        fast!!.player.setScrubbingModeEnabled(scrubbing)
        fast!!.player.playWhenReady = playing
        if (changed) onChanged()
    }
    fun clearDegraded() { degraded = null }
    private fun fallback(message: String) {
        br.com.riseedit.diagnostics.Diagnostics.log("prévia", "fiel → rápida: $message")
        degraded = message
        val position = positionMs
        pending?.cancel(); revision++
        requestedQuality = PreviewQuality.Fast
        guard { activateFast(position) }
        onMessage(message); onChanged()
    }
    private fun releasePlayers() {
        lastPosition = positionMs
        runCatching { faithful?.release() }; faithful = null
        runCatching { fast?.release() }; fast = null
    }
    private fun listen(source: Player) = source.addListener(object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) { if (!released && source === player) failed(error) }
        override fun onIsPlayingChanged(isPlaying: Boolean) { if (!released && source === player) onChanged() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { if (!released && source === player) { fast?.tick(muted); onChanged() } }
    })
    private inline fun guard(block: () -> Unit) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failed(error) }
    }
    /** 0.19.2: the empty-frame-queue failure gets one retry in FrameCompat mode, then Rápida. */
    private fun faithfulFailed(error: Exception) {
        if (!br.com.riseedit.media.FrameCompat.isFrameRegistrationFailure(error) || br.com.riseedit.media.FrameCompat.tolerant) {
            fallback(FAITHFUL_FAILED); return
        }
        br.com.riseedit.media.FrameCompat.enable(context)
        br.com.riseedit.diagnostics.Diagnostics.log("cor", "fiel: modo compatível de quadros ativado; recriando a prévia")
        val position = positionMs
        releasePlayers()
        update(project, analyses, suppliedFaults, position, PreviewQuality.Faithful)
    }
    private fun failed(error: Exception) {
        br.com.riseedit.diagnostics.Diagnostics.log("prévia", "erro do player (${quality.label})", error)
        br.com.riseedit.diagnostics.ColorDiagnostics.report(context, "prévia ${quality.label}", error, project)
        if (faithful != null) { faithfulFailed(error); return }
        val clip = fast?.currentIndex?.let { project.clips.getOrNull(it) } ?: activeClip
        if (clip == null || fault != null) { playing = false; runCatching { player.pause() }; onMessage("A prévia está indisponível para este trecho."); return }
        val position = positionMs
        if (colorPreviewAvailable && isEffectFailure(error)) {
            br.com.riseedit.diagnostics.Diagnostics.log("cor", "efeito de cor falhou na prévia: ${technical(error)}", error)
            colorPreviewAvailable = false
            releasePlayers()
            runCatching { activateFast(position) }.onFailure { playing = false }
            onMessage("A cor na prévia Rápida falhou neste aparelho; mostrando sem cor. A exportação é separada e avisa se falhar."); onChanged()
            return
        }
        runtimeFaults[clip.id] = MediaFault.PreviewFailed(technical(error), error)
        runCatching { activateFast(position) }.onFailure { playing = false; runCatching { player.pause() } }
        onMessage("Um clipe não pôde ser reproduzido. Sua posição foi preservada na timeline."); onChanged()
    }
}

internal const val FAITHFUL_FAILED = "A prévia Fiel falhou neste vídeo; usando a Rápida. A exportação é separada e avisa se falhar."

/** Video effect / GL failures (not the media file): VFP error codes or a VFP/GL cause. */
internal fun isEffectFailure(error: Throwable): Boolean {
    val code = (error as? PlaybackException)?.errorCode
    if (code != null && code in 7000..7999) return true
    var c: Throwable? = error
    while (c != null) {
        if (c is androidx.media3.common.VideoFrameProcessingException ||
            c is androidx.media3.common.util.GlUtil.GlException ||
            c is UnsupportedOperationException
        ) return true
        c = c.cause
    }
    return false
}

internal fun technical(error: Throwable): String {
    val code = (error as? PlaybackException)?.let { "${it.errorCodeName} (${it.errorCode})" }
    var root: Throwable = error
    while (root.cause != null && root.cause !== root) root = root.cause!!
    return listOfNotNull(code, "${root.javaClass.simpleName}: ${root.message}").joinToString(" · ")
}
