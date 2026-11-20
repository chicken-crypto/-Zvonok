package com.example.kotozvonok

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class Screen { HOME, DIALER, CONTACTS, SETTINGS }

    private lateinit var rootFrame: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var numberField: EditText
    private lateinit var suggestionsList: ListView
    private lateinit var searchField: EditText
    private lateinit var navContainer: FrameLayout
    private lateinit var navPill: LiquidGlassView
    private lateinit var navRow: LinearLayout
    private lateinit var navWideLabel: TextView
    private lateinit var searchBox: LiquidGlassView
    private var drawerView: View? = null
    private var currentScreen = Screen.HOME
    private var callFilter = 0
    private val REQ_ADD_CAT = 4
    private val REQ_PICK_ICON = 5
    private val REQ_RINGTONE = 6
    private val REQ_PICK_FONT = 7
    private var pendingIconKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF15131F.toInt())
        }
        outer.addView(buildTopBar())

        content = FrameLayout(this)
        outer.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        outer.addView(buildBottomNav(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))

        rootFrame = FrameLayout(this)
        rootFrame.addView(outer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(rootFrame)
        showScreen(Screen.HOME)
    }

    override fun onResume() {
        super.onResume()
        showScreen(currentScreen)
    }

    override fun onBackPressed() {
        if (drawerView != null) { closeDrawer(); return }
        super.onBackPressed()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fontTypeface(): Typeface? {
        return when (Prefs.fontFamily(this)) {
            "sans" -> Typeface.SANS_SERIF
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            "sfpro" -> try { ResourcesCompat.getFont(this, R.font.sf_pro_text) } catch (e: Exception) { null }
            "custom" -> {
                val path = Prefs.customFontPath(this)
                if (path != null) try { Typeface.createFromFile(path) } catch (e: Exception) { null } else null
            }
            else -> null
        }
    }

    private fun applyFont(view: View) {
        val tf = fontTypeface() ?: return
        fun walk(v: View) {
            if (v is TextView) v.typeface = tf
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(view)
    }

    // ---------- Верхняя панель: "☰" + поиск контактов ----------
    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF15131F.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(10))
        }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_menu)
            setColorFilter(Color.WHITE)
            setOnClickListener { openDrawer() }
        }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(14) })

        val accent = Prefs.accentColor(this)
        searchBox = LiquidGlassView(this).apply {
            setGlassColor((0x22000000.toInt()) or (accent and 0x00FFFFFF))
            setCorner(dp(22).toFloat())
            setupWithActivityRoot()
        }
        searchField = EditText(this).apply {
            hint = "Поиск контактов"
            setHintTextColor(0xFFB3AFC9.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            background = null
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { showScreen(Screen.CONTACTS) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, count: Int, before: Int) {}
            })
        }
        searchBox.addView(searchField, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.addView(searchBox, LinearLayout.LayoutParams(0, dp(44), 1f))
        return bar
    }

    // ---------- Выезжающая панель-меню (половина экрана, "жидкое стекло") ----------
    private fun openDrawer() {
        if (drawerView != null) return
        val panelWidth = resources.displayMetrics.widthPixels / 2

        val scrim = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            alpha = 0f
            setOnClickListener { closeDrawer() }
        }
        val panel = LiquidGlassView(this).apply {
            setGlassColor(0x30FFFFFF)
            setupWithActivityRoot()
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(64), dp(18), dp(18))
        }
        menu.addView(TextView(this).apply {
            text = "Котозвонок"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        })
        fun item(label: String, action: () -> Unit) {
            menu.addView(TextView(this).apply {
                text = label; setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener { closeDrawer(); action() }
            })
        }
        item("Контакты") { showScreen(Screen.CONTACTS) }
        item("Настройки") { showScreen(Screen.SETTINGS) }
        item("Очистить журнал звонков") { clearCallLog() }
        panel.addView(menu, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val container = FrameLayout(this)
        container.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        container.addView(panel, FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        rootFrame.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        drawerView = container

        panel.translationX = -panelWidth.toFloat()
        panel.post {
            panel.updateBackgroundSnapshot()
            scrim.animate().alpha(1f).setDuration(200).start()
            panel.animate().translationX(0f).setDuration(260).start()
        }
    }

    private fun closeDrawer() {
        val container = drawerView as? ViewGroup ?: return
        val panel = container.getChildAt(1)
        val scrim = container.getChildAt(0)
        panel.animate().translationX(-panel.width.toFloat()).setDuration(220).withEndAction {
            rootFrame.removeView(container)
        }.start()
        scrim.animate().alpha(0f).setDuration(220).start()
        drawerView = null
    }

    private fun clearCallLog() {
        if (checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CALL_LOG), 1)
            return
        }
        try { contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) } catch (e: Exception) { }
        if (currentScreen == Screen.HOME) showScreen(Screen.HOME)
    }

    // ---------- Нижняя навигация ----------
    private fun buildBottomNav(): FrameLayout {
        val container = FrameLayout(this).apply { setBackgroundColor(0xFF1D1A2E.toInt()) }
        navContainer = container
        val accent = Prefs.accentColor(this)

        navPill = LiquidGlassView(this).apply {
            setGlassColor((0x33000000.toInt()) or (accent and 0x00FFFFFF))
            setCorner(dp(24).toFloat())
            setupWithActivityRoot()
        }
        container.addView(navPill, FrameLayout.LayoutParams(0, dp(50)).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })

        navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val items = listOf(Triple("tab_home", R.drawable.ic_home, "Главный экран"), Triple("tab_dialer", R.drawable.ic_dialpad, "Клавиатура"))
        items.forEachIndexed { i, (key, res, label) ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener { selectNavTab(i) }
            }
            val icon = Icons.iconView(this, key, res)
            if (Icons.isCustom(this, key)) {
                icon.clipToOutline = true
                icon.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
                }
            }
            col.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
            col.addView(TextView(this).apply {
                text = label; textSize = 9.5f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                maxLines = 1; isSingleLine = true
                setPadding(dp(2), dp(2), dp(2), 0)
            })
            navRow.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        container.addView(navRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        navWideLabel = TextView(this).apply {
            textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { selectNavTab(0) }
        }
        container.addView(navWideLabel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        container.post { positionPill(navPill, navContainer, 2, 0, animate = false) }
        return container
    }

    // Когда открыты "Настройки"/"Контакты" — две вкладки снизу плавно прячутся,
    // а вся плашка растягивается в одну большую с подписью текущего раздела.
    private fun morphNavFor(s: Screen) {
        if (!::navRow.isInitialized) return
        val wide = s == Screen.SETTINGS || s == Screen.CONTACTS
        if (wide) {
            navWideLabel.text = if (s == Screen.SETTINGS) "Настройки" else "Контакты"
            navRow.animate().alpha(0f).setDuration(180).withEndAction { navRow.visibility = View.GONE }.start()
            navWideLabel.visibility = View.VISIBLE
            navWideLabel.animate().alpha(1f).setDuration(220).start()
            navContainer.post {
                val lp = navPill.layoutParams as FrameLayout.LayoutParams
                lp.width = navContainer.width - navContainer.paddingLeft - navContainer.paddingRight - dp(16)
                lp.marginStart = dp(8)
                navPill.layoutParams = lp
                navPill.animate().translationX(0f).setDuration(260).start()
            }
        } else {
            navWideLabel.animate().alpha(0f).setDuration(160).withEndAction { navWideLabel.visibility = View.GONE }.start()
            navRow.visibility = View.VISIBLE
            navRow.animate().alpha(1f).setDuration(200).start()
            positionPill(navPill, navContainer, 2, if (s == Screen.HOME) 0 else 1, animate = true)
        }
    }

    private fun positionPill(pill: View, parent: FrameLayout, count: Int, index: Int, animate: Boolean) {
        // Используем ширину БЕЗ отступов контейнера — иначе плашка съезжает относительно текста.
        val usable = parent.width - parent.paddingLeft - parent.paddingRight
        if (usable <= 0) return
        val seg = usable / count
        val lp = pill.layoutParams as FrameLayout.LayoutParams
        lp.width = seg - dp(16)
        lp.marginStart = dp(8)
        pill.layoutParams = lp
        val targetX = (seg * index).toFloat()
        if (animate) pill.animate().translationX(targetX).setDuration(260).start()
        else pill.translationX = targetX
    }

    private fun selectNavTab(i: Int) {
        positionPill(navPill, navContainer, 2, i, animate = true)
        showScreen(if (i == 0) Screen.HOME else Screen.DIALER)
    }

    private fun showScreen(s: Screen) {
        currentScreen = s
        content.removeAllViews()
        val view = when (s) {
            Screen.HOME -> buildCallsTab()
            Screen.DIALER -> buildDialerTab()
            Screen.CONTACTS -> buildContactsTab()
            Screen.SETTINGS -> buildSettingsTab()
        }
        applyFont(view)
        content.addView(view)
        morphNavFor(s)
        if (::navPill.isInitialized) navPill.updateBackgroundSnapshot()
        if (::searchBox.isInitialized) searchBox.updateBackgroundSnapshot()
    }

    // Переиспользуемый "стеклянный" переключатель вариантов со скользящей плашкой
    private fun glassSelector(labels: List<String>, selectedIndex: Int, accent: Int, onSelect: (Int) -> Unit): View {
        val container = FrameLayout(this)
        val pill = LiquidGlassView(this).apply {
            setGlassColor((0x33000000.toInt()) or (accent and 0x00FFFFFF))
            setCorner(dp(18).toFloat())
            setupWithActivityRoot()
        }
        container.addView(pill, FrameLayout.LayoutParams(0, dp(36)).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        labels.forEachIndexed { i, label ->
            row.addView(TextView(this).apply {
                text = label; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                maxLines = 1
                setOnClickListener {
                    positionPill(pill, container, labels.size, i, animate = true)
                    onSelect(i)
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        container.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
        container.post {
            pill.updateBackgroundSnapshot()
            positionPill(pill, container, labels.size, selectedIndex, animate = false)
        }
        return container
    }

    private fun card(block: LinearLayout.() -> Unit): MaterialCardView {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        inner.block()
        return MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(1).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.md_surface))
            addView(inner, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    // ---------- Экран: набор номера ----------
    private val digitLetters = mapOf(
        "2" to "ABC · АБВГ", "3" to "DEF · ДЕЁЖЗ", "4" to "GHI · ИЙКЛ",
        "5" to "JKL · МНОП", "6" to "MNO · РСТУ", "7" to "PQRS · ФХЦЧ",
        "8" to "TUV · ШЩЪЫ", "9" to "WXYZ · ЬЭЮЯ", "0" to "+"
    )

    private fun buildDialerTab(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(16))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        numberField = EditText(this).apply {
            hint = "Введите номер"
            setHintTextColor(0xFF6B6880.toInt())
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_PHONE
            background = null
            isFocusable = false
        }
        root.addView(numberField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })

        suggestionsList = ListView(this).apply { divider = null }
        root.addView(suggestionsList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        refreshSuggestions()

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )
        rows.forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            row.forEach { digit ->
                val sub = digitLetters[digit]
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                col.addView(TextView(this).apply { text = digit; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
                if (sub != null) col.addView(TextView(this).apply { text = sub; textSize = 7f; setTextColor(0xFF8A8698.toInt()); gravity = Gravity.CENTER })
                val box = FrameLayout(this).apply {
                    background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(0xFF2A2640.toInt()) }
                    addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                    setOnClickListener { numberField.append(digit); refreshSuggestions() }
                }
                r.addView(box, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
            }
            root.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        actionRow.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF3A3650.toInt()) }
            val ic = ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_close); setColorFilter(Color.WHITE) }
            addView(ic, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener {
                val t = numberField.text.toString()
                if (t.isNotEmpty()) numberField.setText(t.dropLast(1))
                refreshSuggestions()
            }
        }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { setMargins(dp(10), 0, dp(10), 0) })
        actionRow.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2FBF71.toInt()) }
            val ic = ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_check); setColorFilter(Color.WHITE) }
            addView(ic, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            setOnClickListener { placeCall(numberField.text.toString()) }
        }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(dp(10), 0, dp(10), 0) })
        root.addView(actionRow)

        return ScrollView(this).apply { addView(root) }
    }

    private fun refreshSuggestions() {
        if (!::suggestionsList.isInitialized) return
        val digits = numberField.text.toString().filter { it.isDigit() }
        if (digits.length < 2 || checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            suggestionsList.layoutParams = suggestionsList.layoutParams.apply { height = 0 }
            suggestionsList.adapter = null
            return
        }
        data class S(val name: String, val number: String)
        val found = mutableListOf<S>()
        val cur = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        cur?.use {
            while (it.moveToNext() && found.size < 5) {
                val num = it.getString(1) ?: continue
                if (num.filter { c -> c.isDigit() }.contains(digits)) {
                    found.add(S(it.getString(0) ?: "?", num))
                }
            }
        }
        if (found.isEmpty()) {
            suggestionsList.layoutParams = suggestionsList.layoutParams.apply { height = 0 }
            suggestionsList.adapter = null
            return
        }
        suggestionsList.layoutParams = suggestionsList.layoutParams.apply { height = dp(56) * found.size.coerceAtMost(3) }
        suggestionsList.adapter = object : ArrayAdapter<S>(this, 0, found) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = found[position]
                val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
                row.addView(TextView(this@MainActivity).apply { text = item.name; setTextColor(Color.WHITE); textSize = 14f })
                row.addView(TextView(this@MainActivity).apply { text = item.number; setTextColor(0xFFA29FB8.toInt()); textSize = 12f })
                return row
            }
        }
        suggestionsList.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            numberField.setText(found[pos].number)
            refreshSuggestions()
        }
    }

    private fun placeCall(number: String) {
        if (number.isBlank()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
            return
        }
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    // ---------- Экран: контакты (фильтруется строкой поиска сверху) ----------
    private fun buildContactsTab(): View {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return permissionNeededView("Нужен доступ к контактам") {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)
            }
        }
        val query = if (::searchField.isInitialized) searchField.text.toString().trim() else ""
        data class C(val name: String, val number: String)
        val list = mutableListOf<C>()
        val cur = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cur?.use {
            while (it.moveToNext()) list.add(C(it.getString(0) ?: "?", it.getString(1) ?: ""))
        }
        val filtered = if (query.isEmpty()) list else list.filter {
            it.name.contains(query, ignoreCase = true) || it.number.contains(query)
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            text = "↻ Обновить"; textSize = 13f; setTextColor(0xFFA29FB8.toInt()); gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
            setOnClickListener { showScreen(Screen.CONTACTS) }
        })
        if (filtered.isEmpty()) {
            root.addView(emptyView(if (query.isEmpty()) "Контактов не найдено" else "Ничего не найдено"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            return root
        }

        val lv = ListView(this)
        lv.setBackgroundColor(0xFF15131F.toInt())
        lv.divider = null
        lv.adapter = object : ArrayAdapter<C>(this, 0, filtered) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = filtered[position]
                val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
                row.addView(TextView(this@MainActivity).apply { text = item.name; setTextColor(Color.WHITE); textSize = 16f })
                row.addView(TextView(this@MainActivity).apply { text = item.number; setTextColor(0xFFA29FB8.toInt()); textSize = 13f })
                return row
            }
        }
        lv.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            selectNavTab(1)
            numberField.setText(filtered[pos].number)
        }
        root.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    // ---------- Экран: главный экран (журнал звонков + фильтры) ----------
    private fun buildCallsTab(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val accent = Prefs.accentColor(this)

        val filterContainer = FrameLayout(this).apply { setPadding(dp(12), dp(10), dp(12), dp(6)) }
        val filterPill = LiquidGlassView(this).apply {
            setGlassColor((0x33000000.toInt()) or (accent and 0x00FFFFFF))
            setCorner(dp(18).toFloat())
            setupWithActivityRoot()
        }
        filterContainer.addView(filterPill, FrameLayout.LayoutParams(0, dp(36)).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val filters = listOf("Все", "Пропущенные", "Контакты")
        filters.forEachIndexed { i, label ->
            filterRow.addView(TextView(this).apply {
                text = label; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                maxLines = 1
                setOnClickListener {
                    callFilter = i
                    positionPill(filterPill, filterContainer, filters.size, i, animate = true)
                    showScreen(Screen.HOME)
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        filterContainer.addView(filterRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        filterContainer.post {
            filterPill.updateBackgroundSnapshot()
            positionPill(filterPill, filterContainer, filters.size, callFilter, animate = false)
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topRow.addView(filterContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(TextView(this).apply {
            text = "↻"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(14), 0)
            setOnClickListener { showScreen(Screen.HOME) }
        })
        root.addView(topRow)

        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            root.addView(permissionNeededView("Нужен доступ к журналу звонков") {
                requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), 1)
            })
            return root
        }

        data class H(val name: String, val number: String, val type: Int, val date: Long)
        val all = mutableListOf<H>()
        val cur = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            null, null, CallLog.Calls.DATE + " DESC"
        )
        cur?.use {
            var count = 0
            while (it.moveToNext() && count < 150) {
                all.add(H(it.getString(0) ?: "", it.getString(1) ?: "", it.getInt(2), it.getLong(3)))
                count++
            }
        }
        val list = when (callFilter) {
            1 -> all.filter { it.type == CallLog.Calls.MISSED_TYPE }
            2 -> all.filter { it.name.isNotEmpty() }
            else -> all
        }.take(60)

        if (list.isEmpty()) {
            root.addView(emptyView("Звонков пока нет"))
            return root
        }

        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val lv = ListView(this)
        lv.setBackgroundColor(0xFF15131F.toInt())
        lv.divider = null
        lv.adapter = object : ArrayAdapter<H>(this, 0, list) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = list[position]
                val rotation = if (item.type == CallLog.Calls.OUTGOING_TYPE) 0f else 180f
                val tint = if (item.type == CallLog.Calls.MISSED_TYPE) 0xFFE5484D.toInt() else Color.WHITE
                val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
                row.addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_call_arrow); setColorFilter(tint); this.rotation = rotation
                }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(12) })
                val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                col.addView(TextView(this@MainActivity).apply { text = item.name.ifEmpty { item.number }; setTextColor(Color.WHITE); textSize = 15f })
                col.addView(TextView(this@MainActivity).apply { text = fmt.format(Date(item.date)); setTextColor(0xFFA29FB8.toInt()); textSize = 12f })
                row.addView(col)
                return row
            }
        }
        lv.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            selectNavTab(1)
            numberField.setText(list[pos].number)
        }
        root.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun permissionNeededView(msg: String, action: () -> Unit): View {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        l.addView(TextView(this).apply { text = msg; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER })
        l.addView(MaterialButton(this).apply {
            text = "Разрешить"; isAllCaps = false; cornerRadius = dp(24)
            backgroundTintList = ColorStateList.valueOf(Prefs.accentColor(this@MainActivity))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        return l
    }

    private fun emptyView(msg: String): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER; orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply { text = msg; setTextColor(0xFFA29FB8.toInt()); textSize = 15f; gravity = Gravity.CENTER })
    }

    // ---------- Экран: настройки (по разделам, раскрывающиеся) ----------
    private fun buildSettingsTab(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val accent = Prefs.accentColor(this)

        root.addView(TextView(this).apply {
            text = "Котозвонок"; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun button(parent: LinearLayout, t: String, action: (View) -> Unit) {
            parent.addView(MaterialButton(this).apply {
                text = t; isAllCaps = false; cornerRadius = dp(26)
                backgroundTintList = ColorStateList.valueOf(accent)
                setOnClickListener(action)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        fun switchRow(parent: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(MaterialSwitch(this).apply {
                isChecked = checked
                setOnCheckedChangeListener { _: CompoundButton, c: Boolean -> onChange(c) }
            })
            parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        fun centeredNote(parent: LinearLayout, t: String) {
            parent.addView(TextView(this).apply {
                text = t; textSize = 11f; setTextColor(0xFFA29FB8.toInt()); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }

        // Раскрывающийся раздел
        fun section(title: String, expanded: Boolean = false, build: (LinearLayout) -> Unit) {
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (expanded) View.VISIBLE else View.GONE
                setPadding(0, dp(10), 0, 0)
            }
            build(body)
            val arrow = TextView(this).apply { text = if (expanded) "▲" else "▼"; setTextColor(0xFF8FA2FF.toInt()); textSize = 12f }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setOnClickListener {
                    val show = body.visibility != View.VISIBLE
                    body.visibility = if (show) View.VISIBLE else View.GONE
                    arrow.text = if (show) "▲" else "▼"
                }
            }
            header.addView(TextView(this).apply {
                text = title; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(0, dp(4), dp(8), dp(4))
            })
            header.addView(arrow)
            val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(12)) }
            wrapper.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
            wrapper.addView(body)
            root.addView(MaterialCardView(this).apply {
                radius = dp(18).toFloat()
                cardElevation = dp(1).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.md_surface))
                addView(wrapper, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        section("Разрешения", expanded = true) { b ->
            button(b, "Доступ к звонкам и контактам") {
                requestPermissions(arrayOf(
                    Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG,
                    Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE
                ), 1)
            }
            button(b, "Показ поверх других окон") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            button(b, "Сделать звонилкой по умолчанию") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rm = getSystemService(RoleManager::class.java)
                    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 3)
                    }
                } else {
                    val i = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    i.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    startActivity(i)
                }
            }
        }

        section("Вид звонка") { b ->
            switchRow(b, "Полноэкранный звонок", Prefs.isFullScreenEnabled(this)) { Prefs.setFullScreenEnabled(this, it) }
            switchRow(b, "После ответа сворачивать в остров", Prefs.collapseToIslandOnAnswer(this)) { Prefs.setCollapseToIslandOnAnswer(this, it) }
            switchRow(b, "Показывать котиков", Prefs.isCatsEnabled(this)) { Prefs.setCatsEnabled(this, it) }
        }

        section("Размер маленького окошка") { b ->
            val options = listOf("Маленький" to 0.85f, "Обычный" to 1.0f, "Крупный" to 1.25f)
            val selected = options.indexOfFirst { it.second == Prefs.overlayScale(this) }.coerceAtLeast(0)
            b.addView(glassSelector(options.map { it.first }, selected, accent) { i ->
                Prefs.setOverlayScale(this, options[i].second); showScreen(Screen.SETTINGS)
            })
        }

        section("Рингтон") { b ->
            button(b, "Выбрать рингтон") {
                val current = Prefs.ringtoneUri(this)
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (current != null) Uri.parse(current) else null)
                }
                startActivityForResult(i, REQ_RINGTONE)
            }
            centeredNote(b, "Звучит вместе с обычным системным сигналом — заглушить системный звук сторонним приложением нельзя.")
        }

        section("Цветовая тема") { b ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            Prefs.ACCENT_PRESETS.forEach { (_, color) ->
                row.addView(FrameLayout(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(color)
                        if (color == accent) setStroke(dp(2), Color.WHITE)
                    }
                    setOnClickListener { Prefs.setAccentColor(this@MainActivity, color); recreate() }
                }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(6), 0, dp(6), 0) })
            }
            b.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }

        section("Шрифт") { b ->
            val fonts = listOf("default" to "Обычный", "sans" to "Без засечек", "serif" to "С засечками", "mono" to "Моно", "sfpro" to "SF Pro")
            val selected = fonts.indexOfFirst { it.first == Prefs.fontFamily(this) }.coerceAtLeast(0)
            b.addView(glassSelector(fonts.map { it.second }, selected, accent) { i ->
                Prefs.setFontFamily(this, fonts[i].first); recreate()
            })
            button(b, "Добавить свой шрифт (.ttf/.otf)") {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                startActivityForResult(i, REQ_PICK_FONT)
            }
            if (Prefs.fontFamily(this) == "custom") centeredNote(b, "Сейчас используется свой шрифт")
        }

        section("Свои картинки") { b ->
            button(b, "Добавить своего кота") {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                startActivityForResult(i, REQ_ADD_CAT)
            }
            centeredNote(b, "Своих котов добавлено: ${Prefs.getCustomCats(this).size}")
            button(b, "Удалить всех добавленных котов") { Prefs.clearCustomCats(this); showScreen(Screen.SETTINGS) }
        }

        section("Иконки кнопок и вкладок") { b ->
            val iconLabels = mapOf(
                "answer" to "Иконка кнопки «Ответить»",
                "decline" to "Иконка кнопки «Отклонить»",
                "search" to "Иконка кнопки «Поиск»",
                "tab_home" to "Иконка вкладки «Главный экран»",
                "tab_dialer" to "Иконка вкладки «Клавиатура»"
            )
            iconLabels.forEach { (key, label) ->
                button(b, label) {
                    pendingIconKey = key
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                    startActivityForResult(i, REQ_PICK_ICON)
                }
            }
            button(b, "Сбросить все иконки к стандартным") { Prefs.clearAllIcons(this); showScreen(Screen.SETTINGS) }
        }

        section("Значок приложения (рабочий стол)") { b ->
            centeredNote(b, "Произвольное фото системой не поддерживается — можно выбрать один из трёх готовых вариантов.")
            val iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            listOf(
                Triple("IconDefault", R.drawable.cat6, "Кот-джентльмен"),
                Triple("IconCat1", R.drawable.cat1, "Серый кот"),
                Triple("IconCat3", R.drawable.cat3, "Трёхцветный")
            ).forEach { (alias, res, label) ->
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), dp(6)) }
                col.addView(ImageView(this).apply {
                    setImageResource(res)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
                    }
                    setOnClickListener { setLauncherIcon(alias) }
                }, LinearLayout.LayoutParams(dp(56), dp(56)))
                col.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
                iconRow.addView(col)
            }
            b.addView(iconRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }

        section("Проверка") { b ->
            button(b, "Показать тестовый звонок") {
                if (Prefs.isFullScreenEnabled(this)) {
                    val i = Intent(this, CallScreenActivity::class.java).apply {
                        putExtra(CallScreenActivity.EXTRA_IS_TEST, true)
                        putExtra(CallScreenActivity.EXTRA_NAME, "Тестовый Котик")
                        putExtra(CallScreenActivity.EXTRA_NUMBER, "+7 900 000-00-00")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(i)
                } else {
                    OverlayManager.show(this, "Тестовый Котик", "+7 900 000-00-00", null, isTest = true)
                }
            }
        }

        return ScrollView(this).apply { addView(root) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ADD_CAT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
                Prefs.addCustomCat(this, uri.toString())
                showScreen(Screen.SETTINGS)
            }
        }
        if (requestCode == REQ_PICK_ICON && resultCode == RESULT_OK) {
            val key = pendingIconKey
            data?.data?.let { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
                if (key != null) Prefs.setIcon(this, key, uri.toString())
                showScreen(Screen.SETTINGS)
            }
            pendingIconKey = null
        }
        if (requestCode == REQ_RINGTONE) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Prefs.setRingtoneUri(this, uri?.toString())
            showScreen(Screen.SETTINGS)
        }
        if (requestCode == REQ_PICK_FONT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val outFile = File(filesDir, "custom_font.ttf")
                    contentResolver.openInputStream(uri)?.use { inp -> outFile.outputStream().use { out -> inp.copyTo(out) } }
                    Typeface.createFromFile(outFile) // проверка, что файл — действительно шрифт
                    Prefs.setCustomFontPath(this, outFile.absolutePath)
                    Prefs.setFontFamily(this, "custom")
                    recreate()
                } catch (e: Exception) { }
            }
        }
    }

    private fun setLauncherIcon(enableAlias: String) {
        val aliases = listOf("IconDefault", "IconCat1", "IconCat3")
        aliases.forEach { name ->
            val state = if (name == enableAlias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try {
                packageManager.setComponentEnabledSetting(
                    android.content.ComponentName(packageName, "$packageName.$name"),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) { }
        }
    }
}
