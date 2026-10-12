package br.com.riseedit

import android.app.Instrumentation
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference

internal class ToolTestUi(private val instrumentation: Instrumentation) {
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
        var toolbar: Rect? = null
        await(description = {
            "Missing tool UI: $text; ${labels(instrumentation.uiAutomation.rootInActiveWindow)}"
        }) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            result = find(root) { node ->
                node.isVisibleToUser &&
                    matchesLabel(node, text) &&
                    (!requireClick || clickableParent(node) != null)
            }
            if (result == null && scroll) {
                toolbar = toolbar ?: toolbarBounds(root)
                val bounds = toolbar
                if (bounds != null) {
                    swipeToolbar(bounds)
                } else {
                    lastScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }
            }
            result != null
        }
        return checkNotNull(result) { "Missing tool UI: $text" }
    }

    fun click(text: String, scroll: Boolean = false) {
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
        val anchor = find(root) { matchesLabel(it, "Estabilização Rise") && it.isVisibleToUser }
            ?: return null
        val button = clickableParent(anchor) ?: return null
        val bounds = Rect()
        button.getBoundsInScreen(bounds)
        val screen = Rect()
        root?.getBoundsInScreen(screen)
        bounds.left = screen.left
        bounds.right = screen.right
        return bounds
    }

    private fun swipeToolbar(bounds: Rect) {
        // A full-page accessibility scroll can skip a partially clipped toolbar button.
        val downTime = SystemClock.uptimeMillis()
        for (step in 0..10) {
            val action = when (step) {
                0 -> MotionEvent.ACTION_DOWN
                10 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val x = bounds.left + bounds.width() * (0.8f - step * 0.04f)
            val event = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                x,
                bounds.exactCenterY(),
                0
            )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
            SystemClock.sleep(25)
        }
    }

    private fun labels(node: AccessibilityNodeInfo?): String {
        if (node == null) return "<no active window>"
        val children = (0 until node.childCount).joinToString { labels(node.getChild(it)) }
        return "[${node.text}/${node.contentDescription}; visible=${node.isVisibleToUser}]$children"
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
