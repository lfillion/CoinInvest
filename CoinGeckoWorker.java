package com.example.coininverst;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class CoinGeckoWorker extends Worker
{
    String m_oFilePath = null;
    public CoinGeckoWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
        m_oFilePath = getInputData().getString("FilePath");
        if (m_oFilePath == null)
            m_oFilePath = "";
    }

    @Override
    public Result doWork()
    {
        Result oResult = Result.success();
        // Do the work here--in this case, upload the images.
        // Indicate whether the work finished successfully with the Result
        String oFilename = m_oFilePath + "/" + "CoinGeckoWorkerTest.log";

        File     oFile   = new File(oFilename);
        boolean  bAppend = true;

        try
        {
            if (!oFile.exists())
                oFile.createNewFile();

            FileWriter     oFWriter = new FileWriter(oFile.getAbsoluteFile(), bAppend);
            BufferedWriter oBWriter = new BufferedWriter(oFWriter);

            Date oDate = new Date();

            oBWriter.write(oDate.toString() + "\r\n");

            oBWriter.close();
            oFWriter.close();
        }
        catch (Exception oIgnore)
        {
            oResult = Result.failure();
        }

        return oResult;
    }

}
