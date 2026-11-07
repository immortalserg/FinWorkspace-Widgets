package lu.finworkspace.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextWatcher
import android.text.Editable
import android.view.View
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
        val showHeader = CheckBox(this).apply { text = "Отображать заголовок"; isChecked = style.showHeader }
        root.addView(showHeader)
        heading("Отображение названий")
        val nameModes = listOf("Одна строка", "Две строки", "Только иконка")
        val nameMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, nameModes)
            setSelection(when (style.nameLines) { 2 -> 1; 0 -> 2; else -> 0 })
        }
        root.addView(nameMode)
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
        heading("Предварительный просмотр")
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        val previewHeading = TextView(this)
        val previewRows = (0 until 3).map {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val label = TextView(this@WidgetConfigureActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val amount = TextView(this@WidgetConfigureActivity).apply {
                    gravity = android.view.Gravity.END
                    setPadding(12, 0, 0, 0)
                }
                addView(label); addView(amount)
            }
        }
        preview.addView(previewHeading)
        previewRows.forEach { preview.addView(it) }
        root.addView(preview)
        fun updatePreview() {
            val bg = runCatching { Color.parseColor(background.text.toString().trim()) }.getOrDefault(style.background)
            val title = runCatching { Color.parseColor(titleColor.text.toString().trim()) }.getOrDefault(style.titleColor)
            val amountColorValue = runCatching { Color.parseColor(amountColor.text.toString().trim()) }.getOrDefault(style.amountColor)
            val face = when (font.selectedItemPosition) {
                1 -> Typeface.SERIF
                2 -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
            preview.background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor((bg and 0x00FFFFFF) or ((opacity.progress * 255 / 100) shl 24))
            }
            val symbol = icons.getOrNull(icon.selectedItemPosition).orEmpty().takeUnless { it == "Без иконки" }.orEmpty()
            previewHeading.text = listOf(symbol, if (showHeader.isChecked) "FinWorkspace" else "").filter { it.isNotBlank() }.joinToString("  ")
            previewHeading.visibility = if (previewHeading.text.isBlank()) View.GONE else View.VISIBLE
            previewHeading.setTextColor(title)
            previewHeading.textSize = (headerSize.progress + 10).toFloat()
            previewHeading.typeface = face
            val mode = nameMode.selectedItemPosition
            val samples = listOf("Основной счёт", "Сбережения", "Карта")
            val values = listOf("1 234,56 €", "980,00 €", "−42,50 €")
            previewRows.forEachIndexed { index, line ->
                line.visibility = if (index < rowCount.progress + 1) View.VISIBLE else View.GONE
                line.setPadding(0, if (compact.isChecked) 0 else 5, 0, if (compact.isChecked) 0 else 5)
                val name = line.getChildAt(0) as TextView
                val value = line.getChildAt(1) as TextView
                name.text = if (mode == 2) "◆" else samples[index]
                name.maxLines = if (mode == 1) 2 else 1
                name.setTextColor(title)
                name.textSize = (labelSize.progress + 10).toFloat()
                name.typeface = face
                value.text = values[index]
                value.setTextColor(amountColorValue)
                value.textSize = (fontSize.progress + 12).toFloat()
                value.typeface = face
            }
        }
        val previewWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        }
        listOf(background, titleColor, amountColor).forEach { it.addTextChangedListener(previewWatcher) }
        val previewSeekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updatePreview() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        listOf(opacity, fontSize, labelSize, headerSize).forEach { it.setOnSeekBarChangeListener(previewSeekListener) }
        val previewSelectionListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { updatePreview() }
        }
        listOf(icon, font, nameMode).forEach { it.onItemSelectedListener = previewSelectionListener }
        showHeader.setOnCheckedChangeListener { _, _ -> updatePreview() }
        compact.setOnCheckedChangeListener { _, _ -> updatePreview() }
        rowCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                rowCountValue.text = "${progress + 1} строк"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        updatePreview()
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
                    nameMode.selectedItemPosition != 2, showHeader.isChecked,
                    listOf("sans", "serif", "mono")[font.selectedItemPosition],
                    fontSize.progress + 12,
                    labelSize.progress + 10,
                    headerSize.progress + 10,
                    rowCount.progress + 1,
                    compact.isChecked,
                    when (nameMode.selectedItemPosition) { 1 -> 2; 2 -> 0; else -> 1 }
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
