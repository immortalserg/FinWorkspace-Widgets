package lu.finworkspace.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan

/** Appearance is saved independently for each widget instance. */
data class WidgetStyle(
    val background: Int = Color.rgb(18, 45, 76),
    val opacity: Int = 94,
    val titleColor: Int = Color.rgb(205, 225, 248),
    val amountColor: Int = Color.WHITE,
    val icon: String = "◆",
    val showName: Boolean = true,
    val showHeader: Boolean = true,
    val font: String = "sans",
    val fontSize: Int = 18,
    val labelSize: Int = 14,
    val headerSize: Int = 16
) {
    companion object {
        fun load(context: Context, id: Int): WidgetStyle {
            val p = context.getSharedPreferences("widget_instances", Context.MODE_PRIVATE)
            val k = "style_${id}_"
            return WidgetStyle(
                p.getInt(k + "background", Color.rgb(18, 45, 76)),
                p.getInt(k + "opacity", 94).coerceIn(0, 100),
                p.getInt(k + "titleColor", Color.rgb(205, 225, 248)),
                p.getInt(k + "amountColor", Color.WHITE),
                p.getString(k + "icon", "◆") ?: "◆",
                p.getBoolean(k + "showName", true),
                p.getBoolean(k + "showHeader", true),
                p.getString(k + "font", "sans") ?: "sans",
                p.getInt(k + "fontSize", 18).coerceIn(12, 30),
                p.getInt(k + "labelSize", 14).coerceIn(10, 24),
                p.getInt(k + "headerSize", 16).coerceIn(10, 26)
            )
        }
    }
    fun save(context: Context, id: Int) {
        val k = "style_${id}_"
        context.getSharedPreferences("widget_instances", Context.MODE_PRIVATE).edit()
            .putInt(k + "background", background)
            .putInt(k + "opacity", opacity)
            .putInt(k + "titleColor", titleColor)
            .putInt(k + "amountColor", amountColor)
            .putString(k + "icon", icon)
            .putBoolean(k + "showName", showName)
            .putBoolean(k + "showHeader", showHeader)
            .putString(k + "font", font)
            .putInt(k + "fontSize", fontSize)
            .putInt(k + "labelSize", labelSize)
            .putInt(k + "headerSize", headerSize).apply()
    }
    fun styled(value: String): CharSequence {
        val family = when (font) {
            "serif" -> "serif"
            "mono" -> "monospace"
            else -> "sans-serif"
        }
        return SpannableString(value).apply {
            if (isNotEmpty()) setSpan(TypefaceSpan(family), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    fun apply(views: RemoteViews) {
        val bitmap = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (background and 0x00FFFFFF) or ((opacity * 255 / 100) shl 24)
        }
        canvas.drawRoundRect(0f, 0f, 480f, 320f, 28f, 28f, paint)
        views.setImageViewBitmap(R.id.widget_background, bitmap)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, headerSize.toFloat())
        views.setTextColor(R.id.widget_icon, titleColor)
        views.setTextColor(R.id.widget_body, amountColor)
        views.setTextViewTextSize(R.id.widget_body, android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        val labels = intArrayOf(R.id.row_1_label, R.id.row_2_label, R.id.row_3_label, R.id.row_4_label, R.id.row_5_label)
        val amounts = intArrayOf(R.id.row_1_amount, R.id.row_2_amount, R.id.row_3_amount, R.id.row_4_amount, R.id.row_5_amount)
        labels.forEach {
            views.setTextColor(it, titleColor)
            views.setTextViewTextSize(it, android.util.TypedValue.COMPLEX_UNIT_SP, labelSize.toFloat())
        }
        amounts.forEach { views.setTextColor(it, amountColor) }
        // Font family requires predeclared text appearances; avoid invoking
        // setTextAppearance via RemoteViews because it is not supported on all launchers.
    }
}
