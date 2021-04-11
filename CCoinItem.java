package com.example.coininverst;

import android.widget.ImageView;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static java.time.ZoneOffset.UTC;

public class CCoinItem
{
    public  String          Name;
    public  String          Symbol;
    public  String          Rank;
    public  String          Value;
    public  Timestamp       Time ;
    private CShortTermStats Stats;
    public  double          value;
    public  int             rank ;
    public  String          ImgPath;
    private static String   ImportTimeZone = "UTC";

    public CCoinItem (String name, String symbol, int iRank, double dValue, Timestamp oTime, String oLogo)
    {
        this.Name    = name;
        this.Symbol  = symbol;
        this.value   = dValue;
        this.rank    = iRank;
        this.Rank    = String.format("%d", iRank);
        this.Value   = ValToString(dValue);
        this.Time    = oTime;
        this.ImgPath = oLogo;
        this.Stats   = new CShortTermStats();
    }
    static String ValToString(double dValue)
    {
        return (dValue < 1.0)? String.format("%.4f", dValue) : String.format("%.2f", dValue);
    }

    static String ConvertToLocalTime(Timestamp oTime, String oFormat)
    {
        // SimpleDateFormat oDateFmt = new SimpleDateFormat(oFormat, Locale.ENGLISH);
        // oDateFmt.setTimeZone(TimeZone.getTimeZone(ImportTimeZone));
        // oDateFmt.setTimeZone(TimeZone.getDefault());
        // return oDateFmt.format(oTime);

        int iHours   =  oTime.getHours();
        int iMinutes =  oTime.getMinutes();
        int iUtcHrs  =  oTime.getTimezoneOffset() / 60;

        return String.format("%02d:%02d", iHours - iUtcHrs, iMinutes);
    }
    public void NewValue(double dValue, Timestamp oTime)
    {
        synchronized (this.Stats)
        {
            this.Stats.AddHistory(CurrValue());
            this.Time = oTime;
            this.value = dValue;
            this.Value = ValToString(dValue);
        }
    }
    public CShortTermStats GetStats()
    {
        CShortTermStats oOut = null;

        synchronized (this.Stats)
        {
            oOut = Stats.CloneItself();
            oOut.History.add(CurrValue());
        }

        return oOut;
    }
    private CStatSample CurrValue()
    {
        return new CStatSample(this.Time, this.value);
    }
    class CShortTermStats
    {
        public boolean                Valid;
        public ArrayList<CStatSample> History;
        public final int              Capacity;

        public  CShortTermStats(int iCapacity)
        {
            Valid    = false;
            Capacity = iCapacity;
            History  = new ArrayList<>();
        }
        public  CShortTermStats()
        {
            Valid    = false;
            Capacity = 20;
            History  = new ArrayList<>();
        }
        public CShortTermStats CloneItself()
        {
            CShortTermStats oNew = new CShortTermStats(Capacity);
            if (Valid)
            {
                oNew.History.addAll(History);
                oNew.Valid = true;
            }
            return oNew;
        }
        public double Performance(double dPeriodSec)
        {
            return 0;
        }
        public double PeriodMaxSec()
        {
            return 0;
        }
        public void AddHistory(CStatSample oSample)
        {
            if (History.size() >= Capacity)
            {
                History.remove(0); // Remove older
            }
            History.add(oSample);
            Valid = History.size() >= 2;
        }
    }
    public class CStatSample
    {
        public double    Price;
        public Timestamp Time ;

        public CStatSample(Timestamp oTime, double dPrice)
        {
            Time  = oTime;
            Price = dPrice;
        }
        public String ToString()
        {
            return String.join(",", Time.toString(), String.valueOf(Price));
        }
    }
}
