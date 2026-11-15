package com.example.kotozvonok

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService

class CallUiService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.activeCall = call

        val number = call.details.handle?.schemeSpecificPart ?: "Номер скрыт"
        val name = call.details.callerDisplayName ?: lookupContactName(number) ?: "Неизвестный"

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                when (state) {
                    Call.STATE_ACTIVE -> OverlayManager.onAnswered(applicationContext)
                    Call.STATE_DISCONNECTED -> {
                        OverlayManager.hide(applicationContext)
                        if (CallManager.activeCall == c) CallManager.activeCall = null
                    }
                }
            }
        })

        if (Prefs.isFullScreenEnabled(this)) {
            val i = Intent(this, CallScreenActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } else {
            OverlayManager.show(applicationContext, name, number, call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallManager.activeCall == call) CallManager.activeCall = null
        OverlayManager.hide(applicationContext)
    }

    private fun lookupContactName(number: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) { null }
}
