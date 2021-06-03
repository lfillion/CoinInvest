package com.example.coininverst;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

//public class TestFgndService extends IntentService
public class TestFgndService extends Service
{
    private final int    FOREGROUND_ID    = 1378;
    private final String NOTIF_CHANNEL_ID = "TestFgndServiceNotifChannel_ID";

    //public TestFgndService()
    //{
    //    super("TestFgndService");
    //}
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        Toast.makeText(this, "Service Created", Toast.LENGTH_LONG).show();

    }
    //@Override
    //public void onHandleIntent(Intent oIntent)
    //{
    //    CreateNotificationChannel();
//
    //    startForeground(FOREGROUND_ID, GetForgroundNotification());
//
    //    try
    //    {
    //        Thread.sleep(10000);
    //    }
    //    catch (Exception oEx)
    //    {
    //    }
    //    stopForeground(true);
    //}
    private Notification GetForgroundNotification()
    {
        Bitmap                     oIcon  = BitmapFactory.decodeResource(getBaseContext().getResources(), android.R.drawable.star_on);
        NotificationCompat.Builder oBuild = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID);

        oBuild.setContentTitle("TestFgndService Title")
                .setTicker("TestFgndService Ticker")
                .setSmallIcon(android.R.drawable.star_on)
                .setLargeIcon(Bitmap.createScaledBitmap(oIcon, 128, 128, false))
                .setOngoing(true);

        return oBuild.build();
    }

    private void CreateNotificationChannel()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            CharSequence        oName        = "TestFgndServiceNotifChannel";
            String              oDescription = "TestFgndService Notification Channel";
            int                 iImportance  = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel oChannel     = new NotificationChannel(NOTIF_CHANNEL_ID, oName, iImportance);
            oChannel.setDescription(oDescription);

            // Don't see these lines in your code...
            NotificationManager oNotificationManager = getSystemService(NotificationManager.class);
            oNotificationManager.createNotificationChannel(oChannel);
        }
    }
    //public int onStartCommand(Intent intent, int flags, int startId)
    //{
    //        Bitmap icon = BitmapFactory.decodeResource(getResources(),
    //                R.drawable.ic_launcher_background);
    //        if (icon == null)
    //            icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
//
    //        Toast.makeText(this,"Creating Notification",Toast.LENGTH_SHORT).show();
////
    //        Notification notification = new NotificationCompat.Builder(this)
    //                .setContentTitle("AndroidMonks Sticker")
    //                .setTicker("AndroidMonks Sticker")
    //                .setContentText("Example")
    //                .setSmallIcon(R.drawable.ic_launcher_background)
    //                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
    //                .setOngoing(true).build();
////
    //        startForeground(1001,notification);
    //        return START_STICKY;
    //    }
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        CreateNotificationChannel();
        startForeground(FOREGROUND_ID, GetForgroundNotification());

        return START_STICKY;
    }
        @Override
        public void onDestroy() {
            Toast.makeText(this, "Service Stopped", Toast.LENGTH_LONG).show();
        }
    }