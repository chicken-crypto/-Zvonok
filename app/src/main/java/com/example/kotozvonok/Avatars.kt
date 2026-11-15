package com.example.kotozvonok

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.widget.ImageView

// Общий источник "котиков" (или заглушки, если коты выключены) для окошка звонка и полного экрана.
object Avatars {
    private val builtin = intArrayOf(
        R.drawable.cat1, R.drawable.cat2, R.drawable.cat3,
        R.drawable.cat4, R.drawable.cat5, R.drawable.cat6
    )

    fun catsEnabled(context: Context): Boolean = Prefs.isCatsEnabled(context)

    fun randomInto(context: Context, iv: ImageView) {
        if (!catsEnabled(context)) {
            iv.setImageDrawable(null)
            iv.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF4F6DF5.toInt())
            }
            return
        }
        iv.background = null
        val custom = Prefs.getCustomCats(context).toList()
        if (custom.isNotEmpty() && (0 until (custom.size + builtin.size)).random() >= builtin.size) {
            try {
                val uri = Uri.parse(custom.random())
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) { iv.setImageBitmap(bmp); return }
                }
            } catch (e: Exception) { /* откат на обычного кота ниже */ }
        }
        iv.setImageResource(builtin.random())
    }
}
