package com.example.kotozvonok

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager

// Запасной путь на случай, если приложение НЕ назначено звонилкой по умолчанию.
// Если оно назначено — всем управляет CallUiService, и этот приёмник ничего не делает,
// чтобы не показывать второе, "ненастоящее" окно поверх настоящего.
class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (CallManager.activeCall != null) return // CallUiService уже всё показывает сам

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (number.isNullOrEmpty()) return
                val name = lookupName(ctx, number) ?: "Неизвестный"
                OverlayManager.show(ctx, name, number, null)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> OverlayManager.hide(ctx)
        }
    }

    private fun lookupName(ctx: Context, number: String): String? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }
}
