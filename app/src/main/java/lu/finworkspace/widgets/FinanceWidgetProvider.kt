package lu.finworkspace.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Currency
import java.util.concurrent.TimeUnit

/** Each provider is a separate widget type in the Android widget picker. */
open class FinanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        schedule(context)
        refresh(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences("widget_instances", Context.MODE_PRIVATE).edit()
        ids.forEach { id ->
            prefs.remove("account_$id")
            listOf("background", "opacity", "titleColor", "amountColor", "icon", "showName", "showHeader", "font", "fontSize", "labelSize", "headerSize", "rowCount").forEach { prefs.remove("style_${id}_$it") }
        }
        prefs.apply()
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }
    companion object {
        const val ACTION_REFRESH = "lu.finworkspace.widgets.REFRESH"
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "finance-widgets",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetWorker>(30, TimeUnit.MINUTES).build()
            )
        }
        fun refresh(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WidgetWorker>().build())
        }
        fun render(context: Context, provider: Class<out AppWidgetProvider>, id: Int, title: String, body: String, rows: List<Pair<String, String>> = emptyList()) {
            val manager = AppWidgetManager.getInstance(context)
            val style = WidgetStyle.load(context, id)
            val configure = Intent(context, WidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val settingsIntent = PendingIntent.getActivity(
                context, id, configure, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_finance)
            style.apply(views)
            views.setTextViewText(R.id.widget_icon, style.styled(style.icon))
            views.setViewVisibility(R.id.widget_icon, if (style.icon.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            val heading = style.showHeader || style.icon.isNotEmpty()
            views.setViewVisibility(R.id.widget_heading, if (heading) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewText(R.id.widget_title, style.styled(if (style.showHeader) title else ""))
            views.setViewVisibility(R.id.widget_title, if (style.showHeader) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewText(R.id.widget_body, style.styled(body))
            views.setViewVisibility(R.id.widget_body, if (body.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
            val ids = intArrayOf(R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4, R.id.row_5)
            val labels = intArrayOf(R.id.row_1_label, R.id.row_2_label, R.id.row_3_label, R.id.row_4_label, R.id.row_5_label)
            val amounts = intArrayOf(R.id.row_1_amount, R.id.row_2_amount, R.id.row_3_amount, R.id.row_4_amount, R.id.row_5_amount)
            ids.forEachIndexed { i, row ->
                val data = if (i < style.rowCount) rows.getOrNull(i) else null
                views.setViewVisibility(row, if (data == null) android.view.View.GONE else android.view.View.VISIBLE)
                if (data != null) {
                    val labelText = if (style.showName) data.first else data.first.takeWhile { it != ' ' }
                    val styledLabel = SpannableString(style.styled(labelText))
                    if (provider == TransactionsWidget::class.java && styledLabel.isNotEmpty()) {
                        val iconColor = when (styledLabel.first()) {
                            '↘' -> Color.rgb(255, 124, 124)
                            '↗' -> Color.rgb(98, 220, 157)
                            else -> Color.rgb(127, 191, 255)
                        }
                        styledLabel.setSpan(ForegroundColorSpan(iconColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    views.setTextViewText(labels[i], styledLabel)
                    views.setTextViewText(amounts[i], style.styled(data.second))
                    views.setTextViewTextSize(amounts[i], android.util.TypedValue.COMPLEX_UNIT_SP, style.fontSize.toFloat())
                }
            }
            views.setOnClickPendingIntent(R.id.widget_background, settingsIntent)
            views.setOnClickPendingIntent(R.id.widget_heading, settingsIntent)
            manager.updateAppWidget(id, views)
        }
        fun allWidgets(context: Context): List<Pair<Class<out AppWidgetProvider>, Int>> {
            val manager = AppWidgetManager.getInstance(context)
            return listOf(
                SingleAccountWidget::class.java,
                AllAccountsWidget::class.java,
                TransactionsWidget::class.java
            ).flatMap { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider)).map { provider to it }
            }
        }
    }
}

class SingleAccountWidget : FinanceWidgetProvider()
class AllAccountsWidget : FinanceWidgetProvider()
class TransactionsWidget : FinanceWidgetProvider()

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val widgets = FinanceWidgetProvider.allWidgets(context)
        if (widgets.isEmpty()) return Result.success()
        val settings = SecureSettings(context)
        if (settings.server.isBlank() || settings.accessToken.isBlank()) {
            widgets.forEach { (provider, id) ->
                FinanceWidgetProvider.render(context, provider, id, "FinWorkspace", "Откройте приложение и подключите сервер")
            }
            return Result.success()
        }
        return try {
            val api = FinanceApi(settings)
            val accounts = api.accounts()
            val transactions = if (widgets.any { it.first == TransactionsWidget::class.java }) api.transactions() else JSONArray()
            val prefs = context.getSharedPreferences("widget_instances", Context.MODE_PRIVATE)
            widgets.forEach { (provider, id) ->
                val (title, body, rows) = when (provider) {
                    SingleAccountWidget::class.java -> {
                        val accountId = prefs.getInt("account_$id", -1)
                        val row = singleAccountRow(accounts, accountId)
                        if (row == null) Triple("Счёт", "Выберите счёт в настройках", emptyList<Pair<String, String>>())
                        else Triple("Счёт", "", listOf(row))
                    }
                    TransactionsWidget::class.java -> Triple("Последние транзакции", "", transactionRows(transactions, accounts))
                    else -> Triple("Все счета", "", accountRows(accounts))
                }
                FinanceWidgetProvider.render(context, provider, id, title, body, rows)
            }
            Result.success()
        } catch (e: Exception) {
            widgets.forEach { (provider, id) ->
                FinanceWidgetProvider.render(context, provider, id, "FinWorkspace", "Не удалось обновить данные")
            }
            Result.retry()
        }
    }
    private fun money(value: Double, code: String): String = runCatching {
        NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(code) }.format(value)
    }.getOrElse { "$value $code" }
    private fun singleAccountRow(accounts: JSONArray, id: Int): Pair<String, String>? {
        for (i in 0 until accounts.length()) {
            val account = accounts.getJSONObject(i)
            if (account.optInt("id") == id) {
                return account.optString("name") to money(
                    account.optDouble("balance"), account.optString("currency", "EUR")
                )
            }
        }
        return null
    }
    private fun accountRows(accounts: JSONArray): List<Pair<String, String>> {
        val totals = linkedMapOf<String, Double>()
        for (i in 0 until accounts.length()) {
            val a = accounts.getJSONObject(i)
            if (!a.optBoolean("is_active", true)) continue
            val code = a.optString("currency", "EUR")
            totals[code] = (totals[code] ?: 0.0) + a.optDouble("balance")
        }
        return totals.entries.take(5).map { (code, value) -> code to money(value, code) }
    }
    private fun transactionRows(items: JSONArray, accounts: JSONArray): List<Pair<String, String>> {
        val currencies = mutableMapOf<Int, String>()
        for (i in 0 until accounts.length()) {
            val a = accounts.getJSONObject(i)
            currencies[a.optInt("id")] = a.optString("currency", "EUR")
        }
        return (0 until minOf(items.length(), 5)).map { i ->
            val item = items.getJSONObject(i)
            val type = item.optString("type")
            val sign = when (type) { "expense" -> "−"; "income" -> "+"; else -> "⇄" }
            val icon = when (type) { "expense" -> "↘"; "income" -> "↗"; else -> "⇄" }
            val code = currencies[item.optInt("account_id")] ?: ""
            val amount = if (code.isNotBlank()) money(item.optDouble("amount"), code) else item.optString("amount")
            "$icon  ${item.optString("description").ifBlank { "Операция" }}" to "$sign$amount"
        }
    }
}
