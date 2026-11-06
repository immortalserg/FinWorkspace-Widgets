package lu.finworkspace.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.*
import kotlin.concurrent.thread

/** Shared configuration for all widget types, including editing an existing instance. */
class WidgetConfigureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED)
        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(id)?.provider?.className.orEmpty()
        val single = provider.endsWith("SingleAccountWidget")
        val style = WidgetStyle.load(this, id)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 24, 28, 24) }
        fun heading(label: String) { root.addView(TextView(this).apply { text = label; textSize = 17f; setPadding(0, 18, 0, 5) }) }
        fun entry(value: String, hint: String): EditText = EditText(this).apply {
            setText(value); this.hint = hint; setSingleLine(true); root.addView(this)
        }
        root.addView(TextView(this).apply { text = "Настройка виджета"; textSize = 24f })
        val ids = mutableListOf<Int>()
        val account = Spinner(this)
        if (single) {
            heading("Счёт")
            root.addView(account)
            val info = TextView(this)
            root.addView(info)
            thread {
                val result = runCatching { FinanceApi(SecureSettings(this)).accounts() }
                runOnUiThread {
                    result.onSuccess { data ->
                        val names = mutableListOf<String>()
                        for (i in 0 until data.length()) {
                            val a = data.getJSONObject(i)
                            if (a.optBoolean("is_active", true)) {
                                ids.add(a.getInt("id"))
                                names.add("${a.optString("name")} (${a.optString("currency")})")
                            }
                        }
                        account.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                        val selected = getSharedPreferences("widget_instances", MODE_PRIVATE).getInt("account_$id", -1)
                        account.setSelection(ids.indexOf(selected).coerceAtLeast(0))
                        if (ids.isEmpty()) info.text = "Нет счетов. Сначала подключите сервер."
                    }.onFailure { info.text = "Не удалось загрузить счета: ${it.message}" }
                }
            }
        }
        heading("Цвет фона (HEX)")
        val background = entry(String.format("#%06X", 0xFFFFFF and style.background), "#122D4C")
        val palette = listOf("#122D4C", "#17213A", "#243B55", "#136F63", "#5C3B82", "#333333", "#FFFFFF", "#000000")
        val colors = Spinner(this).apply { adapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, palette) }
        root.addView(colors)
        // Preserve a custom HEX value when the palette is first initialized.
        colors.setSelection(0)
        colors.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, rowId: Long) {
                if (position != 0) background.setText(palette[position])
            }
        }
        heading("Прозрачность фона (0% — прозрачный, 100% — непрозрачный)")
        val opacity = SeekBar(this).apply { max = 100; progress = style.opacity }
        root.addView(opacity)
        heading("Цвет названий (HEX)")
        val titleColor = entry(String.format("#%06X", 0xFFFFFF and style.titleColor), "#CDE1F8")
        heading("Цвет сумм (HEX)")
        val amountColor = entry(String.format("#%06X", 0xFFFFFF and style.amountColor), "#FFFFFF")
        heading("Иконка")
        val icons = listOf("◆", "🏦", "💳", "💰", "💵", "📊", "📈", "↔", "🛒", "🚗", "🏠", "★", "●", "Без иконки")
        val icon = Spinner(this).apply { adapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, icons); setSelection(icons.indexOf(style.icon).coerceAtLeast(0)) }
        root.addView(icon)
        val showName = CheckBox(this).apply { text = "Отображать название"; isChecked = style.showName }
        val showHeader = CheckBox(this).apply { text = "Отображать заголовок"; isChecked = style.showHeader }
        root.addView(showName); root.addView(showHeader)
        heading("Шрифт")
        val fonts = listOf("Обычный", "С засечками", "Моноширинный")
        val font = Spinner(this).apply { adapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, fonts); setSelection(listOf("sans", "serif", "mono").indexOf(style.font).coerceAtLeast(0)) }
        root.addView(font)
        heading("Размер шрифта суммы (12–30 sp)")
        val fontSize = SeekBar(this).apply { max = 18; progress = style.fontSize - 12 }
        root.addView(fontSize)
        heading("Размер шрифта названий (10–24 sp)")
        val labelSize = SeekBar(this).apply { max = 14; progress = style.labelSize - 10 }
        root.addView(labelSize)
        heading("Размер шрифта заголовка (10–26 sp)")
        val headerSize = SeekBar(this).apply { max = 16; progress = style.headerSize - 10 }
        root.addView(headerSize)
        heading("Количество строк (1–10)")
        val rowCount = SeekBar(this).apply { max = 9; progress = style.rowCount - 1 }
        root.addView(rowCount)
        val rowCountValue = TextView(this).apply { text = "${rowCount.progress + 1} строк" }
        root.addView(rowCountValue)
        rowCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                rowCountValue.text = "${progress + 1} строк"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val compact = CheckBox(this).apply {
            text = "Компактные строки (уменьшенные отступы)"
            isChecked = style.compact
        }
        root.addView(compact)
        val error = TextView(this)
        root.addView(error)
        val save = Button(this).apply { text = "Сохранить настройки" }
        root.addView(save)
        setContentView(ScrollView(this).apply { addView(root) })
        save.setOnClickListener {
            try {
                val selected = if (single) ids.getOrNull(account.selectedItemPosition) else null
                if (single && selected == null) { error.text = "Выберите счёт"; return@setOnClickListener }
                val updated = WidgetStyle(
                    Color.parseColor(background.text.toString().trim()),
                    opacity.progress,
                    Color.parseColor(titleColor.text.toString().trim()),
                    Color.parseColor(amountColor.text.toString().trim()),
                    icons[icon.selectedItemPosition].takeUnless { it == "Без иконки" } ?: "",
                    showName.isChecked, showHeader.isChecked,
                    listOf("sans", "serif", "mono")[font.selectedItemPosition],
                    fontSize.progress + 12,
                    labelSize.progress + 10,
                    headerSize.progress + 10,
                    rowCount.progress + 1,
                    compact.isChecked
                )
                updated.save(this, id)
                if (selected != null) getSharedPreferences("widget_instances", MODE_PRIVATE).edit().putInt("account_$id", selected).apply()
                FinanceWidgetProvider.schedule(this)
                FinanceWidgetProvider.refresh(this)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            } catch (_: IllegalArgumentException) { error.text = "Неверный HEX-цвет. Пример: #122D4C" }
        }
    }
}
