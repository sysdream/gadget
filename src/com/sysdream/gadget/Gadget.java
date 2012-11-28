package com.sysdream.gadget;

import com.sysdream.gadget.GadgetService.GadgetServiceBinder;

import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;

public class Gadget extends Activity {
	
    IGadgetService mService;
    boolean mBound = false;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gadget);       
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    	
    	// Bind to LocalService
        Intent intent = new Intent(this, GadgetService.class);
        this.getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_gadget, menu);
        return true;
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
    	private String TAG = "ServiceConnection";
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            GadgetServiceBinder binder = (GadgetServiceBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d(TAG, "Service connected");
            /* Start server */
            mService.startServer(null, 4444, 0);
        }

        public void onServiceDisconnected(ComponentName arg0) {
        	Log.d(TAG, "Service disconnected");
            mBound = false;
        }
    };
    
    public void onDestroy() {
    	super.onDestroy();
    	if (mBound)
    	{
    		this.getApplicationContext().unbindService(mConnection);
    	}
    }
    
}
