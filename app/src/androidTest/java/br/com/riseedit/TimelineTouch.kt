package br.com.riseedit

import android.app.Instrumentation
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertTrue

/** Injects real touch contacts, including both fingers of a pinch. */
internal class TimelineTouch(private val instrumentation: Instrumentation) {
    fun drag(x: Float, y: Float, endX: Float, cancel: Boolean = false) {
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, listOf(x to y))
        repeat(20) { step ->
            SystemClock.sleep(16)
            send(down, MotionEvent.ACTION_MOVE, listOf((x + (endX - x) * (step + 1) / 20) to y))
        }
        send(
            down,
            if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,
            listOf(
                endX to y
            )
        )
        SystemClock.sleep(300)
    }

    fun pinch(center: Float, y: Float, spread: Float, factor: Float) {
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, listOf((center - spread) to y))
        send(
            down,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf((center - spread) to y, (center + spread) to y)
        )
        repeat(24) { step ->
            SystemClock.sleep(16)
            val radius = spread * (1 + (factor - 1) * (step + 1) / 24)
            send(
                down,
                MotionEvent.ACTION_MOVE,
                listOf(
                    (center - radius) to y,
                    (center + radius) to y
                )
            )
        }
        val final = listOf((center - spread * factor) to y, (center + spread * factor) to y)
        send(
            down,
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            final
        )
        send(down, MotionEvent.ACTION_UP, final.take(1))
        SystemClock.sleep(300)
    }

    private fun send(down: Long, action: Int, points: List<Pair<Float, Float>>) {
        val properties = points.indices.map { id ->
            MotionEvent.PointerProperties().apply {
                this.id = id
                toolType =
                    MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coordinates = points.map { (px, py) ->
            MotionEvent.PointerCoords().apply {
                x = px
                y = py
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        val event = MotionEvent.obtain(
            down, SystemClock.uptimeMillis(), action, points.size,
            properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            assertTrue(
                "Touch injection failed",
                instrumentation.uiAutomation.injectInputEvent(event, true)
            )
        } finally {
            event.recycle()
        }
    }
}
