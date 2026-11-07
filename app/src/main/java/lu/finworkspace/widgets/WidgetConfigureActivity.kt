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
        val transactions = provider.endsWith("TransactionsWidget")
        val style = WidgetStyle.load(this, id)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 24, 28, 24) }
        fun heading(label: String) { root.addView(TextView(this).apply { text = label; textSize = 17f; setPadding(0, 18, 0, 5) }) }
        var activeSection: LinearLayout? = null
        fun addControl(view: View) {
            (activeSection ?: root).addView(view)
        }
        fun section(label: String, initiallyExpanded: Boolean = false) {
            val density = resources.displayMetrics.density
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (initiallyExpanded) View.VISIBLE else View.GONE
            }
            val toggle = TextView(this).apply {
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, (18 * density).toInt(), 0, (12 * density).toInt())
                text = (if (initiallyExpanded) "▾  " else "▸  ") + label
                contentDescription = label
                setOnClickListener {
                    val expand = content.visibility != View.VISIBLE
                    content.visibility = if (expand) View.VISIBLE else View.GONE
                    text = (if (expand) "▾  " else "▸  ") + label
                }
            }
            root.addView(toggle)
            root.addView(content)
            activeSection = content
        }
        fun stepButtons(slider: SeekBar) {
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
            }
            val minus = Button(this).apply {
                text = "−"
                contentDescription = "Уменьшить значение"
                setOnClickListener { slider.progress = (slider.progress - 1).coerceAtLeast(0) }
            }
            val plus = Button(this).apply {
                text = "+"
                contentDescription = "Увеличить значение"
                setOnClickListener { slider.progress = (slider.progress + 1).coerceAtMost(slider.max) }
            }
            buttons.addView(minus)
            buttons.addView(plus)
            addControl(buttons)
        }
        fun entry(value: String, hint: String): EditText = EditText(this).apply {
            setText(value); this.hint = hint; setSingleLine(true); addControl(this)
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
        val showAccountName = CheckBox(this).apply {
            text = "Показывать название счёта"
            isChecked = style.showAccountName
        }
        if (single) {
            root.addView(showAccountName)
        }
        section("Фон и цвета", true)
        addControl(TextView(this).apply { text = "Цвет фона (HEX)"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val background = entry(String.format("#%06X", 0xFFFFFF and style.background), "#122D4C")
        fun colorPicker(target: EditText) {
            val initial = runCatching { Color.parseColor(target.text.toString().trim()) }.getOrDefault(Color.WHITE)
            val hsv = FloatArray(3)
            Color.colorToHSV(initial, hsv)
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 20, 32, 8)
            }
            val sample = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (48 * resources.displayMetrics.density).toInt()
                )
            }
            panel.addView(sample)
            val controls = mutableListOf<SeekBar>()
            listOf("Оттенок", "Насыщенность", "Яркость").forEachIndexed { index, label ->
                panel.addView(TextView(this).apply { text = label; setPadding(0, 12, 0, 0) })
                val slider = SeekBar(this).apply {
                    max = if (index == 0) 360 else 100
                    progress = (hsv[index] * if (index == 0) 1f else 100f).toInt()
                }
                panel.addView(slider)
                controls.add(slider)
            }
            fun chosenColor(): Int = Color.HSVToColor(floatArrayOf(
                controls[0].progress.toFloat(),
                controls[1].progress / 100f,
                controls[2].progress / 100f
            ))
            fun updateSample() {
                sample.setBackgroundColor(chosenColor())
            }
            controls.forEach { slider ->
                slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateSample() }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            updateSample()
            android.app.AlertDialog.Builder(this)
                .setTitle("Выбор цвета")
                .setView(panel)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Выбрать") { _, _ ->
                    target.setText(String.format("#%06X", chosenColor() and 0xFFFFFF))
                }
                .show()
        }
        fun colorControl(target: EditText) {
            val parent = target.parent as? android.view.ViewGroup ?: return
            val position = parent.indexOfChild(target)
            parent.removeView(target)
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (46 * resources.displayMetrics.density).toInt(),
                    (46 * resources.displayMetrics.density).toInt()
                ).apply { setMargins(0, 0, 12, 0) }
                contentDescription = "Открыть палитру цветов"
                setOnClickListener { colorPicker(target) }
            }
            fun updateSwatch() {
                val color = runCatching { Color.parseColor(target.text.toString().trim()) }.getOrDefault(Color.GRAY)
                swatch.background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 10 * resources.displayMetrics.density
                    setStroke(1, Color.GRAY)
                }
            }
            target.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            line.addView(swatch)
            line.addView(target)
            target.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSwatch() }
                override fun afterTextChanged(s: Editable?) {}
            })
            updateSwatch()
            parent.addView(line, position)
        }
        colorControl(background)
        addControl(TextView(this).apply { text = "Прозрачность фона (0% — прозрачный, 100% — непрозрачный)"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val opacity = SeekBar(this).apply { max = 100; progress = style.opacity }
        addControl(opacity)
        stepButtons(opacity)
        val opacityValue = TextView(this).apply { text = "${opacity.progress}%" }
        addControl(opacityValue)
        addControl(TextView(this).apply { text = "Цвет названий (HEX)"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val titleColor = entry(String.format("#%06X", 0xFFFFFF and style.titleColor), "#CDE1F8")
        colorControl(titleColor)
        addControl(TextView(this).apply { text = "Цвет сумм (HEX)"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val amountColor = entry(String.format("#%06X", 0xFFFFFF and style.amountColor), "#FFFFFF")
        colorControl(amountColor)
        section("Заголовок и текст")
        addControl(TextView(this).apply { text = "Иконка"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val icons = listOf("◆", "🏦", "💳", "💰", "💵", "📊", "📈", "↔", "🛒", "🚗", "🏠", "★", "●", "Без иконки")
        val icon = Spinner(this).apply { adapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, icons); setSelection(if (style.icon.isEmpty()) icons.lastIndex else icons.indexOf(style.icon).coerceAtLeast(0)) }
        addControl(icon)
        val showHeader = CheckBox(this).apply { text = "Отображать заголовок"; isChecked = style.showHeader }
        addControl(showHeader)
        addControl(TextView(this).apply { text = "Отображение названий"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val nameModes = listOf("Одна строка", "Две строки", "Только иконка")
        val nameMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, nameModes)
            setSelection(when (style.nameLines) { 2 -> 1; 0 -> 2; else -> 0 })
        }
        addControl(nameMode)
        addControl(TextView(this).apply { text = "Шрифт"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val fonts = listOf("Обычный", "С засечками", "Моноширинный")
        val font = Spinner(this).apply { adapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, fonts); setSelection(listOf("sans", "serif", "mono").indexOf(style.font).coerceAtLeast(0)) }
        addControl(font)
        addControl(TextView(this).apply { text = "Размер шрифта суммы (12–30 sp)"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val fontSize = SeekBar(this).apply { max = 18; progress = style.fontSize - 12 }
        addControl(fontSize)
        stepButtons(fontSize)
        val fontSizeValue = TextView(this).apply { text = "${fontSize.progress + 12} sp" }
        addControl(fontSizeValue)
        addControl(TextView(this).apply { text = "Размер шрифта названий (10–24 sp)"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val labelSize = SeekBar(this).apply { max = 14; progress = style.labelSize - 10 }
        addControl(labelSize)
        stepButtons(labelSize)
        val labelSizeValue = TextView(this).apply { text = "${labelSize.progress + 10} sp" }
        addControl(labelSizeValue)
        addControl(TextView(this).apply { text = "Размер шрифта заголовка (10–26 sp)"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val headerSize = SeekBar(this).apply { max = 16; progress = style.headerSize - 10 }
        addControl(headerSize)
        stepButtons(headerSize)
        val headerSizeValue = TextView(this).apply { text = "${headerSize.progress + 10} sp" }
        addControl(headerSizeValue)
        addControl(TextView(this).apply { text = "Нажатие на виджет"; textSize = 17f; setPadding(0, 18, 0, 5) })
        val tapActions = listOf("Редактировать виджет", "Ничего не делать")
        val tapAction = Spinner(this).apply {
            adapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_dropdown_item, tapActions)
            setSelection(if (style.tapAction == "none") 1 else 0)
        }
        addControl(tapAction)
        section("Строки и размеры")
        val minRows = if (transactions) 3 else 1
        val maxRows = if (transactions) 15 else 10
        addControl(TextView(this).apply {
            text = if (transactions) "Количество транзакций (3–15)" else "Количество строк (1–10)"
            textSize = 17f
            setPadding(0, 18, 0, 5)
        })
        val rowCount = SeekBar(this).apply {
            max = maxRows - minRows
            progress = style.rowCount.coerceIn(minRows, maxRows) - minRows
        }
        addControl(rowCount)
        stepButtons(rowCount)
        val rowCountValue = TextView(this).apply { text = "${rowCount.progress + minRows} строк" }
        addControl(rowCountValue)
        rowCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                rowCountValue.text = "${progress + minRows} строк"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val compact = CheckBox(this).apply {
            text = "Компактные строки (уменьшенные отступы)"
            isChecked = style.compact
        }
        addControl(compact)
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
                line.visibility = if (index < rowCount.progress + minRows && (!getSharedPreferences("widget_preview_ui", MODE_PRIVATE).getBoolean("compact", false) || index == 0)) View.VISIBLE else View.GONE
                line.setPadding(0, if (compact.isChecked) 0 else 5, 0, if (compact.isChecked) 0 else 5)
                val name = line.getChildAt(0) as TextView
                val value = line.getChildAt(1) as TextView
                name.text = if (single && !showAccountName.isChecked) "" else if (mode == 2) "◆" else samples[index]
                name.visibility = if (name.text.isBlank()) View.GONE else View.VISIBLE
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
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityValue.text = "${opacity.progress}%"
                fontSizeValue.text = "${fontSize.progress + 12} sp"
                labelSizeValue.text = "${labelSize.progress + 10} sp"
                headerSizeValue.text = "${headerSize.progress + 10} sp"
                updatePreview()
            }
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
        showAccountName.setOnCheckedChangeListener { _, _ -> updatePreview() }
        compact.setOnCheckedChangeListener { _, _ -> updatePreview() }
        rowCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                rowCountValue.text = "${progress + minRows} строк"
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
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val previewCompact = getSharedPreferences("widget_preview_ui", MODE_PRIVATE)
            .getBoolean("compact", false)
        val previewToggle = TextView(this).apply {
            textSize = 14f
            setPadding(28, 8, 28, 8)
            gravity = android.view.Gravity.END
        }
        fun setPreviewCompact(compactMode: Boolean) {
            previewToggle.text = if (compactMode) "Развернуть предпросмотр ▾" else "Свернуть предпросмотр ▴"
            previewRows.forEachIndexed { index, line ->
                line.visibility = if (index < rowCount.progress + minRows && (!compactMode || index == 0)) View.VISIBLE else View.GONE
            }
            preview.setPadding(24, if (compactMode) 10 else 20, 24, if (compactMode) 10 else 20)
        }
        var isPreviewCompact = previewCompact
        previewToggle.setOnClickListener {
            isPreviewCompact = !isPreviewCompact
            getSharedPreferences("widget_preview_ui", MODE_PRIVATE).edit()
                .putBoolean("compact", isPreviewCompact).apply()
            updatePreview()
            setPreviewCompact(isPreviewCompact)
        }
        setPreviewCompact(isPreviewCompact)
        val previewTitle = TextView(this).apply {
            text = "Предварительный просмотр"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 12, 28, 6)
        }
        screen.addView(previewTitle)
        screen.addView(previewToggle)
        val previewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 0, 28, 12)
            addView(preview)
        }
        screen.addView(previewContainer)
        screen.addView(ScrollView(this).apply {
            isFillViewport = false
            addView(root)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(screen)
        setPreviewCompact(isPreviewCompact)
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
                    rowCount.progress + minRows,
                    compact.isChecked,
                    when (nameMode.selectedItemPosition) { 1 -> 2; 2 -> 0; else -> 1 },
                    if (tapAction.selectedItemPosition == 1) "none" else "edit",
                    showAccountName.isChecked
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
