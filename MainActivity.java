package com.example.coininverst;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;

import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private Intent            mServiceIntent   = null;
    private CoinGeckoService  mService         = null;
    private boolean           mBound           = false;
    private ArrayList<String> mCoinList        = null;
    private FirstFragment     mListFragment    = null;
    private SecondFragment    mGraphFragment   = null;
    private CCoinItemAdapter  mCoinItemAdapter = null;
    private int               miCurrentFragment = -1;
 //   private ListView          mListView        = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FragmentManager oFragManager = getSupportFragmentManager();
        oFragManager.beginTransaction().setReorderingAllowed(true).add(R.id.fragment_container_view, FirstFragment.class, null).commit();
        oFragManager.beginTransaction().setReorderingAllowed(true).add(R.id.fragment_container_view, SecondFragment.class, null).commit();
        oFragManager.executePendingTransactions();

        mListFragment  = (FirstFragment ) oFragManager.getFragments().get(0);
        mGraphFragment = (SecondFragment) oFragManager.getFragments().get(1);

        SwitchView(R.id.id_ListFragment);
       // mListView = (ListView)findViewById(R.id.id_ListView);

    //    FloatingActionButton fab = findViewById(R.id.fab);
//
    //    fab.setOnClickListener(new View.OnClickListener() {
    //        @Override
    //        public void onClick(View view) {
    //            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
    //                    .setAction("Action", null).show();
    //        }
    //    });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            return true;
        }
        else if (id == R.id.action_StartService)
        {
            if (mServiceIntent == null)
            {
                mServiceIntent = new Intent(this, CoinGeckoService.class);
                startService(mServiceIntent);
            }
            bindService(mServiceIntent, Connection, Context.BIND_AUTO_CREATE);
            return true;
        }
        else if (id == R.id.action_StopService)
        {
            unbindService(Connection);
            mBound = false;

            if (mServiceIntent != null)
            {
                stopService(mServiceIntent);
                mServiceIntent = null;
            }

            return true;
        }
        else if (id == R.id.action_List)
        {
            if (mBound)
            {
                SwitchView(R.id.id_ListFragment);

                mCoinList = mService.GetCoinList();
               //mListView.setAdapter(CreateAdapter(mCoinList));
                mListFragment.NewList(CreateAdapter(mCoinList));

            }
        }

        return super.onOptionsItemSelected(item);
    }

    private CCoinItemAdapter CreateAdapter(ArrayList<String> oInList)
    {
        ArrayList<CCoinItem> oCoinItems = new ArrayList<CCoinItem>();

        for (String oItem : oInList)
        {
            oCoinItems.add(new CCoinItem(null, oItem, "XXX", "999", "$$"));
        }
        mCoinItemAdapter = new CCoinItemAdapter(this, oCoinItems);

        return mCoinItemAdapter;
    }
    private void SwitchView(int iID)
    {
        if (iID != miCurrentFragment)
        {
            FragmentManager oFragManager = getSupportFragmentManager();
            if (iID == R.id.id_ListFragment)
                oFragManager.beginTransaction().setReorderingAllowed(true).replace(R.id.fragment_container_view, mListFragment).commit();
            else if (iID == R.id.id_GraphFragment)
                oFragManager.beginTransaction().setReorderingAllowed(true).replace(R.id.fragment_container_view, mGraphFragment).commit();
        }
        miCurrentFragment = iID;
    }
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection Connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CoinGeckoService.LocalBinder oBinder = (CoinGeckoService.LocalBinder) service;
            mService = oBinder.getService();
            mBound   = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            mBound = false;
        }
    };

    public class CCoinItemAdapter extends ArrayAdapter<CCoinItem>
    {
        private Context mContext = null;
        public CCoinItemAdapter(Context context, ArrayList<CCoinItem> oItems)
        {
            super(context, 0, oItems);
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            CCoinItem oItem = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                //convertView = LayoutInflater.from(mContext).inflate(R.layout.grid_layout, parent, false);
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.grid_layout, parent, false);
            }
            // Lookup view for data population
            //ImageView oLogo  = (ImageView) convertView.findViewById(R.id.CoinLogo);
            TextView  oName  = (TextView ) convertView.findViewById(R.id.id_CoinName);
            TextView  oSymb  = (TextView ) convertView.findViewById(R.id.id_CoinSymbol);
            TextView  oRank  = (TextView ) convertView.findViewById(R.id.id_CoinRank);
            Button    oValue = (Button   ) convertView.findViewById(R.id.id_CoinValue);

            // Populate the data into the template view using the data object
            //oLogo.setImageBitmap(((BitmapDrawable)oItem.Logo.getDrawable()).getBitmap());
            oName.setText(oItem.Name);
            oSymb.setText(oItem.Symbol);
            oRank.setText(oItem.Rank);
            oValue.setText(oItem.Value);

            oValue.setTag(position);
            oValue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position = (Integer) view.getTag();
                    // Access the row position here to get the correct data item
                    CCoinItem oCoin = getItem(position);
                    // Do what you want here...
                    String oName = oCoin.Name;
                }
            });


            // Return the completed view to render on screen
            return convertView;
        }
    }
}