package com.example.kotozvonok

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

// Система отправляет сюда номер, когда кто-то нажимает "позвонить" где угодно в телефоне.
// Мы не делаем свой экран набора номера, а сразу передаём вызов дальше.
class DialerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent?.data?.schemeSpecificPart
        if (!number.isNullOrEmpty() &&
            checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        }
        finish()
    }
}
