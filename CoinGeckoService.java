package com.example.coininverst;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
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
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class CoinGeckoService extends Service
{
    private Looper                        m_oServiceLooper     = null;
    private ServiceHandler                m_oServiceHandler    = null;
    private HandlerThread                 m_oHandlerThread     = null;
    private boolean                       m_bExitFlag          = false;
    private boolean                       m_bExitEcho          = false;
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
    private Thread                        m_oStillRunningProc  = null;
    private Message                       m_oHandleMessage     = null;
    private Context                       m_oContext           = null;
    private PowerManager.WakeLock         m_oWakeLock          = null;
    private PowerManager                  m_oPowerManager      = null;
    private final int                     FOREGROUND_ID        = 1338;
    private boolean                       m_bDynamicExportList = true;
    private int                           m_iEventLogSize      = 1024;
    private boolean                       m_bEventLogRestart   = false;

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
            m_oHandleMessage = msg;

            LogEvent("handleMessage(" + msg + ")");

            m_oStillRunningProc = new Thread()
            {
                public void run()
                {
                    InfiniteProcees();
                }
            };
            m_oStillRunningProc.start();
        }
    }
    private void InfiniteProcees()
    {
        LogEvent("Entering InfiniteProcess");

        while (!m_bExitFlag)
        {
            long    lStartTimeMs         = System.currentTimeMillis();
            long    lElapsedTimeMs       = 0L;
            long    lWakeLockTimeoutMSec = 10 * 60 * 1000L;
            boolean bExceptionOccurred   = false;

           // AcquireWakeLock();
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
                        bExceptionOccurred = true;
                        LogEvent("Exception " + e.getMessage());
                    }
                }
            }

            if (!bExceptionOccurred)
            {
                DownloadImages();
                RefreshExportable();
                SaveAllCoins();
                NotifyAppActivity();
            }
            // ReleaseWakelock();

            lElapsedTimeMs = (System.currentTimeMillis() - lStartTimeMs);

            if ((m_iSnapshotPeriodSec * 1000) > (int)lElapsedTimeMs)
                SleepMSec((m_iSnapshotPeriodSec * 1000) - (int)lElapsedTimeMs);
            //do
            //{
            //    SleepMSec(1000);
            //    lElapsedTimeMs = (System.currentTimeMillis() - lStartTimeMs);
            //}
            //while (!m_bExitFlag && (lElapsedTimeMs < (m_iSnapshotPeriodSec * 1000)));
        }

        LogEvent("Exiting InfiniteProcess");
        // Stop the service using the startId, so that we don't stop
        // the service in the middle of handling another job
        stopSelf(m_oHandleMessage.arg1);

        m_bExitEcho = m_bExitFlag;
    }
    private boolean SleepMSec(int iMSec)
    {
        boolean bSuccess = false;
        try
        {
            Thread.sleep(iMSec);
            bSuccess = true;
        }
        catch (Exception oEx)
        {
            // Restore interrupt status.
            Thread.currentThread().interrupt();
        }
        return bSuccess;
    }
    public void AcquireWakeLock()
    {
        if (Globals.wakelock != null)
        {
            Globals.wakelock.release();
            Globals.wakelock = null;
        }
        m_oPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        assert m_oPowerManager != null;
        m_oWakeLock     =  m_oPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"CoinGeckoService:WakeLock");

        m_oWakeLock.acquire(10*60*1000L /*10 minutes*/);

        Globals.wakelock = this.m_oWakeLock;
    }
    public void ReleaseWakelock()
    {
        m_oWakeLock.release();
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onCreate()
    {
        int iNbPage = 3;
        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work doesn't disrupt our UI.
        m_oLogPath      = getExternalFilesDir(null) + "/CoinDBase" ;
        m_oImgPath      = getExternalFilesDir(null) + "/CoinImages";
        m_aoPages       = new String[iNbPage];
        m_oSyncExport   = new Object();
        m_oContext      = this;

        CreateNotificationChannel("FGNDSERVICE_CHANNEL", "CoinGeckoService Foreground Channel", NotificationManager.IMPORTANCE_DEFAULT);
        CreateNotificationChannel("ALERT_CHANNEL"      , "CoinGeckoService Alert Channel"     , NotificationManager.IMPORTANCE_HIGH);

        startForeground(FOREGROUND_ID, GetForgroundNotification());

        for (int iIdx = 0; iIdx < iNbPage; iIdx++)
        {
            m_aoPages[iIdx] = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=cad&order=market_cap_desc&per_page=100&page=" + (iIdx + 1);
        }
        m_oHandlerThread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
        m_oHandlerThread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        m_oServiceLooper  = m_oHandlerThread.getLooper();
        m_oServiceHandler = new ServiceHandler(m_oServiceLooper);

        Toast.makeText(this, "CoinGeckoService starting", Toast.LENGTH_SHORT).show();
    }

    private Notification GetForgroundNotification()
    {
        String oChannelToUse = "No_ID";

        if ((m_oNotifChannels != null)&&(m_oNotifChannels.size() > 0))
        {
            for (String oChannelID : m_oNotifChannels)
            {
                if (oChannelID.contains("FGNDSERVICE"))
                {
                    oChannelToUse = oChannelID;
                    break;
                }
            }
        }

        Bitmap                     oIcon  = BitmapFactory.decodeResource(getBaseContext().getResources(), android.R.drawable.star_on);
        NotificationCompat.Builder oBuild = new NotificationCompat.Builder(this, oChannelToUse);

        oBuild.setContentTitle("CoinGeckoService")
                .setTicker("CoinGeckoService Ticker")
                .setSmallIcon(android.R.drawable.star_on)
                .setLargeIcon(Bitmap.createScaledBitmap(oIcon, 128, 128, false))
                .setOngoing(true);

        return oBuild.build();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        boolean  bForeGnd  = false;

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
        //stopForeground(true);

        return m_oBinder;
    }

    //@Override
    //public void onRebind(Intent intent)
    //{
    //    super.onRebind(intent);
//
    //    stopForeground(true);
    //}

    //@Override
    //public boolean onUnbind(Intent intent)
    //{
    //    startForeground(FOREGROUND_ID, GetForgroundNotification());
//
    //    return super.onUnbind(intent);
    //}

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        m_bExitFlag = true;

        stopForeground(true);

        int iTimeoutMsec = 2000;
        while (!m_bExitEcho && (iTimeoutMsec > 0) && SleepMSec(100))
        {
            iTimeoutMsec -= 100;
        }
        //Toast.makeText(this, "CoinGeckoService done", Toast.LENGTH_SHORT).show();

        Intent oBroadcastIntent = new Intent();
        oBroadcastIntent.setAction("RestartService");
        oBroadcastIntent.setClass(this, ServiceRestarter.class);
        this.sendBroadcast(oBroadcastIntent);
    }
    private void NotifyAppActivity()
    {
        Intent intent = new Intent("Event.NewDataFromCoinGeckoService");
        // You can also include some extra data.
        intent.putExtra("ServiceMessage", "Notify.NewDataFromCoinGeckoService");

        LogEvent("Notify.NewDataFromCoinGeckoService");

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
        String            oWeb       = String.join("", oWebPart1, oCoin.ID(), oWebPart2);
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
                        long      lTimeMSec  = Long.parseLong(aoCouple[0]);
                        Timestamp oTimeStamp = new Timestamp(lTimeMSec);
                        double    dPrice     = Double.parseDouble(aoCouple[1]);

                        if (oOut == null)
                            oOut = new CCoinItem(oCoin.Name(), oCoin.Symbol(), oCoin.Rank(), dPrice, oTimeStamp, true, oCoin.ImgPath());
                        else
                            oOut.NewValue(dPrice, oTimeStamp);
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
                int iNbHistory      = 0;
                int iLocalDataCount = 0;

                m_oCoinExportList = new ArrayList<>();

                LogEvent("Creating Export List");

                for (CCoinInfo oInfo : m_oCoinSnapshotList)
                {
                    CCoinItem oItemFromChart = null;
                    // m_oCoinExportList.add(new CCoinItem(oInfo));
                    if (iNbHistory < 20) // Limit because Market charts take some time
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
                            LogEvent("Exception: Market chart for " + oInfo.Name() + " not available");
                        }
                    }
                    if (oItemFromChart == null)
                    {
                        CCoinItem oItemFromFile = GetLocalHistory(oInfo);

                        if (oItemFromFile != null)
                        {
                            oItemFromFile.NewValue(oInfo.Price(), oInfo.TimeStamp());

                            m_oCoinExportList.add(oItemFromFile);

                            iLocalDataCount++;
                        }
                        else
                            m_oCoinExportList.add(new CCoinItem(oInfo));
                    }
                }
                LogEvent("Got Local Data for " + iLocalDataCount + " Coins");
            }
            else
            {
                LogEvent("Updating Export List");

                for (CCoinInfo oInfo : m_oCoinSnapshotList)
                {
                    boolean bFound   = false;
                    boolean bRankChg = false;

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
                            bFound = true;
                            if ((oInfo.Rank() != oItem.rank)&& m_bDynamicExportList)
                                oItem.NewRank(oInfo.Rank());
                            break;
                        }
                    }
                    if (!bFound && m_bDynamicExportList)
                    {
                        m_oCoinExportList.add(new CCoinItem(oInfo));
                        LogEvent("Added " + oInfo.Name() + " to List");
                    }
                }
                if (m_bDynamicExportList)
                {
                    ArrayList<CCoinItem> oToRemove = new ArrayList<>();

                    for (CCoinItem oItem : m_oCoinExportList)
                    {
                        boolean bFound = false;
                        for (CCoinInfo oInfo : m_oCoinSnapshotList)
                        {
                            if (oInfo.Name().equals(oItem.Name))
                            {
                                bFound = true;
                                break;
                            }
                        }
                        if (!bFound && !oItem.AlertEnabled && !oItem.Favorite)
                        {
                            oToRemove.add(oItem);
                            LogEvent("Removing " + oItem.Name + " from List");
                        }
                    }
                    if (oToRemove.size() > 0)
                        m_oCoinExportList.removeAll(oToRemove);
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
        int iCount = 0;
        for (CCoinInfo oInfo : m_oCoinSnapshotList)
        {
            String oURL           = oInfo.ImgURL();
            String oLocalFileneme = m_oImgPath + "/" + oInfo.Name() + ".png";

            File oFile = new File(oLocalFileneme);

            if (!oFile.exists())
            {
                DownloadFile(oURL, oLocalFileneme);
                iCount++;
            }
            oInfo.SetImgPath(oLocalFileneme);
        }
        if (iCount > 0)
            LogEvent("Downloaded " + iCount + " Coin images");
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
            LogEvent("Exception " + mue.getMessage());
        }
        catch (IOException ioe)
        {
            LogEvent("Exception " + ioe.getMessage());
        }
        catch (SecurityException se)
        {
            LogEvent("Exception " + se.getMessage());
        }
        return bSuccess;
    }
    private String BuildFilename(String oCoinName)
    {
        return m_oLogPath + "/" + oCoinName + ".log";
    }
    private boolean FileDataAvailable(CCoinInfo oCoin)
    {
        String oFilename       = BuildFilename(oCoin.Name());
        File   oFile           = new File(oFilename);
        long   lDiffMSec       = new Date().getTime() - oFile.lastModified();
        long   lStillValidMSec = 60 * 60 * 1000;

        return oFile.exists() && (lDiffMSec <= lStillValidMSec);
    }
    private boolean SaveCoinSamples(CCoinItem oCoin, BufferedWriter oBufWriter)
    {
        boolean                   bSuccess = true;
        CCoinItem.CShortTermStats oStats   = oCoin.GetStats();

        for (CCoinItem.CStatSample oSample : oStats.History)
        {
            try
            {
                oBufWriter.write(oSample.ToExport() + "\r\n");
            }
            catch (Exception ignored)
            {
                bSuccess = false;
                break;
            }
        }
        return bSuccess;
    }
    private void LogEvent(String oEventMsg)
    {
        if ((m_oLogPath == null)||(m_iEventLogSize == 0))
            return;

        File              oDir   = new File(m_oLogPath);
        File              oFile  = new File(m_oLogPath + "/EventMsgs.log");
        ArrayList<String> oLines = new ArrayList<>();

        if (!oDir.exists())
        {
            if (!oDir.mkdir())
                return;
        }
        try
        {
            if (!oFile.exists())
            {
                oFile.createNewFile();
            }
            else if (!m_bEventLogRestart)
            {
                FileReader     oFReader = new FileReader(oFile.getAbsoluteFile());
                BufferedReader oBReader = new BufferedReader(oFReader);
                String         oLine    = null;

                while ((oLine = oBReader.readLine()) != null)
                {
                    oLines.add(oLine);
                }
                oBReader.close();
                oFReader.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        m_bEventLogRestart = false;

        Timestamp  oTimestamp = new Timestamp(System.currentTimeMillis());
        String     oNewLine   = oTimestamp.toString() + ">>" + oEventMsg;

        oLines.add(oNewLine);
        while (oLines.size() > m_iEventLogSize)
            oLines.remove(0);

        try
        {
            FileWriter     oFWriter = new FileWriter(oFile.getAbsoluteFile());
            BufferedWriter oBWriter = new BufferedWriter(oFWriter);

            for (String oLine : oLines)
            {
                oBWriter.write(oLine + "\r\n");
            }
            oBWriter.close();
            oFWriter.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    private void SaveAllCoins()
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
            int iCount = 0;
            for (CCoinItem oCoin : m_oCoinExportList)
            {
                String oFilename = BuildFilename(oCoin.Name);

                try
                {
                    File oFile = new File(oFilename);

                    if (!oFile.exists())
                        oFile.createNewFile();

                    FileWriter     oFWriter = new FileWriter(oFile.getAbsoluteFile(), false);
                    BufferedWriter oBWriter = new BufferedWriter(oFWriter);

                    SaveCoinSamples(oCoin, oBWriter);

                    oBWriter.close();
                    oFWriter.close();
                    iCount++;
                }
                catch (IOException e)
                {
                    LogEvent("Exception " + e.getMessage());
                }
            }
            LogEvent("Saved " + iCount + " Coins");
        }
    }
    private CCoinItem GetLocalHistory(CCoinInfo oCoin)
    {
        CCoinItem oOut = null;

        if (FileDataAvailable(oCoin))
        {
            try
            {
                String         oFilename = BuildFilename(oCoin.Name());
                File           oFile     = new File(oFilename);
                FileReader     oFReader  = new FileReader(oFile.getAbsoluteFile());
                BufferedReader oBReader  = new BufferedReader(oFReader);
                String         oLine     = null;

                while ((oLine = oBReader.readLine()) != null)
                {
                    CCoinItem.CStatSample oSample = CCoinItem.CreateSample(oLine);
                    if (oSample != null)
                    {
                        double    dPrice     = oSample.Price;
                        Timestamp oTimeStamp = oSample.Time;

                        if (oOut == null)
                            oOut = new CCoinItem(oCoin.Name(), oCoin.Symbol(), oCoin.Rank(), dPrice, oTimeStamp, true, oCoin.ImgPath());
                        else
                            oOut.NewValue(dPrice, oTimeStamp);
                    }
                }
                oBReader.close();
                oFReader.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return oOut;
    }
    public ArrayList<CCoinItem> GetCoinList()
    {
        LogEvent("GetCoinList called");

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
        LogEvent("Sending Notification " + oMessage);

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
