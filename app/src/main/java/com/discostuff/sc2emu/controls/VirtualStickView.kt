package com.discostuff.sc2emu.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class VirtualStickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    data class StickPosition(
        val x: Float, // -1..1
        val y: Float, // -1..1 (up = +1)
    )

    var onStickChanged: ((StickPosition) -> Unit)? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 80, 180, 255)
        style = Paint.Style.FILL
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var xNorm = 0f
    private var yNorm = 0f

    fun setCentered(notify: Boolean) {
        xNorm = 0f
        yNorm = 0f
        if (notify) {
            onStickChanged?.invoke(StickPosition(xNorm, yNorm))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.42f

        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crossPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crossPaint)

        val knobRadius = radius * 0.28f
        val knobCx = cx + xNorm * radius
        val knobCy = cy - yNorm * radius
        canvas.drawCircle(knobCx, knobCy, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                setCentered(notify = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(touchX: Float, touchY: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.42f

        var dx = touchX - cx
        var dy = touchY - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > radius && dist > 0f) {
            val scale = radius / dist
            dx *= scale
            dy *= scale
        }

        xNorm = (dx / radius).coerceIn(-1f, 1f)
        yNorm = (-dy / radius).coerceIn(-1f, 1f)
        onStickChanged?.invoke(StickPosition(xNorm, yNorm))
        invalidate()
    }
}
