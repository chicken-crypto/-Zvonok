package com.example.kotozvonok

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView

// Строит значок для слота интерфейса (key): свою картинку, если выбрана в настройках,
// иначе — обычный эмодзи-символ по умолчанию.
object Icons {
    fun iconView(context: Context, key: String, fallbackEmoji: String, textSizeSp: Float, tint: Int = Color.WHITE): android.view.View {
        val customUri = Prefs.getIcon(context, key)
        if (customUri != null) {
            try {
                context.contentResolver.openInputStream(Uri.parse(customUri))?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        return ImageView(context).apply {
                            setImageBitmap(bmp)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    }
                }
            } catch (e: Exception) { /* откат на эмодзи ниже */ }
        }
        return TextView(context).apply {
            text = fallbackEmoji
            textSize = textSizeSp
            gravity = Gravity.CENTER
            setTextColor(tint)
        }
    }
}
