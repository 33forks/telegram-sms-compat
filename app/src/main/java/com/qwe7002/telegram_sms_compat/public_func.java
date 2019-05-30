package com.qwe7002.telegram_sms_compat;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import okhttp3.dnsoverhttps.DnsOverHttps;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.MODE_PRIVATE;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;


class public_func {
    static final String log_tag = "telegram-sms";
    static final String network_error = "Send Message:No network connection";
    static final String broadcast_stop_service = "com.qwe7002.telegram_sms.stop_all";
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    static String get_send_phone_number(String phone_number) {
        return phone_number.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
    }

    static boolean check_network(Context context) {

        ConnectivityManager manager = (ConnectivityManager) context
                .getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        NetworkInfo networkinfo = manager.getActiveNetworkInfo();
        return networkinfo != null && networkinfo.isConnected();
    }

    static String get_url(String token, String func) {
        return "https://api.telegram.org/bot" + token + "/" + func;
    }

    static OkHttpClient get_okhttp_obj(boolean doh_switch) {
        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_0, TlsVersion.TLS_1_1, TlsVersion.TLS_1_3)
                .cipherSuites(
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                        CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                        CipherSuite.TLS_AES_256_GCM_SHA384,
                        CipherSuite.TLS_AES_128_GCM_SHA256
                )
                .build();
        OkHttpClient.Builder okhttp = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectionSpecs(Collections.singletonList(spec))
                .retryOnConnectionFailure(true);
        if (doh_switch) {
            okhttp.dns(new DnsOverHttps.Builder().client(new OkHttpClient.Builder().retryOnConnectionFailure(true).connectionSpecs(Collections.singletonList(spec)).build())
                    .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                    .bootstrapDnsHosts(getByIp("1.1.1.1"), getByIp("2606:4700:4700::1111"), getByIp("185.222.222.222"), getByIp("2a09::"))
                    .includeIPv6(true)
                    .build());
        }
        return okhttp.build();
    }

    private static InetAddress getByIp(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            // unlikely
            throw new RuntimeException(e);
        }
    }
    static boolean is_numeric(String str) {
        for (int i = str.length(); --i >= 0; ) {
            char c = str.charAt(i);
            if (c == '+') {
                continue; //Allowed characters +
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    static String get_network_type(Context context) {
        String net_type = "Unknown";
        ConnectivityManager connect_manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connect_manager != null;
        NetworkInfo network_info = connect_manager.getActiveNetworkInfo();
        if (network_info == null) {
            return net_type;
        }
        switch (network_info.getType()) {
            case ConnectivityManager.TYPE_WIFI:
                net_type = "WIFI";
                break;
            case ConnectivityManager.TYPE_MOBILE:
                switch (network_info.getSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        net_type = "LTE/4G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                    case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                        net_type = "3G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        net_type = "2G";
                        break;
                }
        }
        return net_type;
    }

    static void send_sms(Context context, String send_to, String content) {
        if (!is_numeric(send_to)) {
            write_log(context, "[" + send_to + "] is an illegal phone number");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        android.telephony.SmsManager sms_manager;
        sms_manager = android.telephony.SmsManager.getDefault();
        String display_to_address = send_to;
        String display_to_name = public_func.get_contact_name(context, display_to_address);
        if (display_to_name != null) {
            display_to_address = display_to_name + "(" + send_to + ")";
        }
        String send_content = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + display_to_address + "\n" + context.getString(R.string.content) + content;
        String message_id = "-1";
        request_body.text = send_content + "\n" + context.getString(R.string.status) + context.getString(R.string.sending);
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        try {
            Response response = call.execute();
            if (response.code() != 200 || response.body() == null) {
                throw new IOException(String.valueOf(response.code()));
            }
            message_id = get_message_id(response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
            public_func.write_log(context, "failed to send message:" + e.getMessage());
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        ArrayList<PendingIntent> send_receiver_list = new ArrayList<>();
        IntentFilter filter = new IntentFilter("send_sms");
        BroadcastReceiver receiver = new sms_send_receiver();
        context.getApplicationContext().registerReceiver(receiver, filter);
        Intent sent_intent = new Intent("send_sms");
        sent_intent.putExtra("message_id", message_id);
        sent_intent.putExtra("message_text", send_content);
        PendingIntent sentIntent = PendingIntent.getBroadcast(context, 0, sent_intent, PendingIntent.FLAG_CANCEL_CURRENT);
        send_receiver_list.add(sentIntent);
        sms_manager.sendMultipartTextMessage(send_to, null, divideContents, send_receiver_list, null);
    }

    static void send_fallback_sms(Context context, String content) {
        if (checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("fallback_sms", false)) {
            return;
        }
        android.telephony.SmsManager sms_manager = android.telephony.SmsManager.getDefault();
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        sms_manager.sendMultipartTextMessage(sharedPreferences.getString("trusted_phone_number", null), null, divideContents, null, null);

    }

    static String get_message_id(String result) {
        JsonObject result_obj = new JsonParser().parse(result).getAsJsonObject().get("result").getAsJsonObject();
        return result_obj.get("message_id").getAsString();
    }

    static Notification get_notification_obj(Context context, String notification_name) {
        Notification.Builder result_builder = new Notification.Builder(context)
                .setAutoCancel(false)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setTicker(context.getString(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notification_name + context.getString(R.string.service_is_running));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return result_builder.setPriority(Notification.PRIORITY_MIN).build();
        }
        return result_builder.getNotification();
    }

    static void stop_all_service(Context context) {
        Intent intent = new Intent(broadcast_stop_service);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void start_service(Context context, Boolean battery_switch, Boolean chat_command_switch) {
        Intent battery_service = new Intent(context, battery_monitoring_service.class);
        Intent chat_long_polling_service = new Intent(context, chat_long_polling_service.class);

        if (battery_switch) {
            context.startService(battery_service);
        }
        if (chat_command_switch) {
            context.startService(chat_long_polling_service);
        }

    }


    static String get_contact_name(Context context, String phone_number) {
        String contact_name = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone_number));
            String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String cursor_name = cursor.getString(0);
                    if (!cursor_name.isEmpty())
                        contact_name = cursor_name;
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return contact_name;
    }

    static void write_log(Context context, String log) {
        Log.i(public_func.log_tag, log);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);
        Date ts = new Date(System.currentTimeMillis());
        append_file(context, "error.log", "\n" + simpleDateFormat.format(ts) + " " + log);
    }

    static String read_log(Context context) {
        return read_file(context, "error.log");
    }

    static void add_message_list(Context context, String message_id, String phone) {
        String message_list_raw = public_func.read_file(context, "message.json");
        if (message_list_raw.length() == 0) {
            message_list_raw = "{}";
        }
        JsonObject message_list_obj = new JsonParser().parse(message_list_raw).getAsJsonObject();
        JsonObject object = new JsonObject();
        object.addProperty("phone", phone);
        message_list_obj.add(message_id, object);
        public_func.write_file(context, "message.json", new Gson().toJson(message_list_obj));
    }

    static void append_file(Context context, String file_name, String write_string) {
        private_write_file(context, file_name, write_string, Context.MODE_APPEND);
    }

    static void write_file(Context context, String file_name, String write_string) {
        private_write_file(context, file_name, write_string, Context.MODE_PRIVATE);

    }

    private static void private_write_file(Context context, String file_name, String write_string, int mode) {
        try {
            FileOutputStream file_stream = context.openFileOutput(file_name, mode);
            byte[] bytes = write_string.getBytes();
            file_stream.write(bytes);
            file_stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String read_file(Context context, String file_name) {
        String result = "";
        try {
            FileInputStream file_stream = context.openFileInput(file_name);
            int length = file_stream.available();
            byte[] buffer = new byte[length];
            file_stream.read(buffer);
            result = new String(buffer);
            file_stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    static String get_verification_code(String body){
        code_aux_lib parser = new code_aux_lib();
        return parser.find(body);
    }
}