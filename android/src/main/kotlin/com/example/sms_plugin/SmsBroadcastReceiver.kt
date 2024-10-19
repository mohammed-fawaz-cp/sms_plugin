package com.example.sms_plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages: Array<SmsMessage>? = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages?.forEach { smsMessage ->
                val messageMap = mapOf(
                    "address" to (smsMessage.originatingAddress ?: ""),
                    "body" to (smsMessage.messageBody ?: ""),
                    "date" to smsMessage.timestampMillis
                )

                SmsPlugin.eventSink?.success(messageMap)
            }
        }
    }
}