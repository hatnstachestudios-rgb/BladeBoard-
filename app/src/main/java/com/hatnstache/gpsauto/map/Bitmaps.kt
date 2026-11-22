package com.hatnstache.gpsauto.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat

const val ROUTE_PINK = 0xFFFF4FA8.toInt()
/** Custom (dropped-pin) waypoints, like the game's cyan player waypoints. */
const val WAYPOINT_CYAN = 0xFF3CC8F0.toInt()

object Bitmaps {
    /** The player marker: dark disc, white ring, white arrowhead pointing up (north = travel). */
    fun playerArrow(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x55000000
        c.drawCircle(r, r + size * 0.03f, r * 0.9f, paint)
        paint.color = Color.WHITE
        c.drawCircle(r, r, r * 0.88f, paint)
        paint.color = 0xFF1B1C1F.toInt()
        c.drawCircle(r, r, r * 0.74f, paint)
        val path = Path().apply {
            moveTo(r, r - r * 0.48f)
            lineTo(r + r * 0.36f, r + r * 0.38f)
            lineTo(r, r + r * 0.2f)
            lineTo(r - r * 0.36f, r + r * 0.38f)
            close()
        }
        paint.color = Color.WHITE
        c.drawPath(path, paint)
        return bmp
    }

    /** A saved-place blip as a bitmap, for Android Auto list rows. */
    fun blip(context: Context, iconRes: Int, color: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        BlipPainter(context).draw(Canvas(bmp), size / 2f, size / 2f, size / 2f, iconRes, color)
        return bmp
    }
}

/** Draws GTA-style round blips: colored disc, white ring, white glyph. */
class BlipPainter(private val context: Context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4D000000 }
    private val icons = HashMap<Int, android.graphics.drawable.Drawable>()

    fun draw(c: Canvas, x: Float, y: Float, radius: Float, iconRes: Int?, color: Int) {
        c.drawCircle(x, y + radius * 0.12f, radius * 0.96f, shadow)
        fill.color = color
        val ringW = radius * 0.16f
        c.drawCircle(x, y, radius - ringW / 2, fill)
        ring.strokeWidth = ringW
        c.drawCircle(x, y, radius - ringW / 2, ring)
        if (iconRes != null) {
            val d = icons.getOrPut(iconRes) { ContextCompat.getDrawable(context, iconRes)!!.mutate() }
            val half = (radius * 0.55f).toInt()
            d.setBounds((x - half).toInt(), (y - half).toInt(), (x + half).toInt(), (y + half).toInt())
            d.draw(c)
        }
    }
}
