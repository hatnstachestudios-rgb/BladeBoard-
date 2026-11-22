package com.hatnstache.gpsauto.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * Paints the background color outside a rounded rectangle, plus a white frame line. The map
 * renders through a fast SurfaceView, which can't be clipped to rounded corners, so this
 * gives the minimap its rounded GTA frame instead.
 */
class CornerMaskView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xD9FFFFFF.toInt()
    }
    private val path = Path()
    private val rect = RectF()

    var outsideColor: Int = 0xFF15171A.toInt()
        set(value) { field = value; invalidate() }
    var radiusDp = 28f
        set(value) { field = value; invalidate() }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val r = radiusDp * density
        val sw = 2 * density
        stroke.strokeWidth = sw
        fill.color = outsideColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(rect, Path.Direction.CW)
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.drawPath(path, fill)
        rect.inset(sw / 2, sw / 2)
        canvas.drawRoundRect(rect, r - sw / 2, r - sw / 2, stroke)
    }
}
