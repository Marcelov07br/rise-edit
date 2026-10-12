@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])

package br.com.riseedit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import br.com.riseedit.preview.PreviewQuality
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Exercises the real Compose -> AndroidView -> PlayerView binding for both preview backends. */
class PreviewUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun realPlayerViewsSwitchQualityRenderPixelsAndPauseWhenLeavingEditor() {
        val source = File.createTempFile("preview-ui-720p-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("compatibility/h264_720p_scrub.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val uri = android.net.Uri.fromFile(source).toString()
        val first = Clip(uri = uri, name = "720p UI first", sourceDurationMs = 3_000)
        val project = Project(listOf(first, first.copy(id = UUID.randomUUID().toString(), name = "720p UI second")),
            name = "Prévia UI ${UUID.randomUUID().toString().take(8)}")
        val store = ProjectStore(context)
        var activity: MainActivity? = null
        try {
            store.save(project)
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            val host = activity
            val vm = onMain { ViewModelProvider(host).get(EditorViewModel::class.java) }
            waitNode("RISE EDIT")
            // Opening through the actual project card also changes MainActivity's active project route.
            click(project.name, scroll = true)
            waitUntil("Project did not finish opening") { onMain { vm.state.value.project.id == project.id && vm.state.value.busy == null } }
            val fast = awaitPreview(host, vm, PreviewQuality.Fast)
            assertTrue(onMain { fast.videoSurfaceView is TextureView })
            assertTrue(onMain { vm.player is ExoPlayer })
            assertEditorControlsVisible()
            screenshot("preview-fast-test.png")

            click("Fiel")
            val faithful = awaitPreview(host, vm, PreviewQuality.Faithful)
            assertTrue(onMain { faithful.videoSurfaceView is SurfaceView })
            assertTrue(onMain { vm.player is CompositionPlayer })
            assertEditorControlsVisible()
            screenshot("preview-faithful-test.png")

            click("Rápida")
            val returnedFast = awaitPreview(host, vm, PreviewQuality.Fast)
            assertTrue(onMain { returnedFast.videoSurfaceView is TextureView })
            assertTrue(onMain { vm.player is ExoPlayer })
            assertNull(onMain { vm.state.value.previewFault })
            assertEditorControlsVisible()

            onMain { vm.seek(300); if (!vm.player.playWhenReady) vm.togglePlay() }
            waitUntil("Fast preview did not start playing") { onMain { vm.player.isPlaying } }
            click("Projetos")
            waitNode("RISE EDIT")
            waitUntil("Returning home left preview playback active") { onMain { !vm.player.isPlaying && !vm.player.playWhenReady } }

            click(project.name, scroll = true)
            waitUntil("Project did not reopen") { onMain { vm.state.value.busy == null && vm.state.value.project.id == project.id } }
            awaitPreview(host, vm, PreviewQuality.Fast)
            assertFalse("Reopening the editor resumed old play intent", onMain { vm.player.playWhenReady })

            onMain { vm.seek(300); vm.togglePlay() }
            waitUntil("Preview did not play before Activity.onStop") { onMain { vm.player.isPlaying } }
            // A real Home action drives the activity lifecycle instead of directly invoking its callback.
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
            waitUntil("Activity did not stop after Home") { onMain { !host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) } }
            waitUntil("Activity.onStop left old play intent active") { onMain { !vm.player.isPlaying && !vm.player.playWhenReady } }
        } finally {
            activity?.let { host -> onMain { host.finish() } }
            store.delete(project.id)
            source.delete()
        }
    }

    private fun awaitPreview(activity: MainActivity, vm: EditorViewModel, quality: PreviewQuality): PlayerView {
        var found: PlayerView? = null
        waitUntil("$quality preview did not bind and render real pixels") {
            val view = onMain {
                val state = vm.state.value
                if (state.busy != null || state.previewQuality != quality || state.previewFault != null || vm.player.playbackState != Player.STATE_READY) null
                else playerViews(activity.window.decorView).firstOrNull { it.isShown && it.player === vm.player }
            } ?: return@waitUntil false
            if (hasSourcePixels(view)) { found = view; true } else false
        }
        return requireNotNull(found)
    }

    private fun playerViews(view: View): List<PlayerView> = when (view) {
        is PlayerView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { playerViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun assertEditorControlsVisible() = waitUntil("The video surface covered the Exportar control") {
        val node = find(instrumentation.uiAutomation.rootInActiveWindow, "Exportar") ?: return@waitUntil false
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return@waitUntil false
        try {
            if (!bounds.intersect(0, 0, bitmap.width, bitmap.height)) return@waitUntil false
            (1..6).any { row -> (1..6).any { column ->
                val pixel = bitmap.getPixel(bounds.left + column * bounds.width() / 7, bounds.top + row * bounds.height() / 7)
                Color.green(pixel) > 180 && Color.red(pixel) > 100 && Color.blue(pixel) < 150
            } }
        } finally { bitmap.recycle() }
    }

    private fun hasSourcePixels(playerView: PlayerView): Boolean {
        val output = onMain { playerView.videoSurfaceView } ?: return false
        val size = onMain { output.width to output.height }
        if (size.first <= 0 || size.second <= 0) return false
        val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
        try {
            val copied = when (output) {
                is TextureView -> onMain { output.isAvailable && output.getBitmap(bitmap) != null }
                is SurfaceView -> {
                    if (!onMain { output.holder.surface.isValid }) return false
                    val done = CountDownLatch(1)
                    val result = AtomicInteger(-1)
                    onMain { PixelCopy.request(output, bitmap, { status -> result.set(status); done.countDown() }, Handler(Looper.getMainLooper())) }
                    done.await(2, TimeUnit.SECONDS) && result.get() == PixelCopy.SUCCESS
                }
                else -> false
            }
            if (!copied) return false
            // The testsrc2 fixture contains saturated colors; black/shutter pixels cannot satisfy this.
            return (1..8).any { row -> (1..8).any { column ->
                val color = bitmap.getPixel(column * bitmap.width / 9, row * bitmap.height / 9)
                val channels = listOf(Color.red(color), Color.green(color), Color.blue(color))
                channels.max() > 70 && channels.max() - channels.min() > 40
            } }
        } finally { bitmap.recycle() }
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        screenshot("preview-ui-failure.png")
        throw AssertionError("$message; ${dump(instrumentation.uiAutomation.rootInActiveWindow)}")
    }

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val actual = node.text?.toString()?.trim()?.removePrefix("• ")
        if (node.isVisibleToUser && (actual == text || actual == "‹ $text" || actual?.lineSequence()?.any { it.trim() == text } == true)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), text)?.let { return it }
        return null
    }

    private fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }) return node
        for (index in 0 until node.childCount) scrollable(node.getChild(index))?.let { return it }
        return null
    }

    private fun waitNode(text: String, scroll: Boolean = false): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        waitUntil("Missing UI: $text") {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            found = find(root, text)
            if (found == null && scroll) scrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            found != null
        }
        return requireNotNull(found)
    }

    private fun click(text: String, scroll: Boolean = false) {
        var node: AccessibilityNodeInfo? = waitNode(text, scroll)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("No clickable parent for $text", node)
        assertTrue("UI action failed for $text", node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun screenshot(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val file = File(context.getExternalFilesDir(null), name)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/$name").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        } finally { bitmap.recycle() }
    }

    private fun dump(node: AccessibilityNodeInfo?): String = if (node == null) "<no active window>" else
        "[${node.text} / ${node.contentDescription}]" + (0 until node.childCount).joinToString { dump(node.getChild(it)) }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable?>()
        instrumentation.runOnMainSync { try { result.set(block()) } catch (failure: Throwable) { error.set(failure) } }
        error.get()?.let { throw it }
        return result.get()
    }
}
