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

/** Each provider is a separate widget type in the Android widget picker. */
open class FinanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        schedule(context)
        refresh(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences("widget_instances", Context.MODE_PRIVATE).edit()
        ids.forEach { prefs.remove("account_$it") }
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
        fun render(context: Context, provider: Class<out AppWidgetProvider>, id: Int, title: String, body: String, footer: String) {
            val manager = AppWidgetManager.getInstance(context)
            val intent = Intent(context, provider).setAction(ACTION_REFRESH)
            val pending = PendingIntent.getBroadcast(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_finance)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_body, body)
            views.setTextViewText(R.id.widget_footer, footer)
            views.setOnClickPendingIntent(R.id.widget_footer, pending)
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
                FinanceWidgetProvider.render(context, provider, id, "FinWorkspace", "Откройте приложение и подключите сервер", "Нажмите для обновления")
            }
            return Result.success()
        }
        return try {
            val api = FinanceApi(settings)
            val accounts = api.accounts()
            val transactions = if (widgets.any { it.first == TransactionsWidget::class.java }) api.transactions() else JSONArray()
            val prefs = context.getSharedPreferences("widget_instances", Context.MODE_PRIVATE)
            widgets.forEach { (provider, id) ->
                val (title, body) = when (provider) {
                    SingleAccountWidget::class.java -> {
                        val accountId = prefs.getInt("account_$id", -1)
                        "Счёт" to formatSingle(accounts, accountId)
                    }
                    TransactionsWidget::class.java -> "Последние транзакции" to formatTransactions(transactions, accounts)
                    else -> "Все счета" to formatAll(accounts)
                }
                FinanceWidgetProvider.render(context, provider, id, title, body, "Нажмите для обновления")
            }
            Result.success()
        } catch (e: Exception) {
            widgets.forEach { (provider, id) ->
                FinanceWidgetProvider.render(context, provider, id, "FinWorkspace", "Не удалось обновить данные", "Нажмите для повтора")
            }
            Result.retry()
        }
    }
    private fun money(value: Double, code: String): String = runCatching {
        NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(code) }.format(value)
    }.getOrElse { "$value $code" }
    private fun formatSingle(accounts: JSONArray, id: Int): String {
        for (i in 0 until accounts.length()) {
            val account = accounts.getJSONObject(i)
            if (account.optInt("id") == id) {
                return "${account.optString("name")}\n${money(account.optDouble("balance"), account.optString("currency", "EUR"))}"
            }
        }
        return "Выберите счёт при добавлении виджета"
    }
    private fun formatAll(accounts: JSONArray): String {
        val totals = linkedMapOf<String, Double>()
        for (i in 0 until accounts.length()) {
            val account = accounts.getJSONObject(i)
            if (!account.optBoolean("is_active", true)) continue
            val code = account.optString("currency", "EUR")
            totals[code] = (totals[code] ?: 0.0) + account.optDouble("balance")
        }
        return if (totals.isEmpty()) "Нет счетов" else totals.entries.joinToString("\n") { (code, total) -> money(total, code) }
    }
    private fun formatTransactions(items: JSONArray, accounts: JSONArray): String {
        if (items.length() == 0) return "Нет транзакций"
        val currencies = mutableMapOf<Int, String>()
        for (i in 0 until accounts.length()) {
            val a = accounts.getJSONObject(i)
            currencies[a.optInt("id")] = a.optString("currency", "EUR")
        }
        return (0 until minOf(items.length(), 5)).joinToString("\n") { index ->
            val item = items.getJSONObject(index)
            val sign = when (item.optString("type")) { "expense" -> "−"; "income" -> "+"; else -> "⇄" }
            val currency = currencies[item.optInt("account_id")] ?: ""
            val amount = if (currency.isNotBlank()) money(item.optDouble("amount"), currency)
                else item.optString("amount")
            "${item.optString("description").take(20)}  $sign$amount"
        }
    }
}
