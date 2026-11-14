package com.example.kotozvonok

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.ContactsContract
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

object OverlayManager {
    private var view: View? = null
    private val animators = mutableListOf<ObjectAnimator>()
    private val cats = intArrayOf(
        R.drawable.cat1, R.drawable.cat2, R.drawable.cat3,
        R.drawable.cat4, R.drawable.cat5, R.drawable.cat6
    )

    fun show(context: Context, name: String, number: String) {
        val app = context.applicationContext
        if (!android.provider.Settings.canDrawOverlays(app)) return
        hide(app)
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = app.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        fun pill(colorHex: Int): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            setColor(colorHex)
        }

        // Карточка-плашка
        val card = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xFF221F30.toInt())
            }
            setPadding(dp(16), dp(16), dp(16), dp(14))
            elevation = dp(10).toFloat()
        }

        // Верхняя строка: аватар + имя/подпись
        val topRow = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = ImageView(app).apply {
            setImageResource(pickAvatarCat())
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
            }
        }
        topRow.addView(avatar, LinearLayout.LayoutParams(dp(52), dp(52)))

        val textCol = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(app).apply { text = name; setTextColor(Color.WHITE); textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        textCol.addView(TextView(app).apply { text = "Телефон · Входящий вызов"; setTextColor(0xFFA29FB8.toInt()); textSize = 13f })
        textCol.addView(TextView(app).apply { text = number; setTextColor(0xFF8FA2FF.toInt()); textSize = 12f })
        topRow.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        card.addView(topRow)

        // Коты
        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            gravity = Gravity.BOTTOM
        }
        card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10) })
        cats.forEachIndexed { i, res ->
            val iv = ImageView(app).apply {
                setImageResource(res)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
                }
            }
            row.addView(iv, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            animators += ObjectAnimator.ofFloat(iv, "translationY", 0f, -dp(32).toFloat()).apply {
                duration = 380L + i * 50L
                startDelay = i * 90L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        // Кнопка поиска
        card.addView(Button(app).apply {
            text = "🔍 Найти номер в браузере"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFF4F6DF5.toInt()) }
            setOnClickListener {
                val url = "https://www.google.com/search?q=" + Uri.encode(number)
                app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                hide(app)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        // Отклонить / Ответить, как на скриншоте
        val buttons = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        buttons.addView(Button(app).apply {
            text = "✆  ОТКЛОНИТЬ"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = pill(0xFFE5484D.toInt())
            setOnClickListener { hide(app) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        buttons.addView(Button(app).apply {
            text = "✆  ОТВЕТИТЬ"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = pill(0xFF2FBF71.toInt())
            setOnClickListener { hide(app) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) })
        card.addView(buttons)

        val outer = LinearLayout(app).apply { setPadding(dp(12), dp(40), dp(12), 0) }
        outer.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        wm.addView(outer, lp)
        view = outer
    }

    private fun pickAvatarCat(): Int = cats[(0 until cats.size).random()]

    fun hide(context: Context) {
        animators.forEach { it.cancel() }
        animators.clear()
        view?.let {
            val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        view = null
    }
}
