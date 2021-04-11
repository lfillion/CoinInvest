package com.example.coininverst;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.os.Process;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
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
            // Normally we would do some work here, like download a file.
            // For our sample, we just sleep for 5 seconds.
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
        InputStream       oIS     = oURL.openStream();
        InputStreamReader oSRd    = new InputStreamReader(oIS);
        BufferedReader    oReader = new BufferedReader(oSRd);
        String            oLine   = null;

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
                        m_oCoinSnapshotList.add(new CCoinInfo(oLine.substring(iStart, iEnd)));
                    }
                    iStart = iEnd + 1;
                }
                while (!bExit);
            }
        }
        oReader.close();
    }
    private void RefreshExportable()
    {
        synchronized (m_oSyncExport)
        {
            if (m_oCoinExportList == null)
            {
                m_oCoinExportList = new ArrayList<>();

                for (CCoinInfo oInfo : m_oCoinSnapshotList)
                {
                    CCoinItem oItem  = new CCoinItem(oInfo.Name(), oInfo.Symbol(), oInfo.Rank(), oInfo.Price(), oInfo.TimeStamp(), oInfo.ImgPath());
                    m_oCoinExportList.add(oItem);
                }
            }
            else
            {
                for (CCoinInfo oInfo : m_oCoinSnapshotList)
                {
                    for (CCoinItem oItem : m_oCoinExportList)
                    {
                        if (oInfo.Symbol().equals(oItem.Symbol))
                        {
                            oItem.NewValue(oInfo.Price(), oInfo.TimeStamp());
                            break;
                        }
                    }
                }
            }
        }
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
        if (m_oCoinSnapshotList != null)
        {
            File   oDir  = new File(m_oLogPath);
            String oLine = null;

            if (!oDir.exists())
            {
                if (!oDir.mkdir())
                    return;
            }
            for (CCoinInfo oCoin : m_oCoinSnapshotList)
            {
                String oFilename = m_oLogPath + "/" + oCoin.Name() + ".log";

                try
                {
                    File oFile = new File(oFilename);

                    if (!oFile.exists())
                        oFile.createNewFile();

                    FileWriter     oFWriter = new FileWriter(oFile.getAbsoluteFile(), true);
                    BufferedWriter oBWriter = new BufferedWriter(oFWriter);
                    oLine = oCoin.InfoString() + "\r\n";
                    oBWriter.write(oLine);
                    oBWriter.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            m_oCoinSnapshotList.clear();
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
