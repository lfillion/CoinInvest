package com.example.coininverst;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class FirstFragment extends Fragment {
    private ListView     mListView        = null;
    private TextView     mEventTextView   = null;
    private ScrollView   mEventScrollView = null;
    private String       m_oLogPath       = null;
    public static String TAG              = "ListFragment";
    private ArrayAdapter mArrayAdapter    = null;

    public FirstFragment(ArrayAdapter oAdapter)
    {
        super();
        mArrayAdapter = oAdapter;
    }
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        m_oLogPath = getContext().getExternalFilesDir(null) + "/CoinDBase" ;

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mListView = (ListView) view.findViewById(R.id.id_ListView);
        if (mArrayAdapter != null)
            mListView.setAdapter(mArrayAdapter);

//        mListView.setOnTouchListener(new OnSwipeTouchListener(getActivity())
//        {
//            public void onSwipeRight()
//            {
//                mListView.setVisibility(View.INVISIBLE);
//                mEventTextView.setVisibility(View.VISIBLE);
//
//                ArrayList<String> oEventData = GetEventList();
//
//                mEventTextView.setText("");
//
//                if (oEventData != null)
//                {
//                    for (String oLine : oEventData)
//                    {
//                        mEventTextView.append(oLine + "\r\n");
//                    }
//                }
//            }
//        }
//        );
     mEventTextView = (TextView) view.findViewById(R.id.id_EventTextView);
//     mEventTextView.setOnTouchListener(new OnSwipeTouchListener(getActivity())
//       {
//           public void onSwipeLeft()
//           {
//               mEventTextView.setVisibility(View.INVISIBLE);
//               mListView.setVisibility(View.VISIBLE);
//           }
//       });

    mEventScrollView = (ScrollView) view.findViewById(R.id.id_EvenTextScrollView);
    }
    public void ToggleView()
    {
        if (mListView.getVisibility() == View.VISIBLE)
        {
            mListView.setVisibility(View.INVISIBLE);
            mEventScrollView.setVisibility(View.VISIBLE);
            mEventTextView.setText("");

            ArrayList<String> oEventData = GetEventList();
            if (oEventData != null)
            {
                for (String oLine : oEventData)
                {
                    mEventTextView.append(oLine + "\r\n");
                }
            }
        }
        else
        {
            mEventScrollView.setVisibility(View.INVISIBLE);
            mListView.setVisibility(View.VISIBLE);
        }
    }
    public void NewList(ArrayAdapter oAdapter)
    {
        mArrayAdapter = oAdapter;

        mListView.setAdapter(null);
        mListView.setAdapter(mArrayAdapter);
    }
    private ArrayList<String> GetEventList()
    {
        ArrayList<String> oOut  = new ArrayList<>();
        File              oFile = new File(m_oLogPath + "/EventMsgs.log");

        if (oFile.exists())
        {
            try
            {
                FileReader     oFReader = new FileReader(oFile.getAbsoluteFile());
                BufferedReader oBReader = new BufferedReader(oFReader);
                String         oLine    = null;

                while ((oLine = oBReader.readLine()) != null)
                {
                    oOut.add(oLine);
                }
                oBReader.close();
                oFReader.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        return oOut;
    }
    public ListView GetListView()
    {
        return mListView;
    }
}