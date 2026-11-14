package com.example.kotozvonok

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class CallUiService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.activeCall = call
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) CallManager.activeCall = null
            }
        })
        val i = Intent(this, CallScreenActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallManager.activeCall == call) CallManager.activeCall = null
    }
}
