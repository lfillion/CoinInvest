package com.example.coininverst;

import android.os.Build;
import android.widget.ImageView;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import androidx.annotation.RequiresApi;

import static java.time.ZoneOffset.UTC;

public class CCoinItem
{
    public enum AlertUnits { Dollar, Percent, PercentRelative }

    public  String          Name;
    public  String          Symbol;
    public  String          Rank;
    public  String          Value;
    public  Timestamp       Time ;
    public  AlertUnits      AlertUnit;
    public  double          AlertDollar;
    private CShortTermStats Stats;
    public  double          value;
    public  int             rank ;
    public  String          ImgPath;
    private static String   ImportTimeZone = "UTC";
    public  boolean         LocalTime;
    public  boolean         AlertTriggered;
    public  String          AlertTitle;
    public  String          AlertMessage;
    public  boolean         AlertEnabled;
    public  double          AlertPercent;
    public  boolean         Favorite;

    private double  m_dAlertReference;
    private boolean m_bAlertFloatingRef;

    public CCoinItem (CCoinInfo oCoinInfo)
    {
        this.Name      = oCoinInfo.Name();
        this.Symbol    = oCoinInfo.Symbol();
        this.rank      = oCoinInfo.Rank();
        this.Rank      = String.format("%d", this.rank);
        this.value     = oCoinInfo.Price();
        this.Value     = ValToString(this.value);
        this.Time      = oCoinInfo.TimeStamp();
        this.LocalTime = oCoinInfo.IsLocalTime();
        this.ImgPath   = oCoinInfo.ImgPath();
        this.Stats     = new CShortTermStats();
        this.Favorite  = false;

        ResetAlert();
    }
    public CCoinItem (String name, String symbol, int iRank, double dValue, Timestamp oTime, boolean bLocalTime, String oLogo)
    {
        this.Name      = name;
        this.Symbol    = symbol;
        this.value     = dValue;
        this.rank      = iRank;
        this.Rank      = String.format("%d", iRank);
        this.Value     = ValToString(dValue);
        this.Time      = oTime;
        this.LocalTime = bLocalTime;
        this.ImgPath   = oLogo;
        this.Stats     = new CShortTermStats();
        this.Favorite  = false;

        ResetAlert();
    }
    public CCoinItem CloneItself()
    {
        return new CCoinItem(Name, Symbol, rank, value, Time, LocalTime, ImgPath);
    }
    private void ResetAlert()
    {
        AlertPercent        = 0;
        AlertDollar         = 0;
        AlertUnit           = AlertUnits.Percent;
        m_dAlertReference   = 0;
        m_bAlertFloatingRef = false;
        AlertEnabled        = false;
        AlertTriggered      = false;
        AlertTitle          = null;
        AlertMessage        = null;
    }
    private void AlertDisable()
    {
        AlertEnabled = false;
    }
    public void NewRank(int iRank)
    {
        this.rank = iRank;
        this.Rank = String.format("%d", iRank);
    }
    static String ValToString(double dValue)
    {
        return (dValue < 1.0)? String.format("%.4f", dValue) : String.format("%.2f", dValue);
    }

    static Timestamp ToLocalTime(Timestamp oUTC)
    {
        long lOffsetMSec = oUTC.getTimezoneOffset() * 60 * 1000;

        return new Timestamp(oUTC.getTime() + lOffsetMSec);
    }
    static String ConvertToLocalTime(Timestamp oTime)
    {
        // SimpleDateFormat oDateFmt = new SimpleDateFormat(oFormat, Locale.ENGLISH);
        // oDateFmt.setTimeZone(TimeZone.getTimeZone(ImportTimeZone));
        // oDateFmt.setTimeZone(TimeZone.getDefault());
        // return oDateFmt.format(oTime);

        int iHours   =  oTime.getHours();
        int iMinutes =  oTime.getMinutes();
        int iUtcHrs  =  oTime.getTimezoneOffset() / 60;

        if (iUtcHrs > iHours)
            iHours += 24;

        return String.format("%02d:%02d", iHours - iUtcHrs, iMinutes);
    }
    static String TimeToString(Timestamp oTime)
    {
        int iHours   =  oTime.getHours();
        int iMinutes =  oTime.getMinutes();

        return String.format("%02d:%02d", iHours, iMinutes);
    }
    public boolean NewValue(double dValue, Timestamp oTime)
    {
        boolean bSpecialEvent = false;

        synchronized (this.Stats)
        {
            this.Stats.AddHistory(CurrValue());
            this.Time  = oTime;
            this.value = dValue;
            this.Value = ValToString(dValue);

            if (AlertEnabled && m_bAlertFloatingRef)
            {
                if (AlertPercent < 0)
                {
                    if (dValue > m_dAlertReference)
                        m_dAlertReference = dValue;
                }
                else
                {
                    if (dValue < m_dAlertReference)
                        m_dAlertReference = dValue;
                }
            }
            bSpecialEvent = CheckAlert();
        }
        return bSpecialEvent;
    }
    public CShortTermStats GetStats()
    {
        CShortTermStats oOut = null;

        synchronized (this.Stats)
        {
            oOut = Stats.CloneItself();
            oOut.AddHistory(CurrValue());
        }

        return oOut;
    }
    private CStatSample CurrValue()
    {
        return new CStatSample(this.Time, this.value);
    }

    public static CStatSample CreateSample(String oToImport)
    {
        CStatSample oOut    = null;
        String[]    aoSplit = oToImport.split("#");

        if (aoSplit.length == 2)
        {
            try
            {
                long   lTime  = Long.parseLong(aoSplit[0]);
                double dPrice = Double.parseDouble(aoSplit[1]);

                oOut = new CCoinItem.CStatSample(new Timestamp(lTime), dPrice);
            }
            catch (Exception ignored)
            {
            }
        }
        return oOut;
    }

    private boolean CheckAlert()
    {
        boolean bEvent = false;

        if (AlertEnabled && !AlertTriggered)
        {
            if (m_dAlertReference > 0)
            {
                double dDiffPercent = 100.0 * (this.value - m_dAlertReference) / m_dAlertReference;
                String oVerbe       = "";

                if (AlertPercent < 0)
                {
                    AlertTriggered = dDiffPercent < AlertPercent;
                    oVerbe         = "drop to";
                }
                else
                {
                    AlertTriggered = dDiffPercent > AlertPercent;
                    oVerbe         = "rise to";
                }
                if (AlertTriggered)
                    AlertMessage = Name + " Price " + oVerbe + " $" + this.Value;
            }
            bEvent = AlertTriggered;
        }
        return bEvent;
    }
    public boolean SetAlertPercent(double dAlertPercent, double dFromValue)
    {
        if (dAlertPercent == 0)
            ResetAlert();
        else
        {
            m_dAlertReference   = dFromValue;
            m_bAlertFloatingRef = false;
            AlertPercent        = dAlertPercent;
            AlertDollar         = 0;
            AlertUnit           = AlertUnits.Percent;
            AlertEnabled        = m_dAlertReference > 0;
            AlertTitle          = (dAlertPercent < 0)? "Drop Price Alert!" : "Up Price Alert!";
        }
        return AlertEnabled;
    }
    public void SetAlertPercentRelative(double dAlertPercent, double dFromValue)
    {
        if (SetAlertPercent(dAlertPercent, dFromValue))
        {
            m_bAlertFloatingRef = true;
            AlertUnit           = AlertUnits.PercentRelative;
        }
    }
    public void SetAlertDollar(double dAlertDollar)
    {
        if (dAlertDollar == 0)
            ResetAlert();
        else if (SetAlertPercent(((dAlertDollar - value)/value) * 100.0, value))
        {
            AlertUnit   = AlertUnits.Dollar;
            AlertDollar = dAlertDollar;
        }
    }
    public String FavoriteToString()
    {
        String oSep = "#";

        return "Favorite" + oSep + Name + oSep + (Favorite? "Yes" : "No");
    }
    public void SetFavorite(String oInLine)
    {
        if (oInLine.contains("Favorite") && oInLine.contains(Name))
        {
            String   oSep    = "#";
            String[] aoSplit = oInLine.split(oSep);

            if (aoSplit.length >= 3)
                Favorite = aoSplit[2].equals("Yes");
        }
    }
    public String AlertToString()
    {
        String oSep = "#";
        String oOut = "Alert" + oSep + Name + oSep;

        if (AlertEnabled == false)
            oOut += "Disable";
        else if (AlertUnit == AlertUnits.Dollar)
            oOut += "Enable" + oSep + String.format("%6.10f", AlertDollar) + oSep + "$";
        else if (AlertUnit == AlertUnits.Percent)
            oOut += "Enable" + oSep + String.format("%2.5f(%f)", AlertPercent, m_dAlertReference) + oSep + "%";
        else if (AlertUnit == AlertUnits.PercentRelative)
            oOut += "Enable" + oSep + String.format("%2.5f(%f)", AlertPercent, m_dAlertReference) + oSep + "~%";
        else
            oOut += "ERROR:Unknown state!";

        return oOut;
    }
    public boolean EvaluateAlert(String oInLine)
    {
        boolean bUpdate = false;

        if (AlertTriggered)
        {
            if (!AlertEnabled)
                ResetAlert();
        }
        else if (AlertEnabled)
        {
            if ((AlertUnit == AlertUnits.Dollar)||(AlertUnit == AlertUnits.Percent))
                bUpdate = true;
            else
            {
                CCoinItem oClone = CloneItself();

                oClone.SetAlert(oInLine);

                if ((oClone.AlertUnit == AlertUnits.Dollar) || (oClone.AlertUnit == AlertUnits.Percent))
                    bUpdate = true;
                else if (oClone.AlertPercent != AlertPercent)
                    bUpdate = true;
                else if (AlertPercent > 0)
                    bUpdate = m_dAlertReference > oClone.m_dAlertReference;
                else // Assume (AlertPercent < 0)
                    bUpdate = m_dAlertReference < oClone.m_dAlertReference;
            }
        }
        else
            bUpdate = true;

        return bUpdate;
    }
    public void SetAlert(String oInLine)
    {
        if (oInLine.contains("Alert") && oInLine.contains(Name))
        {
            String   oSep    = "#";
            String[] aoSplit = oInLine.split(oSep);

            if (aoSplit.length >= 3)
            {
                ResetAlert();

                if (aoSplit[2].equals("Enable") && (aoSplit.length >= 5))
                {
                    String oPercent = aoSplit[3].substring(0, aoSplit[3].indexOf('('));
                    String oRefer   = aoSplit[3].substring(aoSplit[3].indexOf('(') + 1, aoSplit[3].indexOf(')'));
                    double dValue   = Double.parseDouble(oPercent);
                    double dRefer   = Double.parseDouble(oRefer);

                    if (aoSplit[4].equals("~%"))
                    {
                        if (!AlertEnabled || (AlertUnit != AlertUnits.PercentRelative))
                            SetAlertPercentRelative(dValue, dRefer);
                        //else, already in alert mode (avoid to change its value).
                    }
                    else if (aoSplit[4].equals("%"))
                        SetAlertPercent(dValue, dRefer);
                    else if (aoSplit[4].equals("$"))
                        SetAlertDollar(dValue);
                }
            }
        }
    }
    public void ClearOneShotAlert()
    {
        AlertDisable();
    }
    class CShortTermStats
    {
        public  boolean                Valid;
        public  ArrayList<CStatSample> History;
        public  ArrayList<CStatSample> Filtered;
        public  ArrayList<Double>      AnalysisData;
        public  final int              Capacity;
        public  final int              Minimal   = 12;
        private final int              FilterTap = 3;
        public  double                 Performance;
        public  double                 GrowthPercent;
        public  double                 SwingIndicator;
        public  double                 TopValue    ;
        public  double                 BotValue    ;
        public  double                 Average     ;
        public  double                 StdDeviation;
        public  long                   PeriodMSec  ;
        public  int                    NbSamples   ;
        public  final int              MajorRev  = 1;
        public  final int              MinorRev  = 0;

        private long                   m_lPeriodStart = 0;
        private long                   m_lPeriodEnd   = 0;
        private final String           DateTimeFormat = "yyyy.MM.dd.HH.mm.ss";

        public  CShortTermStats(int iCapacity)
        {
            ResetStats();
            Capacity     = (iCapacity < (Minimal + FilterTap))? (Minimal + FilterTap) : iCapacity;
        }
        public  CShortTermStats()
        {
            ResetStats();
            Capacity     = 72;
        }
        public String Revison()
        {
            return String.format("%02d.%02d", MajorRev, MinorRev);
        }
        private void ResetStats()
        {
            Valid          = false;
            Performance    = 0;
            GrowthPercent  = 0;
            SwingIndicator = 0;
            History        = new ArrayList<>();
            Filtered       = new ArrayList<>();
            AnalysisData   = new ArrayList<>();
            TopValue       =  Double.MIN_VALUE;
            BotValue       =  Double.MAX_VALUE;

            ResetBasicStats();
        }
        private void ResetBasicStats()
        {
            //TestFormatStat();
            TopValue       = Integer.MIN_VALUE;
            BotValue       = Integer.MAX_VALUE;
            Average        = -1.0;
            StdDeviation   = -1.0;
            PeriodMSec     = 0;
            NbSamples      = 0;
            m_lPeriodStart = 0;
            m_lPeriodEnd   = 0;
        }
        private void TestFormatStat()
        {
            Date oDate = new Date();
            m_lPeriodEnd   = oDate.getTime();
            m_lPeriodStart = m_lPeriodEnd - (3600 * 1000);
            TopValue       = 10.0;
            BotValue       =  5.5;
            Average        =  7.7;
            StdDeviation   =  2.3;
            PeriodMSec     = m_lPeriodEnd - m_lPeriodStart;
            NbSamples      = 72;

            String oTest = FormatStat();
            boolean bLogStat = NextPeriod(oTest);
        }
        private String FormatTime(long lTime)
        {
            return new SimpleDateFormat(DateTimeFormat).format(new Timestamp(lTime));
        }
        public String FormatStat()
        {
            String oOut = "Not Available";

            if (NbSamples > 0)
                oOut = "Start:"   + FormatTime(m_lPeriodStart)       + ","
                     + "End:"     + FormatTime(m_lPeriodEnd  )       + ","
                     + "Min:"     + String.format("%6.3f", BotValue) + ","
                     + "Max:"     + String.format("%6.3f", TopValue) + ","
                     + "Average:" + String.format("%6.3f", Average ) + ","
                     + "StdDev:"  + String.format("%6.6f", StdDeviation);

            return oOut;
        }
        public boolean NextPeriod(String oLastPeriod)
        {
            boolean bAnswer = false;

            if (oLastPeriod.contains("End:"))
            {
                SimpleDateFormat oFormat       = new SimpleDateFormat(DateTimeFormat);
                int              iExtractStart = oLastPeriod.indexOf("End:") + 4;
                int              iExtractEnd   = oLastPeriod.indexOf(",", iExtractStart);
                String           oEndPeriod    = oLastPeriod.substring(iExtractStart, iExtractEnd);

                try
                {
                    Date oEnd     = oFormat.parse(oEndPeriod);
                    long lEndMSec = oEnd.getTime();

                    if (m_lPeriodStart > oEnd.getTime())
                        bAnswer = true;
                }
                catch (Exception oEx)
                {
                }
            }
            return  bAnswer;
        }
        public boolean StatAvailable()
        {
            return NbSamples > 0;
        }
        public CShortTermStats CloneItself()
        {
            CShortTermStats oNew = new CShortTermStats(Capacity);
            if (Valid)
            {
                oNew.History.addAll(this.History);
                oNew.Valid = this.Valid;
                oNew.Filtered.addAll(this.Filtered);
                oNew.Performance    = this.Performance;
                oNew.GrowthPercent  = this.GrowthPercent;
                oNew.SwingIndicator = this.SwingIndicator;
                oNew.NbSamples      = this.NbSamples;
                oNew.TopValue       = this.TopValue;
                oNew.BotValue       = this.BotValue;
                oNew.Average        = this.Average ;
                oNew.StdDeviation   = this.StdDeviation;
                oNew.m_lPeriodStart = this.m_lPeriodStart;
                oNew.m_lPeriodEnd   = this.m_lPeriodEnd;
                oNew.PeriodMSec     = this.PeriodMSec;
            }
            return oNew;
        }
        public void AddHistory(CStatSample oSample)
        {
            int         iLastIdx  = History.size() - 1;
            CStatSample oLastItem = (iLastIdx < 0)? null : History.get(iLastIdx);

            if ((oLastItem != null) && (Math.abs(oSample.Time.getTime() -oLastItem.Time.getTime()) < 30000)) // < 30 sec
            { // Replace last...
                History.remove(iLastIdx);
            }
            else if (History.size() >= Capacity)
            {
                History.remove(0); // Remove older
            }
            History.add(oSample);
            RefreshStats();
            Valid = History.size() >= 2;
        }
        private void Filter()
        {
            if (History.size() >= (Minimal + FilterTap))
            {
                Filtered.clear();

                for (int iIdx = 0; iIdx < History.size() - FilterTap; iIdx++)
                {
                    double dTot = 0;
                    for (int iIdx2 = iIdx; iIdx2 < (iIdx + FilterTap); iIdx2++)
                        dTot += History.get(iIdx2).Price;
                    Filtered.add(new CStatSample(History.get(iIdx + (FilterTap/2)).Time, dTot / FilterTap));
                }
            }
        }
        private double RelativeGain(CStatSample oNew, CStatSample oOld, double dRefPrice)
        {
            double dOut = 0;

            try
            {
                dOut = (100.0 * Gain(oNew.Price, oOld.Price, dRefPrice)) / (oNew.Time.getTime() - oOld.Time.getTime());
            }
            catch (Exception oEx)
            {
            }
            return dOut;
        }
        private double Gain(double dNew, double dOld, double dRefPrice)
        {
            double dOut = 0;

            try
            {
                dOut = (dNew - dOld)/dRefPrice;
            }
            catch (Exception oEx)
            {
            }
            return dOut;
        }
        private void BasicStats()
        {
            ResetBasicStats();

            if (History.size() == Capacity)
            {
                double dTot = 0;

                for (CStatSample oItem : History)
                {
                    if (m_lPeriodStart == 0)
                        m_lPeriodStart = oItem.Time.getTime();
                    else if (oItem.Time.getTime() < m_lPeriodStart)
                        m_lPeriodStart = oItem.Time.getTime();

                    if (m_lPeriodEnd == 0)
                        m_lPeriodEnd = oItem.Time.getTime();
                    else if (oItem.Time.getTime() > m_lPeriodEnd)
                        m_lPeriodEnd = oItem.Time.getTime();

                    if (oItem.Price > TopValue)
                        TopValue = oItem.Price;
                    if (oItem.Price < BotValue)
                        BotValue = oItem.Price;

                    dTot += oItem.Price;
                }
                NbSamples  = History.size();
                Average    = dTot / NbSamples;
                PeriodMSec = m_lPeriodEnd - m_lPeriodStart;

                double dDev = 0;
                for (CStatSample oItem : History)
                {
                    dDev += Math.pow(oItem.Price - Average, 2);
                }
                StdDeviation = Math.sqrt(dDev / NbSamples);
            }
        }
        private void RefreshStats()
        {
            BasicStats();

            Filter();

            ArrayList<CStatSample> oWorkList = Filtered;

            int iSize = oWorkList.size();

            if (iSize >= Minimal)
            {
                CStatSample       oFirstSample =   oWorkList.get(0);
                CStatSample       oLastSample  =   oWorkList.get(iSize - 1);
                double            dFirstPrice  =   oWorkList.get(0).Price;
                double            dLastPrice   =   oWorkList.get(oWorkList.size()-1).Price;
                CLinearAmplifier  oLAmp        = new CLinearAmplifier(1.0, 2.0, oFirstSample.Time, oLastSample.Time);
                CLinearAmplifier  oSwingAmp    = new CLinearAmplifier(0.1, 100.0, oFirstSample.Time, oLastSample.Time);
                final double      dPerformAdj  = 1.0E6;
                final double      dSwingAdj    = 1.0E6;
                ArrayList<Double> oSwingData   = new ArrayList<Double>();

                AnalysisData.clear();
                Performance    = 0;
                GrowthPercent  = 0;
                SwingIndicator = 0;

                for (int iIdx = 0; (dFirstPrice > 0) && (iIdx < iSize - 1); iIdx++)
                {
                    double dDelta = RelativeGain(oWorkList.get(iIdx + 1), oWorkList.get(iIdx), dFirstPrice);

                    AnalysisData.add(dDelta * oLAmp.GetValue    (oWorkList.get(iIdx).Time));
                    oSwingData.add  (dDelta * oSwingAmp.GetValue(oWorkList.get(iIdx).Time));
                }

                double dTot = 0;
                for (Double dVal : AnalysisData)
                    dTot += dVal;

                if (AnalysisData.size() > 0)
                    Performance = (dPerformAdj * dTot)/(double)AnalysisData.size();

                double dSwingTot = 0;
                for (Double dVal : oSwingData)
                    dSwingTot += dVal;

                if (oSwingData.size() > 0)
                    SwingIndicator = (dSwingAdj * dSwingTot)/(double)oSwingData.size();

                GrowthPercent = Gain(dLastPrice, dFirstPrice, dFirstPrice) * 100.0;
            }
        }
    }
    public static class CStatSample
    {
        public double    Price;
        public Timestamp Time ;

        public CStatSample(Timestamp oTime, double dPrice)
        {
            Time  = oTime;
            Price = dPrice;
        }
        @RequiresApi(api = Build.VERSION_CODES.O)
        public String ToString()
        {
            String oTime  = Time.toString();
            String oPrice = (Price >= 100)? String.format("%6.2f", Price)
                                          : String.format("%2.6f", Price);

            oTime = oTime.substring(0, oTime.indexOf('.'));

            return String.join(" -- ", oTime, oPrice);
        }
        public String ToExport()
        {
            long lTime = Time.getTime();

            return String.format("%d#%f", lTime, Price);
        }
    }
    public class CLinearAmplifier
    {
        private double    mdGainMin = 0;
        private double    mdGainMax = 0;
        private Timestamp moStart   = null;
        private Timestamp moEnd     = null;
        private double    mdSlope   = 1.0;

        public CLinearAmplifier(double dGainMin, double dGainMax, Timestamp oStart, Timestamp oEnd)
        {
            mdGainMin = dGainMin;
            mdGainMax = dGainMax;
            moStart   = oStart;
            moEnd     = oEnd;

            double dQuot = (double)(moEnd.getTime() - moStart.getTime());
            if (dQuot == 0)
                dQuot = 0.00000001; // Should not occur

            mdSlope = (mdGainMax - mdGainMin) / dQuot;
        }
        public double GetValue(Timestamp oTime)
        {   // y = mx + b
            return (mdSlope * (double)(oTime.getTime() - moStart.getTime())) + mdGainMin;
        }
    }
}
