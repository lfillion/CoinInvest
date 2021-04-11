package com.example.coininverst;

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
import android.widget.TextView;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class FirstFragment extends Fragment {
    private ListView     mListView = null;
    public static String TAG       = "ListFragment";
    private ArrayAdapter mArrayAdapter = null;

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
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mListView = (ListView) view.findViewById(R.id.id_ListView);
        if (mArrayAdapter != null)
            mListView.setAdapter(mArrayAdapter);
    }
    public void NewList(ArrayAdapter oAdapter)
    {
        mArrayAdapter = oAdapter;

        mListView.setAdapter(null);
        mListView.setAdapter(mArrayAdapter);
    }
    public ListView GetListView()
    {
        return mListView;
    }
}