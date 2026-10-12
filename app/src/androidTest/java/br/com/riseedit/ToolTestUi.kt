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

    fun waitFor(text: String, scroll: Boolean = false): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        await {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            result = find(root) { node ->
                node.text?.contains(text) == true || node.contentDescription?.contains(text) == true
            }
            if (result == null && scroll) {
                find(root) { node ->
                    node.isScrollable &&
                        node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
                }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            result != null
        }
        return checkNotNull(result) { "Missing tool UI: $text" }
    }

    fun click(text: String, scroll: Boolean = false) {
        var node: AccessibilityNodeInfo? = waitFor(text, scroll)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            "Cannot click $text"
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
