package com.dieam.reactnativepushnotification.modules;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.AsyncTask;
import android.content.res.Configuration;
import android.util.Log;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.app.NotificationManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;

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
import java.lang.Thread;
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


        Bundle bundle = intent.getExtras();

        Log.v(LOG_TAG, "onMessageReceived: " + bundle);
        monitorServer(context, bundle);
    }

    private void handleLocalNotification(Context context, Bundle bundle) {

        // If notification ID is not provided by the user for push notification, generate one at random
        if(!bundle.getString("title").equals("xyz")){
            SecureRandom randomNumberGenerator = new SecureRandom();
            bundle.putString("id", String.valueOf(randomNumberGenerator.nextInt()));
            Log.v(LOG_TAG, "changed notificationid: " + bundle);
        }
        
        

        Application applicationContext = (Application) context.getApplicationContext();
        RNPushNotificationHelper pushNotificationHelper = new RNPushNotificationHelper(applicationContext);
        
        Log.v(LOG_TAG, "handleNotification: " + bundle);

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
        AudioManager am;
        Application applicationContext = (Application) context.getApplicationContext();
        NotificationManager notificationManager = (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        am= (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = am.getRingerMode();
        
        String serverurl = bundle.getString("serverurl");
        String userToken = bundle.getString("userToken");
        if(serverurl.equals("") || userToken.equals("")){
            Log.v(LOG_TAG, "serverurl or usertoken is null");
            return;
        }
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
                boolean isDarkThemeOn = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)  == Configuration.UI_MODE_NIGHT_YES;
                if(s != null)
                {
                    try {
                        
                        boolean playSound = bundle.getBoolean("playSound");
                        boolean vibrate = bundle.getBoolean("vibrate");
                        JSONObject jsonObject = new JSONObject(s);
                        if(jsonObject.has("error")){
                            bundle.putString("title", "Server Error: " + getMyPrettyDate(System.currentTimeMillis()));
                            bundle.putString("message", "Error");
                            bundle.putString("channelId", "fusion-mute-0113");   
                            bundle.putString("smallIcon", isDarkThemeOn?"white": "black");
                            handleLocalNotification(context, bundle);       
                        }
                        else if(jsonObject.has("notifications")) {
                            String notifications = jsonObject.getString("notifications");
                            JSONArray notifArr = new JSONArray(notifications);
                            boolean hasPriority = false;
                            for (int i = 0; i < notifArr.length(); i++) {
                                JSONObject eventObj = new JSONObject(notifArr.getString(i));
                                JSONObject notification = new JSONObject(eventObj.getString("notification"));
                                String priority = notification.has("priority")?notification.getString("priority"):"false";
                                if(priority.equals("true")){
                                    hasPriority = true;
                                    break;
                                }                            
                            }
                            for (int i = 0; i < notifArr.length(); i++) {
                                JSONObject eventObj = new JSONObject(notifArr.getString(i));
                                String type = eventObj.getString("type");
                                JSONObject notification = new JSONObject(eventObj.getString("notification"));
                                String msg;
                                String title;
                                String red = "red";
                                String black = "black";
                                String priority = notification.has("priority")?notification.getString("priority"):"false";
                                if(type.equals("user_event")){
                                    msg = notification.getString("message");
                                    title =  "<p><span style='color:" + (priority.equals("true") ? red : black) + "'>New Message</span>    <span style='color:lightgray'>" + getMyPrettyDate(notification.getLong("queue_time")* 1000) + "</span></p>";
                                    bundle.putString("actionType", "user_event");
                                }
                                else if(type.equals("active_alarm")) {
                                    String cleared = notification.getString("cleared");
                                    String ncAlarm =  cleared.equals("true")?"Alarm Reset":"New Alarm";
                                    msg = notification.getString("name") + ": " + (cleared.equals("true")?notification.getString("reset_text"):notification.getString("active_text"));
                                    title =  "<p><span style='color:"+ (priority.equals("true") ? red : black) + "'>" + ncAlarm + "</span>    <span style='color:lightgray'>" + getMyPrettyDate((notification.getLong("active_time") + (cleared.equals("true")?notification.getLong("active_duration"):0))*1000) + "</span></p>";
                                    bundle.putString("actionType", "active_alarm");
                                }else{
                                    continue;
                                }

                                if(priority.equals("true")){
                                    bundle.putString("smallIcon", isDarkThemeOn?"white_priority": "black_priority");
                                }
                                else{
                                    bundle.putString("smallIcon", isDarkThemeOn?"white": "black");
                                }

                                //object id: DataProvider.onNotification eventid or alarmid
                                String objectId = notification.getString("id");
                                bundle.putString("objectId", objectId);
                                priority = hasPriority?"true": priority;
                                //this soundName is for lower than 26
                                if(playSound){
                                    if(priority.equals("true")){
                                        bundle.putString("soundName", "priority.wav");
                                        playAlarmSound(context, "priority.wav");
                                        bundle.putString("channelId", "fusion-sound-priority-0113");
                                    }
                                    else{
                                        if(ringerMode == AudioManager.RINGER_MODE_NORMAL){
                                            bundle.putString("soundName", "normal.wav");
                                            bundle.putString("channelId", "fusion-sound-normal-0113");
                                        }
                                        else if(ringerMode == AudioManager.RINGER_MODE_VIBRATE){
                                            bundle.putString("channelId", "fusion-vibrate-0113");
                                        }
                                        else {
                                            bundle.putString("channelId", "fusion-mute-0113");
                                        }
                                    }
                                }
                                else {
                                    bundle.putString("channelId", "fusion-mute-0113");
                                }
                                
                                bundle.putString("title", title);
                                bundle.putString("message", msg);
                                handleLocalNotification(context, bundle);     
                                
                            }
                        }
                        
                    } catch (Exception e) {
                        Log.v(LOG_TAG, "responseexception" + e.toString());
                    }
                }
                else{
                    bundle.putString("title", "Network Error: " + getMyPrettyDate(System.currentTimeMillis()));
                    bundle.putString("message", "Error");
                    bundle.putString("channelId", "fusion-mute-0113");   
                    bundle.putString("smallIcon", isDarkThemeOn?"white": "black");
                    handleLocalNotification(context, bundle);       
                }
                
                bundle.putString("id", "2704246");
                bundle.putString("title", "xyz");
                handleLocalNotification(context, bundle);
            }
        };

        asyncTask.execute();
        
        
    }

    public void playAlarmSound (Context context, String soundNameString) {
        final MediaPlayer mediaPlayer = new MediaPlayer();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mediaPlayer.reset();
                            mediaPlayer.release();
                        }
                    });
                    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mediaPlayer.start();
                        }
                    });
                    AssetFileDescriptor afd = context.getApplicationContext().getAssets().openFd(soundNameString);
                    if (afd == null) return false;
                    mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
    
                    if (Build.VERSION.SDK_INT >= 21) {
                        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build());
                    } else {
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                    }
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    mediaPlayer.prepare();
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
    
        }.execute();
    }

}