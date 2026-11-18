package app.pocketta.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.unit.dp
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size

/** Draws progress rings for the widgets (Glance has no arc shape, so they are small bitmaps). */
object RingImage {
    fun ring(fraction: Float, color: Int, px: Int = 240): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = px * 0.13f
        val rect = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        p.color = 0x33888888
        c.drawArc(rect, 0f, 360f, false, p)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0.005f) {
            p.color = color
            c.drawArc(rect, -90f, 360f * f, false, p)
        }
        return bmp
    }
}

@androidx.compose.runtime.Composable
fun RingBox(fraction: Float, color: Int, size: androidx.compose.ui.unit.Dp, content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.glance.layout.Box(
        modifier = androidx.glance.GlanceModifier.size(size),
        contentAlignment = androidx.glance.layout.Alignment.Center,
    ) {
        androidx.glance.Image(
            provider = androidx.glance.ImageProvider(RingImage.ring(fraction, color)),
            contentDescription = null,
            modifier = androidx.glance.GlanceModifier.fillMaxSize(),
        )
        content()
    }
}

/** A ring size that fits the widget with room for [reserve] dp of text under it. */
fun ringSizeFor(size: androidx.compose.ui.unit.DpSize, reserve: Int): androidx.compose.ui.unit.Dp =
    minOf(size.width.value - 28f, size.height.value - 28f - reserve).coerceIn(44f, 130f).dp
