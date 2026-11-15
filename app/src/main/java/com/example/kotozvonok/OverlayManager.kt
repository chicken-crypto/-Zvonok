package com.example.kotozvonok

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.Call
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// Компактное "стеклянное" окошко звонка: аватар+имя+номер слева, круглые кнопки справа.
object OverlayManager {
    private var outerView: View? = null
    private val animators = mutableListOf<ObjectAnimator>()

    private var subtitleView: TextView? = null
    private var decBtn: View? = null
    private var ansBtn: View? = null
    private var catsRow: View? = null
    private var buttonsRow: LinearLayout? = null
    private var timerHandler: Handler? = null
    private var callStartMs = 0L
    private var ringtone: Ringtone? = null

    private fun dp(context: Context, v: Int): Int {
        val scale = Prefs.overlayScale(context)
        return (v * scale * context.resources.displayMetrics.density).toInt()
    }

    private fun glassOval(context: Context, colorHex: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(colorHex)
        setStroke(dp(context, 1), 0x40FFFFFF)
    }

    private fun playRingtone(context: Context) {
        stopRingtone()
        try {
            val uriStr = Prefs.ringtoneUri(context)
            val uri = if (uriStr != null) Uri.parse(uriStr) else RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            if (uri != null) {
                ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
            }
        } catch (e: Exception) { }
    }

    private fun stopRingtone() {
        try { ringtone?.stop() } catch (e: Exception) { }
        ringtone = null
    }

    fun show(context: Context, name: String, number: String, call: Call?, isTest: Boolean = false) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        hide(app)
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fun dp(v: Int) = dp(app, v)
        val accent = Prefs.accentColor(app)

        playRingtone(app)

        val card = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                colors = intArrayOf(0xD92A2640.toInt(), 0xF01D1A2E.toInt())
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dp(1), 0x33FFFFFF)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            elevation = dp(10).toFloat()
        }

        val topRow = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = ImageView(app).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
            }
        }
        Avatars.randomInto(app, avatar)
        topRow.addView(avatar, LinearLayout.LayoutParams(dp(36), dp(36)))

        val textCol = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(app).apply {
            text = name; setTextColor(Color.WHITE); textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
        })
        val subtitle = TextView(app).apply {
            text = "Входящий · $number"; setTextColor(0xFFB3AFC9.toInt()); textSize = 10f
            maxLines = 1
        }
        subtitleView = subtitle
        textCol.addView(subtitle)
        topRow.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8); marginEnd = dp(6)
        })

        fun roundBtn(key: String, fallbackRes: Int, colorHex: Int, onClick: () -> Unit): View {
            val icon = Icons.iconView(app, key, fallbackRes, Color.WHITE)
            icon.setPadding(dp(7), dp(7), dp(7), dp(7))
            val box = FrameLayout(app).apply {
                background = glassOval(app, colorHex)
                isClickable = true
                setOnClickListener { onClick() }
            }
            if (Icons.isCustom(app, key)) {
                icon.clipToOutline = true
                icon.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
                }
                box.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            } else {
                box.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            return box
        }

        val btnRow = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL }
        buttonsRow = btnRow

        val searchBtn = roundBtn("search", R.drawable.ic_search, (0xCC000000.toInt() or (accent and 0x00FFFFFF))) {
            val url = "https://www.google.com/search?q=" + Uri.encode(number)
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        btnRow.addView(searchBtn, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(5) })

        val declineBtn = roundBtn("decline", R.drawable.ic_close, 0xE6E5484D.toInt()) {
            stopRingtone()
            if (isTest) {
                hide(app)
            } else {
                call?.reject(false, null)
                hide(app)
            }
        }
        decBtn = declineBtn
        btnRow.addView(declineBtn, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(5) })

        val answerBtn = roundBtn("answer", R.drawable.ic_check, 0xE62FBF71.toInt()) {
            stopRingtone()
            if (isTest) {
                onAnswered(app)
                TestCall.playWhiteNoiseThenEnd(5000) { hide(app) }
            } else {
                call?.answer(0)
            }
        }
        ansBtn = answerBtn
        btnRow.addView(answerBtn, LinearLayout.LayoutParams(dp(32), dp(32)))

        topRow.addView(btnRow)
        card.addView(topRow)

        if (Avatars.catsEnabled(app)) {
            val row = LinearLayout(app).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                gravity = Gravity.BOTTOM
            }
            catsRow = row
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)).apply { topMargin = dp(6) })
            for (i in 0 until 6) {
                val iv = ImageView(app).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
                    }
                }
                Avatars.randomInto(app, iv)
                row.addView(iv, LinearLayout.LayoutParams(0, dp(22), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                animators += ObjectAnimator.ofFloat(iv, "translationY", 0f, -dp(16).toFloat()).apply {
                    duration = 380L + i * 50L
                    startDelay = i * 90L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        }

        val outer = FrameLayout(app).apply { setPadding(dp(8), dp(4), dp(8), 0) }
        outer.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        wm.addView(outer, lp)
        outerView = outer
    }

    // Сразу показать компактный "остров" в режиме разговора (например, после сворачивания с полного экрана)
    fun showActive(context: Context, name: String, number: String) {
        show(context, name, number, CallManager.activeCall, isTest = false)
        onAnswered(context)
    }

    fun onAnswered(context: Context) {
        val app = context.applicationContext
        stopRingtone()
        val parent = buttonsRow ?: return
        decBtn?.visibility = View.GONE
        ansBtn?.visibility = View.GONE
        catsRow?.visibility = View.GONE

        val hangIcon = Icons.iconView(app, "__hangup_internal__", R.drawable.ic_close, Color.WHITE)
        hangIcon.setPadding(dp(app, 7), dp(app, 7), dp(app, 7), dp(app, 7))
        val hang = FrameLayout(app).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6E5484D.toInt())
                setStroke(dp(app, 1), 0x40FFFFFF)
            }
            addView(hangIcon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener {
                TestCall.stop()
                CallManager.activeCall?.disconnect()
                hide(app)
            }
        }
        parent.addView(hang, 0, LinearLayout.LayoutParams(dp(app, 32), dp(app, 32)))

        callStartMs = System.currentTimeMillis()
        val handler = Handler(Looper.getMainLooper())
        timerHandler = handler
        val tick = object : Runnable {
            override fun run() {
                val sec = (System.currentTimeMillis() - callStartMs) / 1000
                subtitleView?.text = "В разговоре · %02d:%02d".format(sec / 60, sec % 60)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tick)
    }

    fun hide(context: Context) {
        stopRingtone()
        TestCall.stop()
        animators.forEach { it.cancel() }
        animators.clear()
        timerHandler?.removeCallbacksAndMessages(null)
        timerHandler = null
        decBtn = null; ansBtn = null; subtitleView = null; catsRow = null; buttonsRow = null
        outerView?.let {
            val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        outerView = null
    }
}
