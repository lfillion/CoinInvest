package com.example.coininverst;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;

import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity implements IHostActivityOptions {
    private Intent               mServiceIntent   = null;
    private CoinGeckoService     mService         = null;
    private boolean              mBound           = false;
    private ArrayList<CCoinItem> mCoinList        = null;
    private FirstFragment        mListFragment    = null;
    private SecondFragment       mGraphFragment   = null;
    private CCoinItemAdapter     mCoinItemAdapter = null;
    private int                  miCurrentFragment = -1;
    private FragmentManager      mFragmentManager  = null;
    private BroadcastReceiver    mMessageReceiver  = null;

    //private ArrayList<String> mCoinList        = null;
 //   private ListView          mListView        = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mServiceIntent = new Intent(this, CoinGeckoService.class);
        startService(mServiceIntent);
        bindService(mServiceIntent, Connection, Context.BIND_AUTO_CREATE);

        mFragmentManager = getSupportFragmentManager();
        mFragmentManager.setFragmentFactory(new CLocalFragmentFactory(mCoinItemAdapter, this));

        mFragmentManager.beginTransaction().setReorderingAllowed(true).add(R.id.fragment_container_view, FirstFragment.class, null).commit();
        mFragmentManager.beginTransaction().setReorderingAllowed(true).add(R.id.fragment_container_view, SecondFragment.class, null).commit();
        mFragmentManager.executePendingTransactions();

        mListFragment  = (FirstFragment ) mFragmentManager.getFragments().get(0);
        mGraphFragment = (SecondFragment) mFragmentManager.getFragments().get(1);

        SwitchView(R.id.id_ListFragment);

        mMessageReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                String oMessage = intent.getStringExtra("ServiceMessage");

                if (oMessage.contains("Notify.NewDataFromCoinGeckoService"))
                {
                    boolean bFirstList = mCoinItemAdapter == null;

                    mCoinList = mService.GetCoinList();
                    //mListView.setAdapter(CreateAdapter(mCoinList));
                    if (bFirstList)
                        mListFragment.NewList(CreateAdapter(mCoinList));
                    else
                        mCoinItemAdapter.notifyDataSetChanged();
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("Event.NewDataFromCoinGeckoService"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onDestroy()
    {  // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
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
        else if (id == R.id.action_ResetAll)
        {

        }
        else if (id == R.id.action_RefreshList)
        {
            if (mBound)
            {
                SwitchView(R.id.id_ListFragment);

                mCoinList = mService.GetCoinList();
               //mListView.setAdapter(CreateAdapter(mCoinList));
                mListFragment.NewList(CreateAdapter(mCoinList));
            }
        }
        else if (id == R.id.action_OriginalOrder)
        {
            if ((mCoinItemAdapter != null) && (mCoinItemAdapter.getCount() > 1))
            {
                mCoinItemAdapter.sort(new Comparator<CCoinItem>() {
                    @Override
                    public int compare(CCoinItem oLeft, CCoinItem oRight)
                    {
                        return oLeft.rank - oRight.rank;
                    }
                });
            }
        }
        else if (id == R.id.action_SortByName)
        {
            if ((mCoinItemAdapter != null) && (mCoinItemAdapter.getCount() > 1))
            {
                mCoinItemAdapter.sort(new Comparator<CCoinItem>() {
                    @Override
                    public int compare(CCoinItem oLeft, CCoinItem oRight)
                    {
                        return oLeft.Name.compareTo(oRight.Name);
                    }
                });
            }
        }
        else if (id == R.id.action_SortByBestSwing)
        {
            if ((mCoinItemAdapter != null) && (mCoinItemAdapter.getCount() > 1))
            {

            }
        }

        return super.onOptionsItemSelected(item);
    }

    private CCoinItemAdapter CreateAdapter(ArrayList<CCoinItem> oInList)
    {
        mCoinItemAdapter = new CCoinItemAdapter(this, oInList);

        return mCoinItemAdapter;
    }
    private void SwitchView(int iID)
    {
        if (iID != miCurrentFragment)
        {
            if (iID == R.id.id_ListFragment)
                mFragmentManager.beginTransaction().replace(R.id.fragment_container_view, mListFragment).setReorderingAllowed(true).addToBackStack(null).commit();
            else if (iID == R.id.id_GraphFragment)
                mFragmentManager.beginTransaction().replace(R.id.fragment_container_view, mGraphFragment).setReorderingAllowed(true).addToBackStack(null).commit();
        }
        miCurrentFragment = iID;
    }
    public void BackButtonClick()
    {
        SwitchView(R.id.id_ListFragment);
    }
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection Connection = new ServiceConnection()
    {
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
            TextView  oName  = (TextView ) convertView.findViewById(R.id.id_CoinName);
            TextView  oSymb  = (TextView ) convertView.findViewById(R.id.id_CoinSymbol);
            TextView  oRank  = (TextView ) convertView.findViewById(R.id.id_CoinRank);
            Button    oValue = (Button   ) convertView.findViewById(R.id.id_CoinValue);
            ImageView oLogo  = (ImageView) convertView.findViewById(R.id.id_Logo);

            // Populate the data into the template view using the data object
            //oLogo.setImageBitmap(((BitmapDrawable)oItem.Logo.getDrawable()).getBitmap());
            oName.setText (oItem.Name);
            oSymb.setText (oItem.Symbol);
            oRank.setText (oItem.Rank);
            oValue.setText(oItem.Value);

            try
            {
                File            oFile     = new File(oItem.ImgPath);
                FileInputStream oInStream = new FileInputStream(oFile);
                oLogo.setImageBitmap(BitmapFactory.decodeStream(oInStream));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            oValue.setTag(position);
            oValue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position = (Integer) view.getTag();
                    // Access the row position here to get the correct data item
                    CCoinItem oCoin = getItem(position);
                    // Do what you want here...
                    SwitchView(R.id.id_GraphFragment);
                    mGraphFragment.SetCoinItem(oCoin);
                }
            });
            // Return the completed view to render on screen
            return convertView;
        }
    }
    public class CLocalFragmentFactory extends FragmentFactory
    {
        public CCoinItemAdapter     mContext     = null;
        public IHostActivityOptions mHostOptions = null;
        public CLocalFragmentFactory(CCoinItemAdapter context, IHostActivityOptions hostOptions)
        {
            super();
            this.mContext     = context;
            this.mHostOptions = hostOptions;
        }

        @NonNull
        @Override
        public Fragment instantiate(@NonNull ClassLoader classLoader, @NonNull String className)
        {
            Class<? extends Fragment> oFragmentClass = loadFragmentClass(classLoader, className);

            if (oFragmentClass == FirstFragment.class)
            {
                return new FirstFragment(mContext);
            }
            else if (oFragmentClass == SecondFragment.class)
            {
                return new SecondFragment(mHostOptions);
            }
            else
            {
                return super.instantiate(classLoader, className);
            }
        }
    }
}