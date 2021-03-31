package com.example.coininverst;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.text.ParseException;

public class CCoinInfo
{
    private String    m_oName;
    private String    m_oSymbol;
    private double    m_dPrice;
    private Timestamp m_oTimestamp;

    public CCoinInfo(String oRawData)
    {
        if (oRawData != null)
        {
            // Model: "id":"bitcoin","symbol":"btc","name":"Bitcoin",
            String oTimeStr = null;
            String oPrice   = null;
            int    iStart   = oRawData.indexOf("name") + 7;
            int    iEnd     = (iStart < 0)? 0 : oRawData.indexOf('"', iStart);

            if ((iStart >= 0)&&(iStart < iEnd))
                m_oName = oRawData.substring(iStart, iEnd);

            iStart = oRawData.indexOf("symbol") + 9;
            iEnd   = (iStart < 0)? 0 : oRawData.indexOf('"', iStart);

            if ((iStart >= 0)&&(iStart < iEnd))
                m_oSymbol = oRawData.substring(iStart, iEnd).toUpperCase();

            // Model: 		"last_updated":"2021-03-14T15:39:31.591Z",

            iStart = oRawData.indexOf("last_updated") + 15;
            iEnd   = (iStart < 0)? 0 : oRawData.indexOf('"', iStart);

            if ((iStart >= 0)&&(iStart < iEnd))
            {
                oTimeStr = oRawData.substring(iStart, iEnd).toUpperCase();

                if (oTimeStr != null)
                {
                    SimpleDateFormat oSDateFmt   = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    Date             oParsedDate = null;
                    try
                    {
                        oParsedDate  = oSDateFmt.parse(oTimeStr);
                        m_oTimestamp = new java.sql.Timestamp(oParsedDate.getTime());

                    }
                    catch (ParseException e)
                    {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            // Model: "current_price":220335

            iStart   = oRawData.indexOf("current_price") + 15;
            iEnd     = (iStart < 0)? 0 : oRawData.indexOf(',', iStart);
            oPrice   = oRawData.substring(iStart, iEnd);
            m_dPrice = Double.parseDouble(oPrice);

            //iStart      = oRawData.indexOf("current_price");
            //iStart      = GetOpenBrace(iStart, oRawData);
            //oPrice      = oRawData.substring(iStart);
            //iStart      = oPrice.indexOf("usd") + 5;
            //iEnd        = (iStart < 0)? 0 : oPrice.indexOf(',', iStart);
            //oPrice      = oPrice.substring(iStart, iEnd);
            //m_dPriceUSD = Double.parseDouble(oPrice);
        }
    }
    public String Name()
    {
        return m_oName;
    }
    public String Symbol()
    {
        return m_oSymbol;
    }
    public double Price()
    {
        return m_dPrice;
    }
    public String InfoString()
    {
        return String.join(",", Symbol(), TimeStamp().toString(), String.valueOf(Price()));
    }
    public Timestamp TimeStamp()
    {
        return m_oTimestamp;
    }
    static int GetOpenBrace(int iStart, String oData)
    {
        return oData.indexOf('{', iStart);
    }
    static int MatchBrace(int iStartPos, String oData)
    {
        int     iPos   = iStartPos;
        int     iDepth = 0;
        boolean bExit  = false;

        if (oData.codePointAt(iPos) == '{')
        {
            do
            {
                iPos++;

                int iNextOpenBrace  = oData.indexOf('{', iPos);
                int iNextCloseBrace = oData.indexOf('}', iPos);

                if (iNextCloseBrace < 0)
                {
                    iPos  = -1;
                    bExit = true;
                }
                else if (iNextOpenBrace < 0)
                {
                    iPos  = iNextCloseBrace;
                    bExit = (iDepth == 0);
                    iDepth--;
                }
                else if (iNextOpenBrace < iNextCloseBrace)
                {
                    iDepth++;
                    iPos = iNextOpenBrace;
                }
                else // Assume (iNextCloseBrace < iNextOpenBrace)
                {
                    iPos  = iNextCloseBrace;
                    bExit = (iDepth == 0);
                    iDepth--;
                }
            }
            while (!bExit);
        }
        else if (oData.codePointAt(iPos) == '}')
        {

        }
        return iPos;
    }
}
