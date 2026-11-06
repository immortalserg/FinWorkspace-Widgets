package lu.finworkspace.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import kotlin.concurrent.thread

/** Android opens this activity once for every newly added single-account widget. */
class AccountWidgetConfigureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val heading = TextView(this).apply { text = "Выберите счёт для виджета"; textSize = 22f }
        layout.addView(heading)
        val spinner = Spinner(this)
        layout.addView(spinner)
        val status = TextView(this)
        layout.addView(status)
        val save = Button(this).apply { text = "Добавить виджет"; isEnabled = false }
        layout.addView(save)
        val openApp = Button(this).apply { text = "Настройки подключения" }
        layout.addView(openApp)
        setContentView(ScrollView(this).apply { addView(layout) })
        openApp.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        val ids = mutableListOf<Int>()
        fun load() {
            status.text = "Загрузка счетов…"
            thread {
                val result = runCatching { FinanceApi(SecureSettings(this)).accounts() }
                runOnUiThread {
                    result.onSuccess { data ->
                        val names = mutableListOf<String>()
                        ids.clear()
                        for (i in 0 until data.length()) {
                            val account = data.getJSONObject(i)
                            if (!account.optBoolean("is_active", true)) continue
                            names.add("${account.optString("name")} (${account.optString("currency")})")
                            ids.add(account.getInt("id"))
                        }
                        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                        save.isEnabled = ids.isNotEmpty()
                        status.text = if (ids.isEmpty()) "Активные счета не найдены" else ""
                    }.onFailure { status.text = "Подключите сервер в настройках приложения: ${it.message}" }
                }
            }
        }
        save.setOnClickListener {
            val selected = ids.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
            getSharedPreferences("widget_instances", MODE_PRIVATE).edit().putInt("account_$widgetId", selected).apply()
            FinanceWidgetProvider.schedule(this)
            FinanceWidgetProvider.refresh(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
        load()
    }
}
