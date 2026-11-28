package app.pocketta.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import app.pocketta.core.Money
import app.pocketta.i18n.Tr
import java.io.File

/** A picture of one month's numbers, ready to send to family or friends. Nothing leaves the phone until you pick an app to share with. */
object SummaryImage {
    class Slice(val name: String, val value: Double)

    class Data(
        val month: String,
        val currency: String,
        val spent: Double,
        val income: Double,
        val previousSpent: Double,
        val categories: List<Slice>,
    )

    private const val W = 1080
    private const val H = 1500
    private val palette = intArrayOf(
        0xFF8DD5B3.toInt(), 0xFFFFC857.toInt(), 0xFF7CB7FF.toInt(), 0xFFFF9E7A.toInt(), 0xFFD6A2E8.toInt(),
    )

    fun render(d: Data, lang: String): Bitmap {
        val ar = lang == Tr.AR
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFF17493A.toInt(), 0xFF0F332A.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        val margin = 90f
        val x = if (ar) W - margin else margin
        val align = if (ar) Paint.Align.RIGHT else Paint.Align.LEFT
        fun p(size: Float, bold: Boolean = false, color: Int = Color.WHITE, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.alpha = alpha
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
        }
        fun t(s: String) = Tr.t(s, lang)

        c.drawText("Pocketta", x, 130f, p(40f, true, alpha = 200))
        c.drawText(t(d.month), x, 250f, p(84f, true))

        c.drawText(t("Spent"), x, 380f, p(40f, alpha = 200))
        c.drawText(Money.short(d.spent) + " " + d.currency, x, 490f, p(120f, true))

        if (d.previousSpent > 0) {
            val pct = ((d.spent - d.previousSpent) / d.previousSpent * 100).toInt()
            val (txt, col) = when {
                pct > 0 -> t("▲ $pct% vs last month") to 0xFFFFB4A9.toInt()
                pct < 0 -> t("▼ ${-pct}% vs last month") to 0xFFA9F0CF.toInt()
                else -> t("＝ vs last month") to Color.WHITE
            }
            c.drawText(txt, x, 570f, p(44f, true, col))
        }

        // Income and what is left
        val colW = (W - 2 * margin) / 2
        val leftX = if (ar) W - margin else margin
        val rightX = if (ar) W - margin - colW else margin + colW
        c.drawText(t("Income"), leftX, 700f, p(36f, alpha = 200))
        c.drawText(Money.short(d.income), leftX, 770f, p(64f, true))
        c.drawText(t("Left"), rightX, 700f, p(36f, alpha = 200))
        c.drawText(Money.short(d.income - d.spent), rightX, 770f, p(64f, true, if (d.income - d.spent < 0) 0xFFFFB4A9.toInt() else Color.WHITE))

        // Top categories
        c.drawText(t("Where it went"), x, 900f, p(36f, alpha = 200))
        val top = d.categories.take(5)
        val total = d.spent.coerceAtLeast(1.0)
        var y = 950f
        val barLeft = margin
        val barRight = W - margin
        top.forEachIndexed { i, s ->
            val col = palette[i % palette.size]
            c.drawText(t(s.name), x, y + 36f, p(38f, true))
            val amount = Money.short(s.value)
            val amountPaint = p(38f, false, alpha = 220).apply { textAlign = if (ar) Paint.Align.LEFT else Paint.Align.RIGHT }
            c.drawText(amount, if (ar) barLeft else barRight, y + 36f, amountPaint)
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 40 }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
            val by = y + 54f
            c.drawRoundRect(RectF(barLeft, by, barRight, by + 18f), 9f, 9f, track)
            val frac = (s.value / total).toFloat().coerceIn(0.02f, 1f)
            val w = (barRight - barLeft) * frac
            if (ar) c.drawRoundRect(RectF(barRight - w, by, barRight, by + 18f), 9f, 9f, fill)
            else c.drawRoundRect(RectF(barLeft, by, barLeft + w, by + 18f), 9f, 9f, fill)
            y += 96f
        }
        return bmp
    }

    /** Draws the picture, saves it in the app's cache and opens Android's share sheet. */
    fun share(context: Context, d: Data, lang: String) {
        val bmp = render(d, lang)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "pocketta-month.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, Tr.t("Share my month", lang)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
