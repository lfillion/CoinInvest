package com.example.coininverst;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class ServiceRestarter extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        Toast.makeText(context, "Service Restarted", Toast.LENGTH_SHORT).show();

        context.startService(new Intent(context, CoinGeckoService.class));
    }
}
