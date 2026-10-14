package br.com.riseedit

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicReference

internal class ToolTestUi(private val instrumentation: Instrumentation) {
    private var toolbar: Rect? = null
    private val toolLabels = listOf(
        "Estabilizar",
        "Cortar",
        "Texto",
        "Áudio",
        "Overlay",
        "Cor",
        "Formato"
    )
    fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(action()) }
        return result.get()
    }

    fun await(
        timeoutMs: Long = 15000,
        description: () -> String = { "Editor did not reach the expected state" },
        condition: () -> Boolean
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error(description())
    }

    fun waitFor(
        text: String,
        scroll: Boolean = false,
        requireClick: Boolean = false
    ): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        val toolbarControl = requireClick && text in toolLabels
        await(description = {
            screenshot("tool-failure")
            "Missing tool UI: $text; toolbar=$toolbar; ${labels(
                root()
            )}"
        }) {
            val root = root()
            if (toolbarControl) toolbarBounds(root)?.let { toolbar = it }
            result = find(root) { node ->
                node.isVisibleToUser &&
                    matchesLabel(node, text) &&
                    (!requireClick || clickableParent(node) != null)
            }
            if (result == null && scroll) {
                val bounds = toolbar
                if (toolbarControl) {
                    if (bounds != null) swipeToolbar(bounds, forward = true)
                } else {
                    lastScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }
            }
            result != null
        }
        return checkNotNull(result) { "Missing tool UI: $text" }
    }

    fun isVisible(label: String): Boolean =
        find(root()) { it.isVisibleToUser && matchesLabel(it, label) } != null

    /** Types into the editable field labelled [label]. */
    fun setText(label: String, value: String) {
        var field: AccessibilityNodeInfo? = null
        await(description = { "Missing field $label" }) {
            field = find(root()) { it.isEditable && it.isVisibleToUser && matchesLabel(it, label) }
                ?: find(root()) { it.isEditable && it.isVisibleToUser }
            field != null
        }
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        check(checkNotNull(field).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            "Cannot type into $label"
        }
    }

    fun click(text: String, scroll: Boolean = false) {
        if (text in toolLabels) resetToolbar()
        val node = clickableParent(waitFor(text, scroll, requireClick = true))
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            "Cannot click $text"
        }
    }

    private fun matchesLabel(node: AccessibilityNodeInfo, text: String): Boolean =
        listOfNotNull(node.text, node.contentDescription).any { label ->
            label.lineSequence().any { it.trim().trimStart('‹', '◆', '✦', ' ') == text }
        }

    private fun toolbarBounds(root: AccessibilityNodeInfo?): Rect? {
        val anchor = find(root) { matchesLabel(it, "Estabilizar") && it.isVisibleToUser }
            ?: return null
        // Only the toolbar button (clickable) counts as the anchor.
        if (clickableParent(anchor) == null) return null
        val bounds = Rect()
        anchor.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return null
        bounds.left = 0
        bounds.right = instrumentation.targetContext.resources.displayMetrics.widthPixels
        return bounds
    }

    private fun resetToolbar() {
        await {
            val root = root()
            val closing = find(root) { matchesLabel(it, "Concluir") && it.isVisibleToUser }
            if (closing != null) return@await false
            toolbarBounds(root)?.let {
                toolbar = it
                return@await true
            }
            val metrics = instrumentation.targetContext.resources.displayMetrics
            val screenHeight = metrics.heightPixels
            val margin = (100 * metrics.density).toInt()
            val button = find(root) { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                node.isVisibleToUser &&
                    toolLabels.any { matchesLabel(node, it) } &&
                    clickableParent(node) != null &&
                    bounds.top >= screenHeight - margin
            }
            button?.let {
                val bounds = Rect()
                it.getBoundsInScreen(bounds)
                bounds.left = 0
                bounds.right = instrumentation.targetContext.resources.displayMetrics.widthPixels
                toolbar = bounds
                swipeToolbar(bounds, forward = false)
            }
            false
        }
    }

    private fun swipeToolbar(bounds: Rect, forward: Boolean) {
        val from = (bounds.width() * if (forward) 0.75f else 0.2f).toInt()
        val to = (bounds.width() * if (forward) 0.45f else 0.85f).toInt()
        val duration = if (forward) 400 else 100
        // The label is close to Android's bottom gesture region; swipe through the glyph row.
        val y =
            bounds.top -
                (12 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
        instrumentation.uiAutomation.executeShellCommand("input swipe $from $y $to $y $duration")
            .use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        SystemClock.sleep(250)
    }

    fun screenshot(name: String) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        instrumentation.uiAutomation.executeShellCommand(
            "cp ${file.absolutePath} /sdcard/Download/$name.png"
        )
            .use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }

    private fun labels(node: AccessibilityNodeInfo?): String {
        if (node == null) return "<no active window>"
        val children = (0 until node.childCount).joinToString { labels(node.getChild(it)) }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return "[${node.text}/${node.contentDescription}; " +
            "visible=${node.isVisibleToUser}; bounds=$bounds]$children"
    }

    private fun root(): AccessibilityNodeInfo? {
        // Compose scrolls visually before UiAutomation's cached virtual nodes are invalidated.
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }

    private fun clickableParent(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = start
        while (node != null && (!node.isClickable || !node.isEnabled)) node = node.parent
        return node
    }

    private fun lastScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        for (index in node.childCount - 1 downTo 0) {
            lastScrollable(node.getChild(index))?.let { return it }
        }
        return node.takeIf {
            it.isScrollable &&
                it.isVisibleToUser &&
                it.actionList.any { action ->
                    action.id ==
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
        }
    }

    private fun find(
        node: AccessibilityNodeInfo?,
        matches: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (matches(node)) return node
        for (index in 0 until node.childCount) {
            find(node.getChild(index), matches)?.let {
                return it
            }
        }
        return null
    }
}
