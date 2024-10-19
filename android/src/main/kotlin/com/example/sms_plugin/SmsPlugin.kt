package com.example.sms_plugin

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsPlugin: FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    private var smsReceiver: BroadcastReceiver? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val TAG = "SmsPlugin"
        var eventSink: EventChannel.EventSink? = null
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "sms_plugin")
        channel.setMethodCallHandler(this)

        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "sms_plugin_stream")
        eventChannel.setStreamHandler(this)

        context = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            "sendSms" -> {
                val phoneNumber = call.argument<String>("phoneNumber")
                val message = call.argument<String>("message")
                if (phoneNumber != null && message != null) {
                    sendSms(phoneNumber, message)
                    result.success(null)
                } else {
                    result.error("INVALID_ARGUMENT", "Phone number or message is null", null)
                }
            }
            "getLatestSms" -> {
                val phoneNumber = call.argument<String>("phoneNumber")
                if (phoneNumber != null) {
                    coroutineScope.launch {
                        val latestSms = getLatestSms(phoneNumber)
                        result.success(latestSms)
                    }
                } else {
                    result.error("INVALID_ARGUMENT", "Phone number is null", null)
                }
            }
            "getAllSms" -> {
                coroutineScope.launch {
                    val allSms = getAllSms()
                    result.success(allSms)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        try {
            SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
        }
    }

    private fun getLatestSms(phoneNumber: String): Map<String, Any>? {
        Log.d(TAG, "Attempting to fetch latest SMS for number: $phoneNumber")
        
        context.contentResolver.notifyChange(Telephony.Sms.CONTENT_URI, null)

        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        
        var selection = "${Telephony.Sms.ADDRESS} = ?"
        var selectionArgs = arrayOf(phoneNumber)
        
        var cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC LIMIT 5"
        )

        if (cursor?.count == 0) {
            Log.d(TAG, "No exact matches found. Trying partial match...")
            cursor?.close()
            
            selection = "${Telephony.Sms.ADDRESS} LIKE ?"
            selectionArgs = arrayOf("%${phoneNumber.takeLast(8)}%")
            
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT 5"
            )
        }

        return cursor?.use { 
            val smslist = mutableListOf<Map<String, Any>>()
            while (it.moveToNext()) {
                val indexId = it.getColumnIndex(Telephony.Sms._ID)
                val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
                val indexDate = it.getColumnIndex(Telephony.Sms.DATE)
                val indexType = it.getColumnIndex(Telephony.Sms.TYPE)
                
                val id = it.getLong(indexId)
                val address = it.getString(indexAddress)
                val body = it.getString(indexBody)
                val date = it.getLong(indexDate)
                val type = it.getInt(indexType)
                
                smslist.add(mapOf(
                    "id" to id,
                    "address" to address,
                    "body" to body,
                    "date" to date,
                    "type" to type
                ))
                
                Log.d(TAG, "Found SMS: ID=$id, Address=$address, Body=$body, Date=$date, Type=$type")
            }
            
            if (smslist.isEmpty()) {
                Log.d(TAG, "No SMS found for number: $phoneNumber")
                null
            } else {
                Log.d(TAG, "Returning latest SMS from ${smslist.size} messages found")
                smslist.first()
            }
        }
    }

    private fun getAllSms(): List<Map<String, Any>> {
        Log.d(TAG, "Querying all SMS messages")
        
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT 100"  // Fetch last 100 messages
        )

        return cursor?.use { 
            val smslist = mutableListOf<Map<String, Any>>()
            while (it.moveToNext()) {
                val indexId = it.getColumnIndex(Telephony.Sms._ID)
                val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
                val indexDate = it.getColumnIndex(Telephony.Sms.DATE)
                val indexType = it.getColumnIndex(Telephony.Sms.TYPE)
                
                smslist.add(mapOf(
                    "id" to it.getLong(indexId),
                    "address" to it.getString(indexAddress),
                    "body" to it.getString(indexBody),
                    "date" to it.getLong(indexDate),
                    "type" to it.getInt(indexType)
                ))
            }
            Log.d(TAG, "Found ${smslist.size} SMS messages")
            smslist
        } ?: emptyList()
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        smsReceiver = SmsBroadcastReceiver()
        val intentFilter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        context.registerReceiver(smsReceiver, intentFilter)
    }

    override fun onCancel(arguments: Any?) {
        smsReceiver?.let { context.unregisterReceiver(it) }
        smsReceiver = null
        eventSink = null
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}