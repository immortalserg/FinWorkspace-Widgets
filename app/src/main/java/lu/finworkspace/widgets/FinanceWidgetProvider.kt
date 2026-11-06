package lu.finworkspace.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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

class FinanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        schedule(context)
        refresh(context)
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }
    companion object {
        const val ACTION_REFRESH = "lu.finworkspace.widgets.REFRESH"
        fun schedule(context: Context) {
            val task = PeriodicWorkRequestBuilder<WidgetWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("finance-widgets", ExistingPeriodicWorkPolicy.KEEP, task)
        }
        fun refresh(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WidgetWorker>().build())
        }
        fun render(context: Context, title: String, body: String, footer: String) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FinanceWidgetProvider::class.java)
            val intent = Intent(context, FinanceWidgetProvider::class.java).setAction(ACTION_REFRESH)
            val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.getAppWidgetIds(component).forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_finance)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_body, body)
                views.setTextViewText(R.id.widget_footer, footer)
                views.setOnClickPendingIntent(R.id.widget_footer, pending)
                manager.updateAppWidget(id, views)
            }
        }
    }
}

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = SecureSettings(applicationContext)
        if (settings.server.isBlank() || settings.accessToken.isBlank()) {
            FinanceWidgetProvider.render(applicationContext, "FinWorkspace", "Настройте подключение в приложении", "Нажмите для обновления")
            return Result.success()
        }
        return try {
            val api = FinanceApi(settings)
            val accounts = api.accounts()
            val mode = settings.mode
            val body = when (mode) {
                "transactions" -> formatTransactions(api.transactions())
                "single" -> formatSingle(accounts, settings.accountId)
                else -> formatAll(accounts)
            }
            val title = when (mode) { "transactions" -> "Последние транзакции"; "single" -> "Счёт"; else -> "Все счета" }
            FinanceWidgetProvider.render(applicationContext, title, body, "Обновлено · нажмите для обновления")
            Result.success()
        } catch (e: Exception) {
            FinanceWidgetProvider.render(applicationContext, "FinWorkspace", "Нет соединения или сессия истекла", "Нажмите для повтора")
            Result.retry()
        }
    }
    private fun money(value: Double, code: String): String = runCatching {
        NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(code) }.format(value)
    }.getOrElse { "$value $code" }
    private fun formatSingle(accounts: JSONArray, id: Int): String {
        for (i in 0 until accounts.length()) {
            val account = accounts.getJSONObject(i)
            if (account.optInt("id") == id) return "${account.optString("name")}\n${money(account.optDouble("balance"), account.optString("currency", "EUR"))}"
        }
        return "Выберите счёт в настройках"
    }
    private fun formatAll(accounts: JSONArray): String {
        val totals = linkedMapOf<String, Double>()
        for (i in 0 until accounts.length()) {
            val a = accounts.getJSONObject(i)
            if (!a.optBoolean("is_active", true)) continue
            val currency = a.optString("currency", "EUR")
            totals[currency] = (totals[currency] ?: 0.0) + a.optDouble("balance")
        }
        return if (totals.isEmpty()) "Нет счетов" else totals.entries.joinToString("\n") { (code, amount) -> money(amount, code) }
    }
    private fun formatTransactions(items: JSONArray): String {
        if (items.length() == 0) return "Нет транзакций"
        return (0 until minOf(items.length(), 5)).joinToString("\n") { index ->
            val item = items.getJSONObject(index)
            val symbol = if (item.optString("type") == "expense") "−" else if (item.optString("type") == "income") "+" else "⇄"
            "${item.optString("description").take(22)}  $symbol${item.optDouble("amount")}"
        }
    }
}
