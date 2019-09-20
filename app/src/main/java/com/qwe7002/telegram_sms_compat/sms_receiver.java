package com.qwe7002.telegram_sms_compat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class sms_receiver extends BroadcastReceiver {
    public void onReceive(final Context context, Intent intent) {
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.d(public_func.log_tag, "reject: Error Extras.");
            return;
        }
        boolean is_default = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            is_default = Telephony.Sms.getDefaultSmsPackage(context).equals(context.getPackageName());
        }
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction()) && is_default) {
            //When it is the default application, it will receive two broadcasts.
            Log.i(public_func.log_tag, "reject: android.provider.Telephony.SMS_RECEIVE.");
            return;
        }
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(public_func.log_tag, "Uninitialized, SMS receiver is deactivated.");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        Object[] pdus = (Object[]) bundle.get("pdus");
        assert pdus != null;
        final SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < pdus.length; i++) {
            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
        }

        if (messages.length == 0) {
            public_func.write_log(context, "Message length is equal to 0.");
            return;
        }

        StringBuilder message_body = new StringBuilder();
        for (SmsMessage item : messages) {
            message_body.append(item.getMessageBody());
        }
        final String message_address = messages[0].getOriginatingAddress();
        assert message_address != null;
        final boolean trust_phone = message_address.contains(sharedPreferences.getString("trusted_phone_number", "trusted_phone_is_none"));
        final message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        String message_body_html = message_body.toString();
        if (sharedPreferences.getBoolean("verification_code", false) && !trust_phone) {
            String verification = public_func.get_verification_code(message_body.toString());
            if (verification != null) {
                request_body.parse_mode = "html";
                message_body_html = message_body.toString()
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("&", "&amp;")
                        .replace(verification, "<code>" + verification + "</code>");
            }
        }
        String message_head = "[" + context.getString(R.string.receive_sms_head) + "]" + "\n" + context.getString(R.string.from) + message_address + "\n" + context.getString(R.string.content);
        String raw_request_body_text = message_head + message_body;
        request_body.text = message_head + message_body_html;

        if (trust_phone) {
            String[] msg_send_list = message_body.toString().split("\n");
            String msg_send_to = public_func.get_send_phone_number(msg_send_list[0]);
            if (message_body.toString().equals("restart-service")) {
                new Thread(() -> {
                    public_func.stop_all_service(context.getApplicationContext());
                    public_func.start_service(context.getApplicationContext(), sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
                });
                request_body.text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.restart_service);
            }
            if (public_func.is_phone_number(msg_send_to) && msg_send_list.length != 1) {
                StringBuilder msg_send_content = new StringBuilder();
                for (int i = 1; i < msg_send_list.length; i++) {
                    if (msg_send_list.length != 2 && i != 1) {
                        msg_send_content.append("\n");
                    }
                    msg_send_content.append(msg_send_list[i]);
                }
                new Thread(() -> public_func.send_sms(context, msg_send_to, msg_send_content.toString())).start();
                return;
            }
        }

        if (public_func.check_network_status(context)) {
            public_func.write_log(context, public_func.network_error);
            public_func.send_fallback_sms(context, request_body.text);
            return;
        }
        String request_body_json = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(public_func.JSON, request_body_json);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS forward failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String error_message = error_head + e.getMessage();
                public_func.write_log(context, error_message);
                public_func.send_fallback_sms(context, raw_request_body_text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                if (response.code() != 200) {
                    String error_message = error_head + response.code() + " " + response.body().string();
                    public_func.write_log(context, error_message);
                    public_func.send_fallback_sms(context, raw_request_body_text);
                } else {
                    if (public_func.is_phone_number(message_address)) {
                        public_func.add_message_list(context, public_func.get_message_id(response.body().string()), message_address);
                    }
                }
            }
        });
    }

}