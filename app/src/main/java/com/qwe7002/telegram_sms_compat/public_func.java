package com.qwe7002.telegram_sms_compat;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.github.sumimakito.codeauxlib.CodeauxLibPortable;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.squareup.okhttp3.extend.Tls12SocketFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import okhttp3.Call;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.dnsoverhttps.DnsOverHttps;


class public_func {
    static final String network_error = "Send Message:No network connection.";
    static final String broadcast_stop_service = "com.qwe7002.telegram_sms.stop_all";
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final CodeauxLibPortable parser = new CodeauxLibPortable();
    static String get_send_phone_number(String phone_number) {
        return phone_number.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
    }

    static boolean check_network_status(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        assert manager != null;
        NetworkInfo networkinfo = manager.getActiveNetworkInfo();
        return networkinfo != null && networkinfo.isConnected();
    }

    static String get_url(String token, String func) {
        return "https://api.telegram.org/bot" + token + "/" + func;
    }

    static OkHttpClient get_okhttp_obj(boolean doh_switch) {
        ConnectionSpec spec;
        OkHttpClient.Builder okhttp = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .cipherSuites(
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA
                    )
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build();
            SSLContext sc = null;
            try {
                sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            }
            assert sc != null;
            //noinspection deprecation
            okhttp.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));
        } else {
            spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_0, TlsVersion.TLS_1_1)
                    .cipherSuites(
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
                    ).build();

        }
        List<ConnectionSpec> specs = new ArrayList<>();
        specs.add(spec);
        specs.add(ConnectionSpec.COMPATIBLE_TLS);
        specs.add(ConnectionSpec.CLEARTEXT);
        okhttp.connectionSpecs(specs);
        if (doh_switch) {
            okhttp.dns(new DnsOverHttps.Builder().client(okhttp.build())
                    .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                    .bootstrapDnsHosts(getByIp("1.0.0.1"), getByIp("9.9.9.9"), getByIp("185.222.222.222"), getByIp("2606:4700:4700::1001"), getByIp("2620:fe::fe"), getByIp("2a09::"))
                    .includeIPv6(true)
                    .build());
        }
        return okhttp.build();
    }

    private static InetAddress getByIp(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static boolean is_phone_number(String str) {
        for (int i = str.length(); --i >= 0; ) {
            char c = str.charAt(i);
            if (c == '+') {
                continue;
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
                    case TelephonyManager.NETWORK_TYPE_NR:
                        net_type = "5G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        net_type = "LTE/4G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                    case TelephonyManager.NETWORK_TYPE_UMTS:
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
                break;
        }
        return net_type;
    }

    static void send_sms(Context context, String send_to, String content) {
        if (!is_phone_number(send_to)) {
            write_log(context, "[" + send_to + "] is an illegal phone number.");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", android.content.Context.MODE_PRIVATE);
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        android.telephony.SmsManager sms_manager;
        sms_manager = android.telephony.SmsManager.getDefault();
        String send_content = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + send_to + "\n" + context.getString(R.string.content) + content;
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
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", android.content.Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("fallback_sms", false)) {
            Log.d("send_fallback_sms", "send_fallback_sms: No fallback number.");
            return;
        }
        android.telephony.SmsManager sms_manager = android.telephony.SmsManager.getDefault();
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        String trust_number = sharedPreferences.getString("trusted_phone_number", null);
        assert trust_number != null;
        sms_manager.sendMultipartTextMessage(trust_number, null, divideContents, null, null);
    }

    static String get_message_id(String result) {
        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject().get("result").getAsJsonObject();
        return result_obj.get("message_id").getAsString();
    }

    static Notification get_notification_obj(Context context, String notification_name) {
        Notification.Builder result_builder = new Notification.Builder(context)
                .setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_stat)
                .setOngoing(true)
                .setTicker(context.getString(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notification_name + context.getString(R.string.service_is_running));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            //noinspection deprecation
            return result_builder.setPriority(Notification.PRIORITY_MIN).build();
        }
        //noinspection deprecation
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
        Intent battery_service = new Intent(context, com.qwe7002.telegram_sms_compat.battery_service.class);
        Intent chat_long_polling_service = new Intent(context, chat_command_service.class);

        if (battery_switch) {
            context.startService(battery_service);
        }
        if (chat_command_switch) {
            context.startService(chat_long_polling_service);
        }

    }

    static String get_sim_name(Context context) {
        String result = "Unknown";
        TelephonyManager telephonyManager = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
        assert telephonyManager != null;
        if (!telephonyManager.getSimOperatorName().equals("")) {
            result = telephonyManager.getSimOperatorName();
        }
        if (!telephonyManager.getNetworkOperatorName().equals("")) {
            result = telephonyManager.getNetworkOperatorName();
        }
        return result;
    }


    static void write_log(Context context, String log) {
        Log.i("write_log", log);
        int new_file_mode = Context.MODE_APPEND;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.UK);
        Date ts = new Date(System.currentTimeMillis());
        String write_string = "\n" + simpleDateFormat.format(ts) + " " + log;
        write_file(context, "error.log", write_string, new_file_mode);
    }

    static String read_log(Context context, int line) {
        String result = "\n" + context.getString(R.string.no_logs);
        String log_content = public_func.read_file_last_line(context, "error.log", line);
        if (!log_content.isEmpty()) {
            result = log_content;
        }
        return result;
    }
    @SuppressWarnings("WeakerAccess")
    static String read_file_last_line(Context context, @SuppressWarnings("SameParameterValue") String file, int line) {
        StringBuilder builder = new StringBuilder();
        FileInputStream file_stream = null;
        FileChannel channel = null;
        try {
            file_stream = context.openFileInput(file);
            channel = file_stream.getChannel();
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.position((int) channel.size());
            int count = 0;
            for (long i = channel.size() - 1; i >= 0; i--) {
                char c = (char) buffer.get((int) i);
                builder.insert(0, c);
                if (c == '\n') {
                    if (count == (line - 1)) {
                        break;
                    }
                    count++;
                }
            }
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        } finally {
            try {
                if (file_stream != null) {
                    file_stream.close();
                }
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void write_file(Context context, String file_name, String write_string, int mode) {
        FileOutputStream file_stream = null;
        try {
            file_stream = context.openFileOutput(file_name, mode);
            byte[] bytes = write_string.getBytes();
            file_stream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file_stream != null) {
                try {
                    file_stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    static String read_file(Context context, @SuppressWarnings("SameParameterValue") String file_name) {
        String result = "";
        FileInputStream file_stream = null;
        try {
            file_stream = context.openFileInput(file_name);
            int length = file_stream.available();
            byte[] buffer = new byte[length];
            //noinspection ResultOfMethodCallIgnored
            file_stream.read(buffer);
            result = new String(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file_stream != null) {
                try {
                    file_stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    static String get_verification_code(String body){
        return parser.find(body);
    }

    static void add_message_list(Context context, String message_id, String phone) {
        String message_list_raw = public_func.read_file(context, "message.json");
        if (message_list_raw.length() == 0) {
            message_list_raw = "{}";
        }
        JsonObject message_list_obj = JsonParser.parseString(message_list_raw).getAsJsonObject();
        JsonObject object = new JsonObject();
        object.addProperty("phone", phone);
        message_list_obj.add(message_id, object);
        public_func.write_file(context, "message.json", new Gson().toJson(message_list_obj), android.content.Context.MODE_PRIVATE);
    }

}
