package br.com.riseedit

import android.app.Instrumentation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference

internal class ToolTestUi(private val instrumentation: Instrumentation) {
    fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(action()) }
        return result.get()
    }

    fun await(timeoutMs: Long = 15000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Editor did not reach the expected state")
    }

    fun waitFor(
        text: String,
        scroll: Boolean = false,
        requireClick: Boolean = false
    ): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        await {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            result = find(root) { node ->
                node.isVisibleToUser &&
                    matchesLabel(node, text) &&
                    (!requireClick || clickableParent(node) != null)
            }
            if (result == null && scroll) {
                // The editor's toolbar follows the timeline in the accessibility tree.
                lastScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
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
