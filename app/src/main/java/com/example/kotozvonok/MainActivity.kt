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
import android.text.InputType
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
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class Screen { HOME, DIALER, CONTACTS, SETTINGS }

    private lateinit var content: FrameLayout
    private lateinit var numberField: EditText
    private lateinit var suggestionsList: ListView
    private lateinit var navContainer: FrameLayout
    private lateinit var navPill: View
    private var currentScreen = Screen.HOME
    private var callFilter = 0 // 0=Все 1=Пропущенные 2=Контакты
    private val REQ_ADD_CAT = 4
    private val REQ_PICK_ICON = 5
    private val REQ_RINGTONE = 6
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

        setContentView(outer)
        showScreen(Screen.HOME)
    }

    override fun onResume() {
        super.onResume()
        showScreen(currentScreen)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fontTypeface(): Typeface? = when (Prefs.fontFamily(this)) {
        "sans" -> Typeface.SANS_SERIF
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        else -> null
    }

    private fun applyFont(view: View) {
        val tf = fontTypeface() ?: return
        fun walk(v: View) {
            if (v is TextView) v.typeface = tf
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(view)
    }

    // ---------- Верхняя панель ----------
    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF15131F.toInt())
            setPadding(dp(14), dp(16), dp(14), dp(12))
        }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_menu)
            setColorFilter(Color.WHITE)
            setOnClickListener { showTopMenu(it) }
        }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(18) })
        bar.addView(TextView(this).apply {
            text = "Котозвонок"; textSize = 19f; setTextColor(Color.WHITE)
        })
        return bar
    }

    private fun showTopMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Контакты")
        popup.menu.add(0, 2, 1, "Настройки")
        popup.menu.add(0, 3, 2, "Очистить журнал звонков")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showScreen(Screen.CONTACTS)
                2 -> showScreen(Screen.SETTINGS)
                3 -> clearCallLog()
            }
            true
        }
        popup.show()
    }

    private fun clearCallLog() {
        if (checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CALL_LOG), 1)
            return
        }
        try { contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) } catch (e: Exception) { }
        if (currentScreen == Screen.HOME) showScreen(Screen.HOME)
    }

    // ---------- Нижняя навигация: 2 пункта + скользящая плашка ----------
    private fun buildBottomNav(): FrameLayout {
        val container = FrameLayout(this).apply { setBackgroundColor(0xFF1D1A2E.toInt()) }
        navContainer = container
        val accent = Prefs.accentColor(this)
        val tint = (0x33000000.toInt()) or (accent and 0x00FFFFFF)

        navPill = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(tint)
                setStroke(dp(1), 0x40FFFFFF)
            }
        }
        container.addView(navPill, FrameLayout.LayoutParams(0, dp(50)).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
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
            col.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
            col.addView(TextView(this).apply { text = label; textSize = 10f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        container.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        container.post { positionPill(navPill, navContainer, 2, 0, animate = false) }
        return container
    }

    private fun positionPill(pill: View, parent: FrameLayout, count: Int, index: Int, animate: Boolean) {
        val w = parent.width
        if (w == 0) return
        val seg = w / count
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
        if (::navContainer.isInitialized) {
            when (s) {
                Screen.HOME -> positionPill(navPill, navContainer, 2, 0, animate = true)
                Screen.DIALER -> positionPill(navPill, navContainer, 2, 1, animate = true)
                else -> { }
            }
        }
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
            setPadding(dp(20), dp(24), dp(20), dp(20))
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
        root.addView(suggestionsList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(0)))
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
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                }
                col.addView(TextView(this).apply { text = digit; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
                if (sub != null) col.addView(TextView(this).apply { text = sub; textSize = 7f; setTextColor(0xFF8A8698.toInt()); gravity = Gravity.CENTER })
                val box = FrameLayout(this).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2A2640.toInt()) }
                    addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                    setOnClickListener { numberField.append(digit); refreshSuggestions() }
                }
                r.addView(box, LinearLayout.LayoutParams(dp(66), dp(66)).apply { setMargins(dp(10), dp(8), dp(10), dp(8)) })
            }
            root.addView(r)
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

    // ---------- Экран: контакты ----------
    private fun buildContactsTab(): View {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return permissionNeededView("Нужен доступ к контактам") {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)
            }
        }
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
        if (list.isEmpty()) return emptyView("Контактов не найдено")

        val lv = ListView(this)
        lv.setBackgroundColor(0xFF15131F.toInt())
        lv.divider = null
        lv.adapter = object : ArrayAdapter<C>(this, 0, list) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = list[position]
                val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
                row.addView(TextView(this@MainActivity).apply { text = item.name; setTextColor(Color.WHITE); textSize = 16f })
                row.addView(TextView(this@MainActivity).apply { text = item.number; setTextColor(0xFFA29FB8.toInt()); textSize = 13f })
                return row
            }
        }
        lv.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            selectNavTab(1)
            numberField.setText(list[pos].number)
        }
        return lv
    }

    // ---------- Экран: главный экран (журнал звонков + фильтры) ----------
    private fun buildCallsTab(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Фильтры со скользящей плашкой
        val filterContainer = FrameLayout(this).apply { setPadding(dp(12), dp(10), dp(12), dp(6)) }
        val accent = Prefs.accentColor(this)
        val filterPill = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor((0x33000000.toInt()) or (accent and 0x00FFFFFF))
                setStroke(dp(1), 0x40FFFFFF)
            }
        }
        filterContainer.addView(filterPill, FrameLayout.LayoutParams(0, dp(36)).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val filters = listOf("Все", "Пропущенные", "Контакты")
        filters.forEachIndexed { i, label ->
            filterRow.addView(TextView(this).apply {
                text = label; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setOnClickListener {
                    callFilter = i
                    positionPill(filterPill, filterContainer, filters.size, i, animate = true)
                    showScreen(Screen.HOME)
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        filterContainer.addView(filterRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        filterContainer.post { positionPill(filterPill, filterContainer, filters.size, callFilter, animate = false) }
        root.addView(filterContainer)

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

    // ---------- Экран: настройки ----------
    private fun buildSettingsTab(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }
        val accent = Prefs.accentColor(this)

        fun sectionTitle(t: String) {
            root.addView(TextView(this).apply { text = t; textSize = 13f; setTextColor(0xFF8FA2FF.toInt()); setPadding(0, dp(14), 0, dp(6)) })
        }
        fun addCard(v: View) {
            root.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        fun button(t: String, action: (View) -> Unit) {
            addCard(card {
                addView(MaterialButton(this@MainActivity).apply {
                    text = t
                    isAllCaps = false
                    cornerRadius = dp(26)
                    backgroundTintList = ColorStateList.valueOf(accent)
                    setOnClickListener(action)
                })
            })
        }
        fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            addCard(card {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply { text = label; setTextColor(Color.WHITE); textSize = 14f },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(MaterialSwitch(this@MainActivity).apply {
                    isChecked = checked
                    setOnCheckedChangeListener { _: CompoundButton, c: Boolean -> onChange(c) }
                })
            })
        }

        root.addView(TextView(this).apply { text = "Котозвонок"; textSize = 26f; setTextColor(Color.WHITE) })

        sectionTitle("РАЗРЕШЕНИЯ")
        button("Доступ к звонкам и контактам") {
            requestPermissions(arrayOf(
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE
            ), 1)
        }
        button("Показ поверх других окон") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("Сделать звонилкой по умолчанию") {
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

        sectionTitle("ВИД ЗВОНКА")
        switchRow("Полноэкранный звонок", Prefs.isFullScreenEnabled(this)) { Prefs.setFullScreenEnabled(this, it) }
        switchRow("После ответа сворачивать в остров", Prefs.collapseToIslandOnAnswer(this)) { Prefs.setCollapseToIslandOnAnswer(this, it) }
        switchRow("Показывать котиков", Prefs.isCatsEnabled(this)) { Prefs.setCatsEnabled(this, it) }

        sectionTitle("РАЗМЕР МАЛЕНЬКОГО ОКОШКА")
        val sizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Маленький" to 0.85f, "Обычный" to 1.0f, "Крупный" to 1.25f).forEach { (label, scale) ->
            sizeRow.addView(MaterialButton(this).apply {
                text = label; isAllCaps = false; cornerRadius = dp(20)
                val selected = Prefs.overlayScale(this@MainActivity) == scale
                backgroundTintList = ColorStateList.valueOf(if (selected) accent else 0xFF2A2640.toInt())
                setOnClickListener { Prefs.setOverlayScale(this@MainActivity, scale); showScreen(Screen.SETTINGS) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        addCard(card { addView(sizeRow) })

        sectionTitle("РИНГТОН")
        button("Выбрать рингтон") {
            val current = Prefs.ringtoneUri(this)
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (current != null) Uri.parse(current) else null)
            }
            startActivityForResult(i, REQ_RINGTONE)
        }
        addCard(card {
            addView(TextView(this@MainActivity).apply {
                text = "Звонит рядом с обычным системным сигналом — отключить системный звук для стороннего приложения нельзя."
                textSize = 11f; setTextColor(0xFFA29FB8.toInt())
            })
        })

        sectionTitle("ЦВЕТОВАЯ ТЕМА")
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        Prefs.ACCENT_PRESETS.forEach { (label, color) ->
            colorRow.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(color)
                    if (color == accent) setStroke(dp(2), Color.WHITE)
                }
                setOnClickListener { Prefs.setAccentColor(this@MainActivity, color); recreate() }
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(6), 0, dp(6), 0) })
        }
        addCard(card { addView(colorRow) })

        sectionTitle("ШРИФТ")
        val fontRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("default" to "Обычный", "sans" to "Без засечек", "serif" to "С засечками", "mono" to "Моно").forEach { (key, label) ->
            fontRow.addView(MaterialButton(this).apply {
                text = label; isAllCaps = false; textSize = 11f; cornerRadius = dp(18)
                val selected = Prefs.fontFamily(this@MainActivity) == key
                backgroundTintList = ColorStateList.valueOf(if (selected) accent else 0xFF2A2640.toInt())
                setOnClickListener { Prefs.setFontFamily(this@MainActivity, key); recreate() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        addCard(card { addView(fontRow) })

        sectionTitle("СВОИ КАРТИНКИ")
        button("Добавить своего кота") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            startActivityForResult(i, REQ_ADD_CAT)
        }
        addCard(card {
            addView(TextView(this@MainActivity).apply {
                text = "Своих котов добавлено: ${Prefs.getCustomCats(this@MainActivity).size}"
                setTextColor(0xFFA29FB8.toInt()); textSize = 12f
            })
        })
        button("Удалить всех добавленных котов") {
            Prefs.clearCustomCats(this)
            showScreen(Screen.SETTINGS)
        }

        sectionTitle("ИКОНКИ КНОПОК И ВКЛАДОК")
        val iconLabels = mapOf(
            "answer" to "Иконка кнопки «Ответить»",
            "decline" to "Иконка кнопки «Отклонить»",
            "search" to "Иконка кнопки «Поиск»",
            "tab_home" to "Иконка вкладки «Главный экран»",
            "tab_dialer" to "Иконка вкладки «Клавиатура»"
        )
        iconLabels.forEach { (key, label) ->
            button(label) {
                pendingIconKey = key
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                startActivityForResult(i, REQ_PICK_ICON)
            }
        }
        button("Сбросить все иконки к стандартным") {
            Prefs.clearAllIcons(this)
            showScreen(Screen.SETTINGS)
        }

        sectionTitle("ЗНАЧОК ПРИЛОЖЕНИЯ (рабочий стол)")
        addCard(card {
            addView(TextView(this@MainActivity).apply {
                text = "Произвольное фото системой не поддерживается — можно выбрать один из трёх готовых вариантов."
                textSize = 12f; setTextColor(0xFFA29FB8.toInt())
            })
        })
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
        addCard(card { addView(iconRow) })

        sectionTitle("ПРОВЕРКА")
        button("Показать тестовый звонок") {
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
