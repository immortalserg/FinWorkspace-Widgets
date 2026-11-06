package lu.finworkspace.widgets

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import kotlin.concurrent.thread

/** Connection management only. Widget types and accounts are configured per instance. */
class MainActivity : Activity() {
    private lateinit var settings: SecureSettings
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SecureSettings(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
        }
        fun field(hintText: String, password: Boolean = false): EditText {
            val input = EditText(this).apply {
                hint = hintText
                if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            layout.addView(input)
            return input
        }
        layout.addView(TextView(this).apply { text = "FinWorkspace Widgets"; textSize = 25f })
        layout.addView(TextView(this).apply {
            text = "Подключите сервер. Затем добавьте на рабочий стол виджеты «Счёт», «Все счета» или «Транзакции»."
            textSize = 15f
            setPadding(0, 16, 0, 16)
        })
        val server = field("https://server.example.com").apply { setText(settings.server) }
        val email = field("Email")
        val password = field("Пароль", true)
        status = TextView(this).apply {
            text = if (settings.accessToken.isNotBlank()) "Сервер подключён" else "Сервер не подключён"
        }
        val login = Button(this).apply { text = "Подключиться" }
        val refresh = Button(this).apply { text = "Обновить все виджеты" }
        val logout = Button(this).apply { text = "Отключить аккаунт" }
        layout.addView(login)
        layout.addView(refresh)
        layout.addView(logout)
        layout.addView(status)
        setContentView(ScrollView(this).apply { addView(layout) })

        login.setOnClickListener {
            val base = server.text.toString().trim().trimEnd('/')
            if (!base.startsWith("https://")) {
                status.text = "Для безопасности необходим HTTPS"
                return@setOnClickListener
            }
            if (email.text.isBlank() || password.text.isBlank()) {
                status.text = "Введите email и пароль"
                return@setOnClickListener
            }
            login.isEnabled = false
            status.text = "Подключение…"
            thread {
                val result = runCatching {
                    // Never keep tokens from a different server.
                    if (settings.server != base) settings.clear()
                    settings.server = base
                    FinanceApi(settings).login(email.text.toString(), password.text.toString())
                }
                runOnUiThread {
                    login.isEnabled = true
                    password.text.clear()
                    status.text = if (result.isSuccess) "Подключено" else
                        "Ошибка авторизации: ${result.exceptionOrNull()?.message}"
                    if (result.isSuccess) {
                        FinanceWidgetProvider.schedule(this)
                        FinanceWidgetProvider.refresh(this)
                    }
                }
            }
        }
        refresh.setOnClickListener {
            FinanceWidgetProvider.refresh(this)
            status.text = "Запрос на обновление отправлен"
        }
        logout.setOnClickListener {
            settings.clear()
            FinanceWidgetProvider.refresh(this)
            status.text = "Аккаунт отключён"
        }
    }
}
