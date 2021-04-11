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
import android.widget.ImageView;
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
    public static String         TAG           = "GraphFragment";
    private IHostActivityOptions mHostOptions  = null;
    private Button               mBackButton   = null;
    private ImageView            mGraphicArea  = null;
    private ImageView            mLogoImgView  = null;
    private TextView             mRankTextView = null;
    private TextView             mSymbTextView = null;
    private TextView             mNameTextView = null;
    private TextView             mValTextView  = null;
    private CCoinItem            mCoinItem     = null;
    private CGraphProperties     mGraphParams  = null;

    public SecondFragment(IHostActivityOptions oHostOptions)
    {
        super();
        mHostOptions = oHostOptions;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBackButton   = (Button   ) getView().findViewById(R.id.id_BackViewButton);
        mGraphicArea  = (ImageView) getView().findViewById(R.id.id_ImageView);
        mLogoImgView  = (ImageView) getView().findViewById(R.id.id_2ndLogo);
        mRankTextView = (TextView ) getView().findViewById(R.id.id_2ndCoinRank);
        mSymbTextView = (TextView ) getView().findViewById(R.id.id_2ndCoinSymbol);
        mNameTextView = (TextView ) getView().findViewById(R.id.id_2ndCoinName);
        mValTextView  = (TextView ) getView().findViewById(R.id.id_2ndCoinValue);

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

        if ((mCoinItem != null) && mCoinItem.GetStats().Valid)
            DrawGraphic(mCoinItem, Color.RED);
        else
            DrawPseudoGraphic();

//        mImageView.setImageResource(R.drawable.ic_launcher_background);

    }
    private void DrawPseudoGraphic()
    {
        // (1900 + 101)= 2001 sept 11 8h30...
        CCoinItem oPseudo = new CCoinItem("PSEUDO", "NUL", 0, 50.0, new Timestamp(101,9,11,8,25,0,0), null);
        oPseudo.NewValue(58.0, new Timestamp(101,9,11,8,30,0,0));
        oPseudo.NewValue(60.0, new Timestamp(101, 9, 11, 8, 35, 0, 0));
        oPseudo.NewValue(61.0, new Timestamp(101, 9, 11, 8, 40, 0, 0));

        DrawGraphic(oPseudo, Color.BLACK);
    }
    private void DrawGraphic(CCoinItem oCoin, int iColor)
    {
        int    iTotalWidth  = 600;
        int    iTotalHeight = 600;
        Bitmap oBmp         = Bitmap.createBitmap(iTotalWidth, iTotalHeight, Bitmap.Config.ARGB_8888);
        Canvas oCanvas      = new Canvas(oBmp);
        Paint  oPaint       = new Paint();

        ArrayList<CCoinItem.CStatSample> oLocalSamples = oCoin.GetStats().History;
        ArrayList<PointF>                oPointList    = new ArrayList<>();

        mGraphParams = new CGraphProperties(oCanvas);

        DrawGrid(oCanvas);

        DefineScale(oCanvas, oLocalSamples);

        DrawText(oCanvas);

        for (CCoinItem.CStatSample oSample : oLocalSamples)
        {
            oPointList.add(ToCoord(oSample));
        }

        oPaint.setColor(iColor);
        DrawVector(oCanvas, oPaint, oPointList);

        mGraphicArea.setImageBitmap(oBmp);
    }
    private void DrawGrid(Canvas oCanvas)
    {
        Paint oPaint = new Paint();

        oPaint.setColor (mGraphParams.GridColor);
        oCanvas.drawLine(mGraphParams.WorkingArea.left , mGraphParams.WorkingArea.top   , mGraphParams.WorkingArea.right, mGraphParams.WorkingArea.top   , oPaint);
        oCanvas.drawLine(mGraphParams.WorkingArea.right, mGraphParams.WorkingArea.top   , mGraphParams.WorkingArea.right, mGraphParams.WorkingArea.bottom, oPaint);
        oCanvas.drawLine(mGraphParams.WorkingArea.right, mGraphParams.WorkingArea.bottom, mGraphParams.WorkingArea.left , mGraphParams.WorkingArea.bottom, oPaint);
        oCanvas.drawLine(mGraphParams.WorkingArea.left , mGraphParams.WorkingArea.bottom, mGraphParams.WorkingArea.left , mGraphParams.WorkingArea.top   , oPaint);
    }

    private void DrawText(Canvas oCanvas)
    {
        Paint oPaint = new Paint();
        float fTSize = 20f;

        String oYMinVal  = CCoinItem.ValToString(mGraphParams.YVMin);
        String oYMaxVal  = CCoinItem.ValToString(mGraphParams.YVMax);
        String oXMinTime = CCoinItem.ConvertToLocalTime(new Timestamp(mGraphParams.XVMin), "HH:MM:SS a");
        String oXMaxTime = CCoinItem.ConvertToLocalTime(new Timestamp(mGraphParams.XVMax), "HH:MM:SS a");

        if (  ((oYMinVal.length() > 4) && !oYMinVal.contains("."))
            ||((oYMaxVal.length() > 4) && !oYMaxVal.contains("."))  )
            fTSize = 14f; //Todo: measure text size in pixels.

        oPaint.setColor(mGraphParams.GridColor);
        oPaint.setTextSize(fTSize);
        oCanvas.drawText(oYMaxVal , mGraphParams.WorkingArea.right + 5f, mGraphParams.WorkingArea.top   , oPaint);
        oCanvas.drawText(oYMinVal , mGraphParams.WorkingArea.right + 5f, mGraphParams.WorkingArea.bottom, oPaint);
        oCanvas.drawText(oXMinTime, mGraphParams.WorkingArea.left, mGraphParams.WorkingArea.bottom + 20f, oPaint);
        oCanvas.drawText(oXMaxTime, mGraphParams.WorkingArea.right - 50f, mGraphParams.WorkingArea.bottom + 20f, oPaint);
    }
    private void DefineScale(Canvas oCanvas, ArrayList<CCoinItem.CStatSample> oSampleList)
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

            mGraphParams.SetScale(oStart.getTime(), oEnd.getTime(), dMinVal, dMaxVal);
        }
    }

    public PointF ToCoord(CCoinItem.CStatSample oSample)
    {
        PointF oOut = new PointF();
        double dX   = oSample.Time.getTime() - mGraphParams.XVOffset;
        double dY   = oSample.Price          - mGraphParams.YVOffset;

        oOut.x = mGraphParams.WorkingOrigin.x + (float)(dX / mGraphParams.XScale);
        oOut.y = mGraphParams.WorkingOrigin.y - (float)(dY / mGraphParams.YScale);

        return oOut;
    }
    public void DrawVector(Canvas oCanvas, Paint oPaint, ArrayList<PointF> oVector)
    {
        for (int iIdx = 1; iIdx < oVector.size(); iIdx++)
        {
            oCanvas.drawLine(oVector.get(iIdx -1).x, oVector.get(iIdx- 1).y, oVector.get(iIdx).x, oVector.get(iIdx).y, oPaint);
        }
    }
    public void SetCoinItem(CCoinItem oToShow)
    {
        mCoinItem = oToShow;
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
            float fLMargin = oCanvas.getWidth () / 50;
            float fRMargin = oCanvas.getWidth () / 10;
            float fTMargin = oCanvas.getHeight() / 40;
            float fBMargin = oCanvas.getHeight() / 20;

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
    }
}