package com.example.kotozvonok

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.widget.ImageView

// Строит иконку для слота интерфейса (key): свою картинку, если выбрана в настройках,
// иначе — стандартную векторную иконку (никаких эмодзи).
object Icons {
    fun isCustom(context: Context, key: String): Boolean = Prefs.getIcon(context, key) != null

    fun iconView(context: Context, key: String, fallbackRes: Int, tint: Int = Color.WHITE): ImageView {
        val iv = ImageView(context)
        val customUri = Prefs.getIcon(context, key)
        if (customUri != null) {
            try {
                context.contentResolver.openInputStream(Uri.parse(customUri))?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        iv.setImageBitmap(bmp)
                        iv.scaleType = ImageView.ScaleType.CENTER_CROP
                        return iv
                    }
                }
            } catch (e: Exception) { /* откат на стандартную иконку ниже */ }
        }
        iv.setImageResource(fallbackRes)
        iv.setColorFilter(tint)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        return iv
    }
}
