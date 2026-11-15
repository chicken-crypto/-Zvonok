package com.example.kotozvonok

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// Это и есть замена стандартного экрана входящего звонка.
// Запускает его CallUiService, когда звонок появляется в системе.
class CallScreenActivity : Activity() {
    private val animators = mutableListOf<ObjectAnimator>()
    private var callback: Call.Callback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Показываем поверх экрана блокировки и включаем экран, как настоящий звонок
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val call = CallManager.activeCall
        if (call == null) { finish(); return }

        val number = call.details.handle?.schemeSpecificPart ?: "Номер скрыт"
        val name = call.details.callerDisplayName ?: lookupContactName(number) ?: "Неизвестный"

        buildUi(name, number)

        val cb = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) finish()
            }
        }
        callback = cb
        call.registerCallback(cb)
    }

    private fun lookupContactName(number: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) { null }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun buildUi(name: String, number: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1D1B2E.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(60), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "Входящий звонок"; setTextColor(0xFFA29FB8.toInt()); textSize = 14f; gravity = Gravity.CENTER
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = name; setTextColor(Color.WHITE); textSize = 28f; gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(4))
        }, matchWrap())
        // Номер - мелким шрифтом, как просили
        root.addView(TextView(this).apply {
            text = number; setTextColor(0xFF8FA2FF.toInt()); textSize = 13f; gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        if (Avatars.catsEnabled(this)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; gravity = Gravity.BOTTOM }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)))
            for (i in 0 until 6) {
                val iv = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
                    }
                }
                Avatars.randomInto(this, iv)
                row.addView(iv, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
                animators += ObjectAnimator.ofFloat(iv, "translationY", 0f, -dp(40).toFloat()).apply {
                    duration = 380L + i * 50L
                    startDelay = i * 90L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        }

        // Кнопка поиска номера в браузере - прямо на экране звонка
        root.addView(Button(this).apply {
            text = "🔍 Найти номер в браузере"
            setOnClickListener {
                val url = "https://www.google.com/search?q=" + Uri.encode(number)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, matchWrap().apply { topMargin = dp(16) })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(8))
        }
        buttons.addView(Button(this).apply {
            text = "✕ Сбросить"
            setOnClickListener { CallManager.activeCall?.reject(false, null); finish() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttons.addView(Button(this).apply {
            text = "✆ Ответить"
            setOnClickListener { CallManager.activeCall?.answer(0); finish() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        root.addView(buttons, matchWrap())

        setContentView(root)
    }

    override fun onDestroy() {
        super.onDestroy()
        animators.forEach { it.cancel() }
        callback?.let { CallManager.activeCall?.unregisterCallback(it) }
    }
}
