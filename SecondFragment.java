package com.example.coininverst;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class SecondFragment extends Fragment {
    public static String         TAG                = "GraphFragment";
    private IHostActivityOptions mHostOptions       = null;
    private Button               mBackButton        = null;
    private ImageView            mGraphicArea       = null;
    private ImageView            mLogoImgView       = null;
    private TextView             mRankTextView      = null;
    private TextView             mSymbTextView      = null;
    private TextView             mNameTextView      = null;
    private TextView             mValTextView       = null;
    private TextView             mGrowthTextView    = null;
    private TextView             mPerfTextView      = null;
    private TextView             mSwingTextView     = null;
    private CCoinItem            mCoinItem          = null;
    private ImageView            mAnalysisImgView   = null;
    private Switch               mPriceAlertSwitch  = null;
    private EditText             mAlertEditText     = null;
    private Button               mAlertAcceptButton = null;

    public SecondFragment(IHostActivityOptions oHostOptions)
    {
        super();
        mHostOptions = oHostOptions;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        mBackButton        = (Button   ) getView().findViewById(R.id.id_BackViewButton);
        mGraphicArea       = (ImageView) getView().findViewById(R.id.id_ImageView);
        mLogoImgView       = (ImageView) getView().findViewById(R.id.id_2ndLogo);
        mRankTextView      = (TextView ) getView().findViewById(R.id.id_2ndCoinRank);
        mSymbTextView      = (TextView ) getView().findViewById(R.id.id_2ndCoinSymbol);
        mNameTextView      = (TextView ) getView().findViewById(R.id.id_2ndCoinName);
        mValTextView       = (TextView ) getView().findViewById(R.id.id_2ndCoinValue);
        mGrowthTextView    = (TextView ) getView().findViewById(R.id.id_GrowthValue);
        mPerfTextView      = (TextView ) getView().findViewById(R.id.id_PerformanceValue);
        mSwingTextView     = (TextView ) getView().findViewById(R.id.id_SwingValue);
        mAnalysisImgView   = (ImageView) getView().findViewById(R.id.id_AnalysisImageView);
        mPriceAlertSwitch  = (Switch   ) getView().findViewById(R.id.id_PriceAlertSwitch);
        mAlertEditText     = (EditText ) getView().findViewById(R.id.id_AlertTriggerPercent);
        mAlertAcceptButton = (Button   ) getView().findViewById(R.id.id_AcceptButton);

        if (mCoinItem != null)
        {
            mRankTextView.setText(mCoinItem.Rank);
            mSymbTextView.setText(mCoinItem.Symbol);
            mNameTextView.setText(mCoinItem.Name);
            mValTextView.setText(mCoinItem.Value);

            try
            {
                File            oFile     = new File(mCoinItem.ImgPath);
                FileInputStream oInStream = new FileInputStream(oFile);
                mLogoImgView.setImageBitmap(BitmapFactory.decodeStream(oInStream));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        mBackButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                mHostOptions.BackButtonClick();
            }
        });

        mPriceAlertSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                boolean bEnable = isChecked;

                mAlertEditText.setEnabled(bEnable);
                mAlertAcceptButton.setEnabled(bEnable);
                mAlertAcceptButton.setTextColor(Color.WHITE);
            }
        });

        mAlertAcceptButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view1)
            {
                mAlertAcceptButton.setTextColor(Color.GREEN);

                String oValue = mAlertEditText.getText().toString();
                double dValue = Double.parseDouble(oValue);

                if (mCoinItem != null)
                {
                    mCoinItem.SetAlertPercent(dValue);
                }
            }
        });
        int iColor1 = Color.RED;
        int iColor2 = Color.BLUE;

        if ((mCoinItem == null) || !mCoinItem.GetStats().Valid)
        {
            mCoinItem = PseudoCoin();
            iColor1   = Color.BLACK;
            iColor2   = Color.GRAY;
        }

        DrawGraphic(mCoinItem, iColor1, iColor2);

        String oPerfValue = String.format("%2.6f", mCoinItem.GetStats().Performance);
        mPerfTextView.setText(oPerfValue);

        String oGrowthValue = String.format("%3.2f %%", mCoinItem.GetStats().GrowthPercent);
        mGrowthTextView.setText(oGrowthValue);

        String oSwingValue = String.format("%2.6f", mCoinItem.GetStats().SwingIndicator);
        mSwingTextView.setText(oSwingValue);

        DrawAnalysisData(mCoinItem);
    }
    @Override
    public void onResume()
    {
        super.onResume();

        mPriceAlertSwitch.setChecked(false);
        mAlertAcceptButton.setTextColor(Color.WHITE);
        mAlertAcceptButton.setEnabled(false);
        if ((mCoinItem == null)|| !mCoinItem.AlertEnabled)
            mAlertEditText.getText().clear();
        else
            mAlertEditText.setText(String.format("%3.2f", mCoinItem.AlertPercent));
        mAlertEditText.setEnabled(false);
    }
    public void SetCoinItem(CCoinItem oToShow)
    {
        mCoinItem = oToShow;
    }

    private CCoinItem PseudoCoin()
    {
        // (1900 + 101)= 2001 sept 11 8h30...
        CCoinItem oPseudo = new CCoinItem("PSEUDO", "NUL", 0, 50.0, new Timestamp(101,9,11,8,25,0,0), true, null);
        oPseudo.NewValue(58.0, new Timestamp(101,9,11,8,30,0,0));
        oPseudo.NewValue(60.0, new Timestamp(101,9,11,8,35,0,0));
        oPseudo.NewValue(61.0, new Timestamp(101,9,11,8,40,0,0));
        oPseudo.NewValue(61.5, new Timestamp(101,9,11,8,45,0,0));
        oPseudo.NewValue(61.0, new Timestamp(101,9,11,8,50,0,0));
        oPseudo.NewValue(60.9, new Timestamp(101,9,11,8,55,0,0));
        oPseudo.NewValue(60.2, new Timestamp(101,9,11,9,00,0,0));
        oPseudo.NewValue(60.0, new Timestamp(101,9,11,9,05,0,0));
        oPseudo.NewValue(59.8, new Timestamp(101,9,11,9,10,0,0));
        oPseudo.NewValue(60.4, new Timestamp(101,9,11,9,15,0,0));
        oPseudo.NewValue(61.0, new Timestamp(101,9,11,9,20,0,0));
        oPseudo.NewValue(61.9, new Timestamp(101,9,11,9,25,0,0));
        oPseudo.NewValue(62.2, new Timestamp(101,9,11,9,30,0,0));
        oPseudo.NewValue(63.1, new Timestamp(101,9,11,9,35,0,0));
        oPseudo.NewValue(63.5, new Timestamp(101,9,11,9,40,0,0));

        return oPseudo;
    }
    private void DrawAnalysisData(CCoinItem oCoin)
    {
        int    iTotalWidth  = 600;
        int    iTotalHeight = 400;
        Bitmap oBmp         = Bitmap.createBitmap(iTotalWidth, iTotalHeight, Bitmap.Config.ARGB_8888);
        Canvas oCanvas      = new Canvas(oBmp);
        Paint  oPaint       = new Paint();

        ArrayList<Double> oData        = oCoin.GetStats().AnalysisData;
        ArrayList<PointF> oPointList   = new ArrayList<>();
        CGraphProperties  oGraphParams = new CGraphProperties(oCanvas);

        DrawGrid    (oCanvas, oGraphParams);
        DefineScale2(oGraphParams, oData);
        DrawYZero   (oCanvas, oGraphParams);

        int iXPos = 0;
        for (double dSample : oData)
        {
            oPointList.add(ToCoord(oGraphParams, iXPos++, dSample));
        }

        oPaint.setColor(Color.GRAY);
        DrawBars(oCanvas, oGraphParams, oPaint, oPointList);

        mAnalysisImgView.setImageBitmap(oBmp);
    }
    private void DrawGraphic(CCoinItem oCoin, int iColor, int iColor2)
    {
        int    iTotalWidth  = 600;
        int    iTotalHeight = 400;
        Bitmap oBmp         = Bitmap.createBitmap(iTotalWidth, iTotalHeight, Bitmap.Config.ARGB_8888);
        Canvas oCanvas      = new Canvas(oBmp);
        Paint  oPaint       = new Paint();

        ArrayList<CCoinItem.CStatSample> oLocalSamples = oCoin.GetStats().History;
        ArrayList<PointF>                oPointList    = new ArrayList<>();
        CGraphProperties                 oGraphParams  = new CGraphProperties(oCanvas);

        DrawGrid(oCanvas, oGraphParams);

        DefineScale(oGraphParams, oLocalSamples);

        DrawText(oCanvas, oGraphParams);

        for (CCoinItem.CStatSample oSample : oLocalSamples)
        {
            oPointList.add(ToCoord(oGraphParams, oSample));
        }

        oPaint.setColor(iColor);
        DrawVector(oCanvas, oPaint, oPointList);

        if (iColor2 != iColor)
        {
            ArrayList<CCoinItem.CStatSample> oFiltered = oCoin.GetStats().Filtered;
            oPointList.clear();

            for (CCoinItem.CStatSample oSample : oFiltered)
            {
                oPointList.add(ToCoord(oGraphParams, oSample));
            }

            oPaint.setColor(iColor2);
            DrawVector(oCanvas, oPaint, oPointList);
        }
        mGraphicArea.setImageBitmap(oBmp);
    }
    private void DrawGrid(Canvas oCanvas, CGraphProperties oGraphParams)
    {
        Paint oPaint = new Paint();

        oPaint.setColor (oGraphParams.GridColor);
        oCanvas.drawLine(oGraphParams.WorkingArea.left , oGraphParams.WorkingArea.top   , oGraphParams.WorkingArea.right, oGraphParams.WorkingArea.top   , oPaint);
        oCanvas.drawLine(oGraphParams.WorkingArea.right, oGraphParams.WorkingArea.top   , oGraphParams.WorkingArea.right, oGraphParams.WorkingArea.bottom, oPaint);
        oCanvas.drawLine(oGraphParams.WorkingArea.right, oGraphParams.WorkingArea.bottom, oGraphParams.WorkingArea.left , oGraphParams.WorkingArea.bottom, oPaint);
        oCanvas.drawLine(oGraphParams.WorkingArea.left , oGraphParams.WorkingArea.bottom, oGraphParams.WorkingArea.left , oGraphParams.WorkingArea.top   , oPaint);
    }
    private void DrawYZero(Canvas oCanevas, CGraphProperties oGraphParams)
    {
        Paint oPaint = new Paint();

        oPaint.setColor(oGraphParams.GridColor);
        PointF oP1 = new PointF();
        PointF oP2 = new PointF();

        oP1.x = oGraphParams.WorkingArea.left;
        oP1.y = oGraphParams.WorkingOrigin.y;
        oP2.x = oGraphParams.WorkingArea.right;
        oP2.y = oP1.y;

        oCanevas.drawLine(oP1.x, oP1.y, oP2.x, oP2.y, oPaint);
    }
    private void DrawText(Canvas oCanvas, CGraphProperties oGraphParams)
    {
        Paint oPaint = new Paint();
        float fTSize = 20f;

        String oYMinVal  = CCoinItem.ValToString(oGraphParams.YVMin);
        String oYMaxVal  = CCoinItem.ValToString(oGraphParams.YVMax);
        String oXMinTime = CCoinItem.TimeToString(new Timestamp(oGraphParams.XVMin));
        String oXMaxTime = CCoinItem.TimeToString(new Timestamp(oGraphParams.XVMax));

        if (  ((oYMinVal.length() > 4) && !oYMinVal.contains("."))
            ||((oYMaxVal.length() > 4) && !oYMaxVal.contains("."))
            ||(oYMinVal.length() > 5)
            ||(oYMaxVal.length() > 5)  )
            fTSize = 14f; //Todo: measure text size in pixels.

        oPaint.setColor(oGraphParams.GridColor);
        oPaint.setTextSize(fTSize);
        oCanvas.drawText(oYMaxVal , oGraphParams.WorkingArea.right + 5f, oGraphParams.WorkingArea.top   , oPaint);
        oCanvas.drawText(oYMinVal , oGraphParams.WorkingArea.right + 5f, oGraphParams.WorkingArea.bottom, oPaint);
        oCanvas.drawText(oXMinTime, oGraphParams.WorkingArea.left, oGraphParams.WorkingArea.bottom + 20f, oPaint);
        oCanvas.drawText(oXMaxTime, oGraphParams.WorkingArea.right - 50f, oGraphParams.WorkingArea.bottom + 20f, oPaint);
    }
    private void DefineScale(CGraphProperties oGraphParams, ArrayList<CCoinItem.CStatSample> oSampleList)
    {
        if (oSampleList.size() > 1)
        {
            Timestamp oStart  = null;
            Timestamp oEnd    = null;
            double    dMinVal = 0;
            double    dMaxVal = 0;

            dMinVal = dMaxVal = oSampleList.get(0).Price;
            oStart  = oEnd    = oSampleList.get(0).Time;

            for (CCoinItem.CStatSample oSample : oSampleList)
            {
                if (oSample.Time.getTime() < oStart.getTime())
                    oStart = oSample.Time;
                if (oSample.Time.getTime() > oEnd.getTime())
                    oEnd   = oSample.Time;

                if (oSample.Price > dMaxVal)
                    dMaxVal = oSample.Price;
                if (oSample.Price < dMinVal)
                    dMinVal = oSample.Price;
            }

            oGraphParams.SetScale(oStart.getTime(), oEnd.getTime(), dMinVal, dMaxVal);
        }
    }
    private void DefineScale2(CGraphProperties oGraphParams, ArrayList<Double> oSampleList)
    {
        if (oSampleList.size() > 1)
        {
            double    dMinVal = 0;
            double    dMaxVal = 0;

            dMinVal = dMaxVal = oSampleList.get(0);

            for (double dSample : oSampleList)
            {
                if (dSample > dMaxVal)
                    dMaxVal = dSample;
                if (dSample < dMinVal)
                    dMinVal = dSample;
            }

            oGraphParams.SetScale(0, oSampleList.size() - 1, dMinVal, dMaxVal);
            oGraphParams.RecenterOrigin();
        }
    }
    public PointF ToCoord(CGraphProperties oGraphParams, CCoinItem.CStatSample oSample)
    {
        PointF oOut = new PointF();
        double dX   = oSample.Time.getTime() - oGraphParams.XVOffset;
        double dY   = oSample.Price          - oGraphParams.YVOffset;

        oOut.x = oGraphParams.WorkingOrigin.x + (float)(dX / oGraphParams.XScale);
        oOut.y = oGraphParams.WorkingOrigin.y - (float)(dY / oGraphParams.YScale);

        return oOut;
    }
    public PointF ToCoord(CGraphProperties oGraphParams, int iXPos, double dValue)
    {
        PointF oOut = new PointF();

        oOut.x = oGraphParams.WorkingOrigin.x + (float)((double)iXPos / oGraphParams.XScale);
        oOut.y = oGraphParams.WorkingOrigin.y - (float)(       dValue / oGraphParams.YScale);

        return oOut;
    }
    public void DrawVector(Canvas oCanvas, Paint oPaint, ArrayList<PointF> oVector)
    {
        for (int iIdx = 1; iIdx < oVector.size(); iIdx++)
        {
            oCanvas.drawLine(oVector.get(iIdx -1).x, oVector.get(iIdx- 1).y, oVector.get(iIdx).x, oVector.get(iIdx).y, oPaint);
        }
    }
    public void DrawBars(Canvas oCanvas, CGraphProperties oGraphParams, Paint oPaint, ArrayList<PointF> oVector)
    {
        float fYOrigin = oGraphParams.WorkingOrigin.y;

        for (int iIdx = 1; iIdx < oVector.size(); iIdx++)
        {
            float fLeft  = oVector.get(iIdx -1).x;
            float fTop   = Math.min(fYOrigin, oVector.get(iIdx -1).y);
            float fRight = oVector.get(iIdx).x;
            float fBot   = Math.max(fYOrigin, oVector.get(iIdx -1).y);

            oCanvas.drawRect(fLeft, fTop, fRight, fBot, oPaint);
        }
    }
    public class CGraphProperties
    {
        RectF  Total;
        RectF  WorkingArea;
        PointF WorkingOrigin;
        double XScale    = 1.0;
        double YScale    = 1.0;
        long   XVOffset  = 0;
        double YVOffset  = 0.0;
        long   XVMin     = 0;
        long   XVMax     = 0;
        double YVMin     = 0.0;
        double YVMax     = 0.0;
        String XLabel    = "Time (hours)";
        String YLabel    = "Price (CAD)";
        double XGrid     = 0;
        double YGrid     = 0;
        int    GridColor = Color.GRAY;

        public CGraphProperties(Canvas oCanvas)
        {
            float fLMargin = oCanvas.getWidth () / 50f;
            float fRMargin = oCanvas.getWidth () / 10f;
            float fTMargin = oCanvas.getHeight() / 20f;
            float fBMargin = oCanvas.getHeight() / 20f;

            Total           = new RectF(0f, 0f, oCanvas.getWidth () -1, oCanvas.getHeight() -1);
            WorkingArea     = new RectF(fLMargin, fTMargin, Total.right - fRMargin, Total.bottom - fBMargin);
            WorkingOrigin   = new PointF();
            WorkingOrigin.x = WorkingArea.left  ;
            WorkingOrigin.y = WorkingArea.bottom;
        }
        public void SetScale(long lX1, long lX2, double dY1, double dY2)
        {
            XVMin = XVOffset = Math.min(lX1, lX2);
            XVMax = Math.max(lX1, lX2);
            YVMin = YVOffset = Math.min(dY1, dY2);
            YVMax = Math.max(dY1, dY2);

            double dDeltaX = Math.abs(lX2 - lX1);
            double dDeltaY = Math.abs(dY2 - dY1);

            XScale = dDeltaX / (double)(WorkingArea.right  - WorkingArea.left);
            YScale = dDeltaY / (double)(WorkingArea.bottom - WorkingArea.top );

            if (XScale == 0)
                XScale = 1.0;
            if (YScale == 0)
                YScale = 1.0;
        }
        public void RecenterOrigin()
        {
            if (YVMin < 0)
            {
                WorkingOrigin.y += (YVMin / YScale);
            }
        }
    }
}