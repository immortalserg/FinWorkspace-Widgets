package lu.finworkspace.widgets

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews

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
    val fontSize: Int = 18
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
                p.getInt(k + "fontSize", 18).coerceIn(12, 30)
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
            .putInt(k + "fontSize", fontSize).apply()
    }
    fun apply(views: RemoteViews) {
        views.setInt(R.id.widget_background, "setColorFilter", background)
        views.setInt(R.id.widget_background, "setImageAlpha", opacity * 255 / 100)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextColor(R.id.widget_icon, titleColor)
        views.setTextColor(R.id.widget_body, amountColor)
        views.setTextViewTextSize(R.id.widget_body, android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        val labels = intArrayOf(R.id.row_1_label, R.id.row_2_label, R.id.row_3_label, R.id.row_4_label, R.id.row_5_label)
        val amounts = intArrayOf(R.id.row_1_amount, R.id.row_2_amount, R.id.row_3_amount, R.id.row_4_amount, R.id.row_5_amount)
        labels.forEach { views.setTextColor(it, titleColor) }
        amounts.forEach { views.setTextColor(it, amountColor) }
        // Font family requires predeclared text appearances; avoid invoking
        // setTextAppearance via RemoteViews because it is not supported on all launchers.
    }
}
