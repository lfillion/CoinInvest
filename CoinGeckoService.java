package com.example.coininverst;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.os.Process;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.*;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

public class CoinGeckoService extends Service
{
    private Looper               m_oServiceLooper     = null;
    private ServiceHandler       m_oServiceHandler    = null;
    private HandlerThread        m_oHandlerThread     = null;
    private boolean              m_bExitFlag          = false;
    private final int            m_iSnapshotPeriodSec = 300;
    private String[]             m_aoPages            = null;
    private ArrayList<CCoinInfo> m_oCoinSnapshotList  = null;
    private final IBinder        m_oBinder            = new LocalBinder();
    private String               m_oLogPath           = null;

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

                LogAllCoins();

                int iNbSec = m_iSnapshotPeriodSec;
                while (!m_bExitFlag && (iNbSec-- > 0))
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
                }
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
        m_oLogPath = getExternalFilesDir(null) + "/CoinDBase";
        m_aoPages  = new String[iNbPage];

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
                catch (IOException e){
                    e.printStackTrace();
                }

            }
            m_oCoinSnapshotList.clear();
        }
    }
    public ArrayList<String> GetCoinList()
    {
        ArrayList<String> oOut = new ArrayList<>();

        File oDir = new File(m_oLogPath);

        if (oDir.exists())
        {
            File[] aoFiles = oDir.listFiles();

            for (File oFile : aoFiles)
            {
                String oFilename = oFile.getName();
                oOut.add(oFilename);
            }
        }

        return oOut;
    }
    public class LocalBinder extends Binder
    {
        CoinGeckoService getService()
        {   // Return this instance of LocalService so clients can call public methods
            return CoinGeckoService.this;
        }
    }
}
