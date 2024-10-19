import 'dart:async';
import 'package:flutter/services.dart';

class SmsPlugin {
  static const MethodChannel _channel = MethodChannel('sms_plugin');
  static const EventChannel _eventChannel = EventChannel('sms_plugin_stream');

  static Future<void> sendSms(String phoneNumber, String message) async {
    await _channel.invokeMethod('sendSms', {
      'phoneNumber': phoneNumber,
      'message': message,
    });
  }

  static Future<Map<String, dynamic>?> getLatestSms(String phoneNumber) async {
    final sms = await _channel.invokeMethod('getLatestSms', {
      'phoneNumber': phoneNumber,
    });
    return sms != null ? Map<String, dynamic>.from(sms) : null;
  }

  static Stream<Map<String, dynamic>> onSmsReceived() {
    return _eventChannel.receiveBroadcastStream().map((event) {
      return Map<String, dynamic>.from(event);
    });
  }
}
