package com.example.kotozvonok

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var numberField: EditText
    private var currentTab = 0
    private val REQ_ADD_CAT = 4
    private val REQ_PICK_ICON = 5
    private var pendingIconKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF15131F.toInt())
        }

        content = FrameLayout(this)
        outer.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        outer.addView(buildBottomNav(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))

        setContentView(outer)
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        showTab(currentTab) // обновить данные (например список звонков) при возврате в приложение
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Нижнее меню ----------
    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1D1A2E.toInt())
        }
        val tabs = listOf(
            Triple("tab_dialer", "⌨️", "Набор"),
            Triple("tab_contacts", "👤", "Контакты"),
            Triple("tab_calls", "🕑", "Звонки"),
            Triple("tab_settings", "⚙️", "Настройки")
        )
        tabs.forEachIndexed { i, (key, emoji, label) ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener { showTab(i) }
            }
            val icon = Icons.iconView(this, key, emoji, 18f)
            if (icon is android.widget.ImageView) {
                icon.clipToOutline = true
                icon.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) { o.setOval(0, 0, v.width, v.height) }
                }
                col.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
            } else {
                col.addView(icon)
            }
            col.addView(TextView(this).apply { text = label; textSize = 10f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
            nav.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        return nav
    }

    private fun showTab(i: Int) {
        currentTab = i
        content.removeAllViews()
        val view = when (i) {
            0 -> buildDialerTab()
            1 -> buildContactsTab()
            2 -> buildCallsTab()
            else -> buildSettingsTab()
        }
        content.addView(view)
    }

    private fun card(block: LinearLayout.() -> Unit): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0xFF211E30.toInt()) }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        c.block()
        return c
    }

    // ---------- Вкладка: набор номера ----------
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
            isFocusable = false // набираем кнопками, как в обычной звонилке
        }
        root.addView(numberField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )
        rows.forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            row.forEach { digit ->
                r.addView(TextView(this).apply {
                    text = digit; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2A2640.toInt()) }
                    setOnClickListener { numberField.append(digit) }
                }, LinearLayout.LayoutParams(dp(66), dp(66)).apply { setMargins(dp(10), dp(8), dp(10), dp(8)) })
            }
            root.addView(r)
        }

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        actionRow.addView(TextView(this).apply {
            text = "⌫"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF3A3650.toInt()) }
            setOnClickListener {
                val t = numberField.text.toString()
                if (t.isNotEmpty()) numberField.setText(t.dropLast(1))
            }
        }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { setMargins(dp(10), 0, dp(10), 0) })
        actionRow.addView(TextView(this).apply {
            text = "✆"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2FBF71.toInt()) }
            setOnClickListener { placeCall(numberField.text.toString()) }
        }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(dp(10), 0, dp(10), 0) })
        root.addView(actionRow)

        return ScrollView(this).apply { addView(root) }
    }

    private fun placeCall(number: String) {
        if (number.isBlank()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
            return
        }
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    // ---------- Вкладка: контакты ----------
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
            while (it.moveToNext()) {
                list.add(C(it.getString(0) ?: "?", it.getString(1) ?: ""))
            }
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
            showTab(0)
            numberField.setText(list[pos].number)
        }
        return lv
    }

    // ---------- Вкладка: журнал звонков ----------
    private fun buildCallsTab(): View {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return permissionNeededView("Нужен доступ к журналу звонков") {
                requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), 1)
            }
        }
        data class H(val name: String, val number: String, val type: Int, val date: Long)
        val list = mutableListOf<H>()
        val cur = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            null, null, CallLog.Calls.DATE + " DESC"
        )
        cur?.use {
            var count = 0
            while (it.moveToNext() && count < 60) {
                list.add(H(it.getString(0) ?: it.getString(1) ?: "?", it.getString(1) ?: "", it.getInt(2), it.getLong(3)))
                count++
            }
        }
        if (list.isEmpty()) return emptyView("Звонков пока нет")

        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val lv = ListView(this)
        lv.setBackgroundColor(0xFF15131F.toInt())
        lv.divider = null
        lv.adapter = object : ArrayAdapter<H>(this, 0, list) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = list[position]
                val icon = when (item.type) {
                    CallLog.Calls.OUTGOING_TYPE -> "↗️"
                    CallLog.Calls.MISSED_TYPE -> "❌"
                    else -> "↙️"
                }
                val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
                row.addView(TextView(this@MainActivity).apply { text = icon; textSize = 16f }, LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT))
                val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                col.addView(TextView(this@MainActivity).apply { text = item.name; setTextColor(Color.WHITE); textSize = 15f })
                col.addView(TextView(this@MainActivity).apply { text = fmt.format(Date(item.date)); setTextColor(0xFFA29FB8.toInt()); textSize = 12f })
                row.addView(col)
                return row
            }
        }
        lv.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            showTab(0)
            numberField.setText(list[pos].number)
        }
        return lv
    }

    private fun permissionNeededView(msg: String, action: () -> Unit): View {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        l.addView(TextView(this).apply { text = msg; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER })
        l.addView(Button(this).apply {
            text = "Разрешить"; isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFF4F6DF5.toInt()) }
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        return l
    }

    private fun emptyView(msg: String): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER; orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply { text = msg; setTextColor(0xFFA29FB8.toInt()); textSize = 15f; gravity = Gravity.CENTER })
    }

    // ---------- Вкладка: настройки ----------
    private fun buildSettingsTab(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }

        fun sectionTitle(t: String) {
            root.addView(TextView(this).apply { text = t; textSize = 13f; setTextColor(0xFF8FA2FF.toInt()); setPadding(0, dp(14), 0, dp(6)) })
        }
        fun addCard(v: LinearLayout) {
            root.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        fun button(t: String, action: (View) -> Unit) {
            addCard(card {
                addView(Button(this@MainActivity).apply {
                    text = t; isAllCaps = false; setTextColor(Color.WHITE)
                    background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFF4F6DF5.toInt()) }
                    setOnClickListener(action)
                })
            })
        }

        root.addView(TextView(this).apply { text = "🐱 Котозвонок"; textSize = 26f; setTextColor(Color.WHITE) })

        sectionTitle("РАЗРЕШЕНИЯ")
        button("Доступ к звонкам и контактам") {
            requestPermissions(arrayOf(
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
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
        addCard(card {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply { text = "Полноэкранный звонок"; setTextColor(Color.WHITE); textSize = 14f },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(this@MainActivity).apply {
                isChecked = Prefs.isFullScreenEnabled(this@MainActivity)
                setOnCheckedChangeListener { _: CompoundButton, c: Boolean -> Prefs.setFullScreenEnabled(this@MainActivity, c) }
            })
        })
        addCard(card {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply { text = "Показывать котиков"; setTextColor(Color.WHITE); textSize = 14f },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(this@MainActivity).apply {
                isChecked = Prefs.isCatsEnabled(this@MainActivity)
                setOnCheckedChangeListener { _: CompoundButton, c: Boolean -> Prefs.setCatsEnabled(this@MainActivity, c) }
            })
        })
        button("➕ Добавить своего кота") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            startActivityForResult(i, REQ_ADD_CAT)
        }
        addCard(card {
            addView(TextView(this@MainActivity).apply {
                text = "Своих котов добавлено: ${Prefs.getCustomCats(this@MainActivity).size}"
                setTextColor(0xFFA29FB8.toInt()); textSize = 12f
            })
        })
        button("🗑 Удалить всех добавленных котов") {
            Prefs.clearCustomCats(this)
            showTab(3)
        }

        sectionTitle("ИКОНКИ КНОПОК И ВКЛАДОК")
        val iconLabels = mapOf(
            "answer" to "Иконка кнопки «Ответить»",
            "decline" to "Иконка кнопки «Отклонить»",
            "search" to "Иконка кнопки «Поиск»",
            "tab_dialer" to "Иконка вкладки «Набор»",
            "tab_contacts" to "Иконка вкладки «Контакты»",
            "tab_calls" to "Иконка вкладки «Звонки»",
            "tab_settings" to "Иконка вкладки «Настройки»"
        )
        iconLabels.forEach { (key, label) ->
            button(label) {
                pendingIconKey = key
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                startActivityForResult(i, REQ_PICK_ICON)
            }
        }
        button("↺ Сбросить все иконки к стандартным") {
            Prefs.clearAllIcons(this)
            showTab(3)
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
            col.addView(android.widget.ImageView(this).apply {
                setImageResource(res)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) { o.setOval(0, 0, v.width, v.height) }
                }
                setOnClickListener { setLauncherIcon(alias) }
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            col.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
            iconRow.addView(col)
        }
        addCard(card { addView(iconRow) })

        sectionTitle("ПРОВЕРКА")
        button("Показать тестовый звонок") {
            OverlayManager.show(this, "Тестовый Котик", "+7 900 000-00-00", null)
        }

        return ScrollView(this).apply { addView(root) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ADD_CAT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { /* некоторые источники не дают постоянный доступ, не страшно */ }
                Prefs.addCustomCat(this, uri.toString())
                showTab(3)
            }
        }
        if (requestCode == REQ_PICK_ICON && resultCode == RESULT_OK) {
            val key = pendingIconKey
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { }
                if (key != null) Prefs.setIcon(this, key, uri.toString())
                showTab(3)
            }
            pendingIconKey = null
        }
    }

    // Переключение между заранее заготовленными значками приложения на рабочем столе.
    // Произвольное фото системой здесь не поддерживается без пересборки APK.
    private fun setLauncherIcon(enableAlias: String) {
        val aliases = listOf("IconDefault", "IconCat1", "IconCat3")
        aliases.forEach { name ->
            val state = if (name == enableAlias) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try {
                packageManager.setComponentEnabledSetting(
                    android.content.ComponentName(packageName, "$packageName.$name"),
                    state,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) { }
        }
    }
}
