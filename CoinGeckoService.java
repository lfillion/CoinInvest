package com.example.coininverst;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.*;
import android.os.Process;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.ArrayList;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class CoinGeckoService extends Service
{
    private Looper                        m_oServiceLooper     = null;
    private ServiceHandler                m_oServiceHandler    = null;
    private HandlerThread                 m_oHandlerThread     = null;
    private boolean                       m_bExitFlag          = false;
    private int                           m_iSnapshotPeriodSec = 300;
    private String[]                      m_aoPages            = null;
    private ArrayList<CCoinInfo>          m_oCoinSnapshotList  = null;
    private volatile ArrayList<CCoinItem> m_oCoinExportList    = null;
    private Object                        m_oSyncExport        = null;
    private final IBinder                 m_oBinder            = new LocalBinder();
    private String                        m_oLogPath           = null;
    private String                        m_oImgPath           = null;
    private Intent                        m_oIntent            = null;
    private ArrayList<String>             m_oNotifChannels     = null;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler
    {
        public ServiceHandler(Looper oLooper)
        {
            super(oLooper);
        }
        @Override
        public void handleMessage(Message msg)
        {
            while (!m_bExitFlag)
            {
                long lStartTimeMs   = System.currentTimeMillis();
                long lElapsedTimeMs = 0L;

                for (String oWebPage : m_aoPages)
                {
                    if (!m_bExitFlag)
                    {
                        try
                        {
                            GetCoinSnapshot(new URL(oWebPage));
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }

                DownloadImages();
                RefreshExportable();
                LogAllCoins();
                NotifyAppActivity();

                do
                {
                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e)
                    {
                        // Restore interrupt status.
                        Thread.currentThread().interrupt();
                    }
                    lElapsedTimeMs = (System.currentTimeMillis() - lStartTimeMs);
                }
                while (!m_bExitFlag && (lElapsedTimeMs < (m_iSnapshotPeriodSec * 1000)));
            }
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }
    @Override
    public void onCreate()
    {
        int iNbPage = 3;
        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work doesn't disrupt our UI.
        m_oLogPath    = getExternalFilesDir(null) + "/CoinDBase" ;
        m_oImgPath    = getExternalFilesDir(null) + "/CoinImages";
        m_aoPages     = new String[iNbPage];
        m_oSyncExport = new Object();

        CreateNotificationChannel("ALERT_CHANNEL", "CoinGeckoService Alert Channel", NotificationManager.IMPORTANCE_HIGH);

        for (int iIdx = 0; iIdx < iNbPage; iIdx++)
        {
            m_aoPages[iIdx] = new String("https://api.coingecko.com/api/v3/coins/markets?vs_currency=cad&order=market_cap_desc&per_page=100&page=" + (iIdx + 1));
        }
        m_oHandlerThread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
        m_oHandlerThread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        m_oServiceLooper  = m_oHandlerThread.getLooper();
        m_oServiceHandler = new ServiceHandler(m_oServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Toast.makeText(this, "CoinGeckoService starting", Toast.LENGTH_SHORT).show();

        m_oIntent = intent;

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = m_oServiceHandler.obtainMessage();
        msg.arg1 = startId;
        m_oServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return m_oBinder;
    }

    @Override
    public void onDestroy()
    {
        m_bExitFlag = true;
        Toast.makeText(this, "CoinGeckoService done", Toast.LENGTH_SHORT).show();
    }
    private void NotifyAppActivity()
    {
        Intent intent = new Intent("Event.NewDataFromCoinGeckoService");
        // You can also include some extra data.
        intent.putExtra("ServiceMessage", "Notify.NewDataFromCoinGeckoService");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    private void GetCoinSnapshot(URL oURL) throws MalformedURLException, IOException
    {
        InputStream       oIS        = oURL.openStream();
        InputStreamReader oSRd       = new InputStreamReader(oIS);
        BufferedReader    oReader    = new BufferedReader(oSRd);
        String            oLine      = null;
        Timestamp         oTimestamp = new Timestamp(System.currentTimeMillis());

        while ((oLine = oReader.readLine()) != null)
        {
            if (oLine.startsWith("[")&&oLine.endsWith("]"))
            {
                int     iStart = 0;
                int     iEnd   = 0;
                boolean bExit  = false;

                if (m_oCoinSnapshotList == null)
                    m_oCoinSnapshotList = new ArrayList<>();

                do
                {
                    iStart = CCoinInfo.GetOpenBrace(iStart, oLine);
                    if (iStart < 0)
                        bExit = true;
                    else
                        iEnd = CCoinInfo.MatchBrace(iStart, oLine);

                    if (iEnd < 0)
                        bExit = true;

                    if (!bExit)
                    {
                        m_oCoinSnapshotList.add(new CCoinInfo(oLine.substring(iStart, iEnd), oTimestamp));
                    }
                    iStart = iEnd + 1;
                }
                while (!bExit);
            }
        }
        oReader.close();
    }
    private CCoinItem GetHistory(CCoinInfo oCoin) throws MalformedURLException, IOException
    {
        CCoinItem         oOut       = null;
        String            oWebPart1  = "https://api.coingecko.com/api/v3/coins/";
        String            oWebPart2  = "/market_chart?vs_currency=cad&days=1";
        String            oCoinName  = oCoin.Name().toLowerCase().replace(" ", "%20");
        String            oWeb       = String.join("", oWebPart1, oCoinName, oWebPart2);
        URL               oURL       = new URL(oWeb);
        InputStream       oIS        = oURL.openStream();
        InputStreamReader oSRd       = new InputStreamReader(oIS);
        BufferedReader    oReader    = new BufferedReader(oSRd);
        String            oLine      = null;
        Timestamp         oTimestamp = new Timestamp(System.currentTimeMillis());

        while ((oLine = oReader.readLine()) != null)
        {
            int      iStart   = 0;
            int      iEndAll  = 0;
            int      iEnd     = 0;
            boolean  bExit    = false;

            iStart = CCoinInfo.GetOpenBlock('[', iStart, oLine);
            if (iStart < 0)
                bExit = true;
            else
                iEndAll = CCoinInfo.MatchBlock('[', ']', iStart, oLine);

            if (iEndAll < 0)
                bExit = true;

            iStart++;
            bExit = iStart >= iEndAll;

            ArrayList<String> oTuples   = new ArrayList<>();
            final int         iMaxTuple = 50;

            while (!bExit)
            {
                iStart = CCoinInfo.GetOpenBlock('[', iStart, oLine);
                if (iStart < 0)
                    bExit = true;
                else
                    iEnd = CCoinInfo.MatchBlock('[', ']', iStart, oLine);
                if (iEnd < 0)
                    bExit = true;

                if (!bExit &&(iStart < iEnd))
                {
                    String oItem = oLine.substring(iStart + 1, iEnd);

                    if (oItem.contains(",") && !oItem.contains("[") && !oItem.contains("]"))
                        oTuples.add(oItem);

                    iStart = iEnd; //For next
                }
            }
            if (oTuples.size() > 0)
            {
                if (oTuples.size() > iMaxTuple)
                    oTuples.subList(0, oTuples.size() - iMaxTuple).clear();

                for (String oItem : oTuples)
                {
                    String[] aoCouple = oItem.split(",");

                    if (aoCouple.length == 2)
                    {
                        long      lTimeMSec = Long.parseLong(aoCouple[0]);
                        Timestamp oLocal    = new Timestamp(lTimeMSec);
                        double    dPrice    = Double.parseDouble(aoCouple[1]);

                        if (oOut == null)
                            oOut = new CCoinItem(oCoin.Name(), oCoin.Symbol(), oCoin.Rank(), dPrice, oLocal, true, oCoin.ImgPath());
                        else
                            oOut.NewValue(dPrice, oLocal);
                    }
                }
            }
        }
        oReader.close();

        return oOut;
    }
    private void RefreshExportable()
    {
        synchronized (m_oSyncExport)
        {
            if (m_oCoinExportList == null)
            {
                int iNbHistory = 0;

                m_oCoinExportList = new ArrayList<>();

                for (CCoinInfo oInfo : m_oCoinSnapshotList)
                {
                    CCoinItem oItemFromChart = null;
                    // m_oCoinExportList.add(new CCoinItem(oInfo));
                    if (iNbHistory < 10) // Market charts take some time
                    {
                        try
                        {
                            oItemFromChart = GetHistory(oInfo);
                            if (oItemFromChart != null)
                            {
                                m_oCoinExportList.add(oItemFromChart);
                                iNbHistory++;
                            }
                        }
                        catch (IOException e)
                        { // Market chart wasn't available
                        }
                    }
                    if (oItemFromChart == null)
                        m_oCoinExportList.add(new CCoinItem(oInfo));
                }
            }
            else
            {
                for (CCoinInfo oInfo : m_oCoinSnapshotList)
                {
                    for (CCoinItem oItem : m_oCoinExportList)
                    {
                        if (oInfo.Name().equals(oItem.Name))
                        {
                            if (oItem.NewValue(oInfo.Price(), oInfo.TimeStamp()));
                            {
                                if (oItem.AlertTriggered)
                                {
                                    NotifyAlert(oItem);
                                    oItem.ClearOneShotAlert();
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        m_oCoinSnapshotList.clear();
    }
    private void DownloadImages()
    {
        File oDir = new File(m_oImgPath);

        if (!oDir.exists())
        {
            if (!oDir.mkdir())
                return;
        }
        for (CCoinInfo oInfo : m_oCoinSnapshotList)
        {
            String oURL           = oInfo.ImgURL();
            String oLocalFileneme = m_oImgPath + "/" + oInfo.Name() + ".png";

            File oFile = new File(oLocalFileneme);

            if (!oFile.exists())
                DownloadFile(oURL, oLocalFileneme);

            oInfo.SetImgPath(oLocalFileneme);
        }
    }
    private boolean DownloadFile(String oURL, String oLocalPath)
    {
        boolean bSuccess = false;

        try
        {
            InputStream     oInStream     = new URL(oURL).openStream();
            DataInputStream oDataInStream = new DataInputStream(oInStream);

            byte[] abyBuffer = new byte[1024];
            int    iLength   = 0;

            FileOutputStream oFileOutStream = new FileOutputStream(new File(oLocalPath));

            while ((iLength = oDataInStream.read(abyBuffer)) > 0)
            {
                oFileOutStream.write(abyBuffer, 0, iLength);
            }
            bSuccess = true;
        }
        catch (MalformedURLException mue)
        {
            mue.printStackTrace();
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
        catch (SecurityException se)
        {
            se.printStackTrace();
        }
        return bSuccess;
    }
    private void LogAllCoins()
    {
        if (m_oCoinExportList != null)
        {
            File         oDir         = new File(m_oLogPath);
            final String oStatKeyword = "#StatVersion:";
            final String oCoinKeyword = "#CoinInfo   :";

            if (!oDir.exists())
            {
                if (!oDir.mkdir())
                    return;
            }
            for (CCoinItem oCoin : m_oCoinExportList)
            {
                String oFilename = m_oLogPath + "/" + oCoin.Name + ".log";

                try
                {
                    File                      oFile   = new File(oFilename);
                    boolean                   bAppend = true;
                    CCoinItem.CShortTermStats oStats  = oCoin.GetStats();
                    boolean                   bProcess = (oStats != null)&& oStats.StatAvailable();

                    if (!oFile.exists())
                        oFile.createNewFile();
                    else if (bProcess)
                    {
                        FileReader     oFReader   = new FileReader(oFile.getAbsoluteFile());
                        BufferedReader oBReader   = new BufferedReader(oFReader);
                        String         oFirstLine = oBReader.readLine();
                        String         oLastLine  = oFirstLine;
                        String         oNextLine  = null;

                        bAppend = oFirstLine.contains(oStatKeyword) && oFirstLine.contains(oStats.Revison());

                        while ((oNextLine = oBReader.readLine()) != null)
                            oLastLine = oNextLine;

                        bProcess = oStats.NextPeriod(oLastLine);

                        oBReader.close();
                        oFReader.close();
                    }
                    if (bProcess)
                    {
                        FileWriter     oFWriter = new FileWriter(oFile.getAbsoluteFile(), bAppend);
                        BufferedWriter oBWriter = new BufferedWriter(oFWriter);

                        if (!bAppend)
                        {
                            oBWriter.write(oStatKeyword + oStats.Revison() + "\r\n");
                            oBWriter.write(oCoinKeyword + oCoin.Name + "\r\n");
                        }
                        oBWriter.write(oStats.FormatStat() + "\r\n");
                        oBWriter.close();
                        oFWriter.close();
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
    public ArrayList<CCoinItem> GetCoinList()
    {
        while (m_oCoinExportList == null)
        {
            try
            {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Restore interrupt status.
                Thread.currentThread().interrupt();
            }
        }
        synchronized (m_oSyncExport)
        {
            return m_oCoinExportList;
        }
    }
    public void TestNotification()
    {
        NotifyAlert(null);
    }
    private void NotifyAlert(CCoinItem oInAlert) //Set oInAlert = null to test an alert
    {
        String oChannelToUse = null;
        String oTitle        = null;
        String oMessage      = null;

        if ((m_oNotifChannels != null)&&(m_oNotifChannels.size() > 0))
        {
            for (String oChannelID : m_oNotifChannels)
            {
                if (oChannelID.contains("ALERT"))
                {
                    oChannelToUse = oChannelID;
                    break;
                }
            }
        }

        if (oInAlert == null)
        {
            oTitle   = "Test Notification";
            oMessage = "Message Example!";
        }
        else
        {
            oTitle   = oInAlert.AlertTitle;
            oMessage = oInAlert.AlertMessage;
        }
        SendNotification(oChannelToUse, oTitle, oMessage);
    }
    private void CreateNotificationChannel(String oChannelName, String oDescrip, int iImportance)
    {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            String              oNewID   = String.join("", oChannelName, "_ID");
            NotificationChannel oChannel = new NotificationChannel(oNewID, oChannelName, iImportance);

            if (m_oNotifChannels == null)
                m_oNotifChannels = new ArrayList<>();

            if (!m_oNotifChannels.contains(oNewID))
                m_oNotifChannels.add(oNewID);

            oChannel.setDescription(oDescrip);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager oNotificationManager = getSystemService(NotificationManager.class);
            oNotificationManager.createNotificationChannel(oChannel);
        }
    }
    private void SendNotification(String oChannelID, String oTitle, String oMessage)
    {
        NotificationCompat.Builder oBuilder =
                new NotificationCompat.Builder(this, oChannelID)
                        .setSmallIcon(R.drawable.ic_launcher_background)
                        .setContentTitle(oTitle)
                        .setContentText(oMessage);

        //Intent oNotificationIntent = new Intent(this, MenuScreen.class);
        //Intent oNotificationIntent = m_oIntent;
        Intent        oNotificationIntent = new Intent(this, CoinGeckoService.class);
        PendingIntent oContentIntent      = PendingIntent.getActivity(this, 0, oNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        oBuilder.setContentIntent(oContentIntent);
        oBuilder.setAutoCancel(true);
        oBuilder.setLights(Color.BLUE, 500, 500);
        long[] alPattern = {500,500,500,500,500,500,500,500,500};
        oBuilder.setVibrate(alPattern);
        oBuilder.setStyle(new NotificationCompat.InboxStyle());
        // Add as notification
        NotificationManager oManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        oManager.notify(1, oBuilder.build());
    }

    public void SetRefreshPeriodSec(int iNbSec)
    {
        m_iSnapshotPeriodSec = iNbSec;
    }

    public class LocalBinder extends Binder
    {
        CoinGeckoService getService()
        {   // Return this instance of LocalService so clients can call public methods
            return CoinGeckoService.this;
        }
    }
}
