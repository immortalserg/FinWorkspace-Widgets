package lu.finworkspace.widgets

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import android.view.ViewGroup
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var settings: SecureSettings
    private lateinit var status: TextView
    private lateinit var accounts: Spinner
    private var accountIds = listOf(-1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SecureSettings(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
        }
        val root = ScrollView(this).apply { addView(layout) }
        fun field(hint: String, password: Boolean = false): EditText {
            val input = EditText(this).apply {
                this.hint = hint
                if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            layout.addView(input)
            return input
        }
        val heading = TextView(this).apply { text = "FinWorkspace Widgets"; textSize = 25f }
        layout.addView(heading)
        val server = field("https://server.example.com").apply { setText(settings.server) }
        val email = field("Email")
        val password = field("Пароль", true)
        status = TextView(this)
        val login = Button(this).apply { text = "Подключиться" }
        layout.addView(login)
        val modes = arrayOf("Все счета", "Один счёт", "Транзакции")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spinner.setSelection(listOf("all", "single", "transactions").indexOf(settings.mode).coerceAtLeast(0))
        layout.addView(spinner)
        accounts = Spinner(this)
        layout.addView(accounts)
        val load = Button(this).apply { text = "Загрузить счета" }
        layout.addView(load)
        val save = Button(this).apply { text = "Сохранить настройки виджета" }
        layout.addView(save)
        layout.addView(status)
        setContentView(root)
        login.setOnClickListener {
            val base = server.text.toString().trim().trimEnd('/')
            if (!base.startsWith("https://")) { status.text = "Для безопасности необходим HTTPS"; return@setOnClickListener }
            settings.server = base
            status.text = "Подключение…"
            thread {
                val result = runCatching { FinanceApi(settings).login(email.text.toString(), password.text.toString()) }
                runOnUiThread {
                    password.text.clear()
                    status.text = if (result.isSuccess) "Подключено" else "Ошибка авторизации: ${result.exceptionOrNull()?.message}"
                    if (result.isSuccess) FinanceWidgetProvider.refresh(this)
                }
            }
        }
        load.setOnClickListener {
            thread {
                val result = runCatching { FinanceApi(settings).accounts() }
                runOnUiThread {
                    result.onSuccess { data ->
                        val names = mutableListOf("Выберите счёт")
                        val ids = mutableListOf(-1)
                        for (i in 0 until data.length()) {
                            val a = data.getJSONObject(i)
                            names.add(a.optString("name")); ids.add(a.optInt("id"))
                        }
                        accountIds = ids
                        accounts.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                        accounts.setSelection(ids.indexOf(settings.accountId).coerceAtLeast(0))
                    }.onFailure { status.text = "Не удалось загрузить счета: ${it.message}" }
                }
            }
        }
        save.setOnClickListener {
            val mode = listOf("all", "single", "transactions")[spinner.selectedItemPosition]
            val selected = accountIds.getOrElse(accounts.selectedItemPosition) { -1 }
            if (mode == "single" && selected == -1) { status.text = "Сначала выберите счёт"; return@setOnClickListener }
            settings.mode = mode
            settings.accountId = selected
            FinanceWidgetProvider.schedule(this)
            FinanceWidgetProvider.refresh(this)
            status.text = "Сохранено. Добавьте виджет FinWorkspace на рабочий стол."
        }
    }
}
