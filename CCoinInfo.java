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
    private String    m_oName       = "";
    private String    m_oID         = "";
    private String    m_oSymbol     = "";
    private double    m_dPrice      = 0;
    private int       m_iRank       = 0;
    private Timestamp m_oTimestamp  = null;
    private String    m_oImageURL   = null;
    private String    m_oImageLocal = null;
    private boolean   m_bLocalTime  = false;

    public CCoinInfo(String oRawData, Timestamp oTimestamp)
    {
        if (oRawData != null)
        {
            // Model: "id":"bitcoin","symbol":"btc","name":"Bitcoin",
            String oTimeStr = null;
            String oPrice   = null;
            String oRank    = null;
            int    iStart   = oRawData.indexOf("name") + 7;
            int    iEnd     = (iStart < 0)? 0 : oRawData.indexOf('"', iStart);

            if ((iStart >= 0)&&(iStart < iEnd))
                m_oName = oRawData.substring(iStart, iEnd);

            iStart = oRawData.indexOf("id") + 5;
            iEnd   = (iStart < 0)? 0 : oRawData.indexOf('"', iStart);

            if ((iStart >= 0)&&(iStart < iEnd))
                m_oID = oRawData.substring(iStart, iEnd);

            iStart = oRawData.indexOf("symbol") + 9;
            iEnd   = (iStart < 0)? 0 : oRawData.indexOf('"', iStart);

            if ((iStart >= 0)&&(iStart < iEnd))
                m_oSymbol = oRawData.substring(iStart, iEnd).toUpperCase();

            // Model: "market_cap_rank":1,
            iStart  = oRawData.indexOf("market_cap_rank") + 17;
            iEnd    = (iStart < 0)? 0 : oRawData.indexOf(',', iStart);
            oRank   = oRawData.substring(iStart, iEnd);
            m_iRank = Integer.parseInt(oRank);


            m_bLocalTime = (oTimestamp != null);

            if (oTimestamp != null)
            {
                m_oTimestamp = oTimestamp;
            }
            else
            {
                // Model: 		"last_updated":"2021-03-14T15:39:31.591Z",
                iStart = oRawData.indexOf("last_updated") + 15;
                iEnd   = (iStart < 0)? 0 : oRawData.indexOf('"', iStart);

                if ((iStart >= 0)&&(iStart < iEnd))
                {
                    oTimeStr = oRawData.substring(iStart, iEnd).toUpperCase();

                    if (oTimeStr != null)
                    {
                        SimpleDateFormat oSDateFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        Date oParsedDate = null;
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
            }
            // Model: "current_price":220335

            iStart   = oRawData.indexOf("current_price") + 15;
            iEnd     = (iStart < 0)? 0 : oRawData.indexOf(',', iStart);
            oPrice   = oRawData.substring(iStart, iEnd);
            m_dPrice = Double.parseDouble(oPrice);

            // Model: "image":"https://blabla.png?99999",
            iStart      = oRawData.indexOf("image") + 8;
            iEnd        = (iStart < 0)? 0 : oRawData.indexOf('?', iStart);
            m_oImageURL = oRawData.substring(iStart, iEnd).replace("large", "small");
        }
    }
    public String    Name        () { return m_oName      ; }
    public String    ID          () { return m_oID        ; }
    public String    Symbol      () { return m_oSymbol    ; }
    public double    Price       () { return m_dPrice     ; }
    public int       Rank        () { return m_iRank      ; }
    public String    ImgURL      () { return m_oImageURL  ; }
    public String    ImgPath     () { return m_oImageLocal; }
    public Timestamp TimeStamp   () { return m_oTimestamp ; }
    public boolean   IsLocalTime () { return m_bLocalTime ; }

    public void SetImgPath(String oPath)
    {
        m_oImageLocal = oPath;
    }
    public String InfoString()
    {
        return String.join(",", Symbol(), TimeStamp().toString(), String.valueOf(Price()));
    }

    static int GetOpenBrace(int iStart, String oData)
    {
        return GetOpenBlock('{', iStart, oData);
    }
    static int GetOpenBlock(char cOpen, int iStart, String oData)
    {
        return oData.indexOf(cOpen, iStart);
    }
    static int MatchBrace(int iStartPos, String oData)
    {
        return MatchBlock('{', '}', iStartPos, oData);
    }
    static int MatchBlock(char cOpen, char cClose, int iStartPos, String oData)
    {
        int     iPos   = iStartPos;
        int     iDepth = 0;
        boolean bExit  = false;

        if (oData.codePointAt(iPos) == cOpen)
        {
            do
            {
                iPos++;

                int iNextOpenBrace  = oData.indexOf(cOpen , iPos);
                int iNextCloseBrace = oData.indexOf(cClose, iPos);

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
        else if (oData.codePointAt(iPos) == cClose)
        {

        }
        return iPos;
    }
}
