package com.dieam.reactnativepushnotification.modules;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.AsyncTask;
import android.content.res.Configuration;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import android.text.format.DateFormat;

import javax.net.ssl.HttpsURLConnection;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;

import org.json.JSONObject;  
import org.json.JSONException;  
import org.json.JSONArray;  

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.RequestBody;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNPushNotificationPublisher extends BroadcastReceiver {
    final static String NOTIFICATION_ID = "notificationId";

    @Override
    public void onReceive(final Context context, Intent intent) {
        int id = intent.getIntExtra(NOTIFICATION_ID, 0);
        long currentTime = System.currentTimeMillis();

        Log.i(LOG_TAG, "NotificationPublisher: Prepare To Publish: " + id + ", Now Time: " + currentTime);

        Bundle bundle = intent.getExtras();

        Log.v(LOG_TAG, "onMessageReceived: " + bundle);
        monitorServer(context, bundle);
    }

    private void handleLocalNotification(Context context, Bundle bundle) {

        // If notification ID is not provided by the user for push notification, generate one at random
        
        SecureRandom randomNumberGenerator = new SecureRandom();
        bundle.putString("id", String.valueOf(randomNumberGenerator.nextInt()));

        Application applicationContext = (Application) context.getApplicationContext();
        RNPushNotificationHelper pushNotificationHelper = new RNPushNotificationHelper(applicationContext);
        
        Log.v(LOG_TAG, "sendNotification: " + bundle);

        pushNotificationHelper.sendToNotificationCentre(bundle);
    }
     
    public static String getMyPrettyDate(long neededTimeMilis) {
        Calendar nowTime = Calendar.getInstance();
        Calendar neededTime = Calendar.getInstance();
        neededTime.setTimeInMillis(neededTimeMilis);
    
        if ((neededTime.get(Calendar.YEAR) == nowTime.get(Calendar.YEAR))) {
    
            if ((neededTime.get(Calendar.MONTH) == nowTime.get(Calendar.MONTH))) {
    
                if (neededTime.get(Calendar.DATE) - nowTime.get(Calendar.DATE) == 1) {
                    //here return like "Tomorrow at 12:00"
                    return "Tomorrow at " + DateFormat.format("HH:mm", neededTime);
    
                } else if (nowTime.get(Calendar.DATE) == neededTime.get(Calendar.DATE)) {
                    //here return like "Today at 12:00"
                    return "Today at " + DateFormat.format("HH:mm", neededTime);
    
                } else if (nowTime.get(Calendar.DATE) - neededTime.get(Calendar.DATE) == 1) {
                    //here return like "Yesterday at 12:00"
                    return "Yesterday at " + DateFormat.format("HH:mm", neededTime);
    
                } else {
                    //here return like "May 31, 12:00"
                    return DateFormat.format("MMMM d, HH:mm", neededTime).toString();
                }
    
            } else {
                //here return like "May 31, 12:00"
                return DateFormat.format("MMMM d, HH:mm", neededTime).toString();
            }
    
        } else {
            //here return like "May 31 2010, 12:00" - it's a different year we need to show it
            return DateFormat.format("MMMM dd yyyy, HH:mm", neededTime).toString();
        }
    }
    
    public void monitorServer(Context context, Bundle bundle) {
        String serverurl = bundle.getString("serverurl");
        String userToken = bundle.getString("userToken");
        
        String messageString = "";

        OkHttpClient client = new OkHttpClient().newBuilder()
            .build();
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, "f=json");
        Request request = new Request.Builder()
            .url(serverurl + "/api/users/self/changes")
            .method("POST", body)
            .addHeader("Authorization", "Bearer " + userToken)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build();
        
        AsyncTask<Void, Void, String> asyncTask = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        Log.v(LOG_TAG, "requestnot successful: ");
                        return null;
                    }
                    return response.body().string();
                } catch (Exception e) {
                    Log.v(LOG_TAG, "doInbackground: " + e.toString());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String s) {
                Log.v(LOG_TAG, "getchangesresponse: " + s);
                if(s != null)
                {
                    try {
                        boolean isDarkThemeOn = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)  == Configuration.UI_MODE_NIGHT_YES;
                        
                        JSONObject jsonObject = new JSONObject(s);
                        if(jsonObject.has("notifications")) {
                            String notifications = jsonObject.getString("notifications");
                            JSONArray notifArr = new JSONArray(notifications);
                            for (int i = 0; i < notifArr.length(); i++) {
                                JSONObject eventObj = new JSONObject(notifArr.getString(i));
                                String type = eventObj.getString("type");
                                Log.v(LOG_TAG, "in_first_for: " + type);
                                JSONObject notification = new JSONObject(eventObj.getString("notification"));
                                String msg;
                                String title;
                                String red = "red";
                                String black = "black";
                                String priority = notification.getString("priority");
                                if(type.equals("user_event")){
                                    msg = notification.getString("message");
                                    title =  "<p><span style='color:" + (priority.equals("true") ? red : black) + "'>New Message</span>    <span style='color:lightgray'>" + getMyPrettyDate(notification.getLong("queue_time")* 1000) + "</span></p>";
                                    bundle.putString("actionType", "user_event");
                                }
                                else {
                                    String cleared = notification.getString("cleared");
                                    String ncAlarm =  cleared.equals("true")?"Alarm Reset":"New Alarm";
                                    msg = notification.getString("name") + ": " + (cleared.equals("true")?notification.getString("reset_text"):notification.getString("active_text"));
                                    title =  "<p><span style='color:"+ (priority.equals("true") ? red : black) + "'>" + ncAlarm + "</span>    <span style='color:lightgray'>" + getMyPrettyDate((notification.getLong("active_time") + (cleared.equals("true")?notification.getLong("active_duration"):0))*1000) + "</span></p>";
                                    bundle.putString("actionType", "alarm");
                                }
                                String objectId = notification.getString("id");
                                bundle.putString("objectId", objectId);
                                String hasPermission = bundle.getString("hasPermission");
                                bundle.putString("showFlag", hasPermission.equals("true") ? "true" : "false");
                                bundle.putString("soundName", 
                                    priority.equals("true") ? bundle.getString("soundNameHigh") : bundle.getString("soundNameNormal"));
                                if(!bundle.getBoolean("playSound")){
                                    bundle.putString("soundName", "");
                                }
                                bundle.putString("channelId", priority.equals("true") ? "fusion-high-channel-0113" : "fusion-normal-channel-0113");
                                if(priority.equals("true")){
                                    bundle.putString("smallIcon", isDarkThemeOn?"white_priority": "black_priority");
                                }
                                else{
                                    bundle.putString("smallIcon", isDarkThemeOn?"white": "black");
                                }
                                // boolean priority = notification.getBoolean("priority");
                                // bundle.putString("soundName", 
                                //     priority ? bundle.getString("soundNameHigh") : bundle.getString("soundNameNormal"));
                                bundle.putString("title", title);
                                bundle.putString("message", msg);
                                handleLocalNotification(context, bundle);       
                                Log.v(LOG_TAG, "in_for_last" + bundle.toString());
                            }
                        }

                    } catch (JSONException e) {
                        Log.v(LOG_TAG, "jsonexception" + e.toString());
                    }
                }
                
                
                bundle.putString("showFlag", "false");
                handleLocalNotification(context, bundle);
                
            }
        };

        asyncTask.execute();
        
        
    }

}