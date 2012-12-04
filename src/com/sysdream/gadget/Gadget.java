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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.support.v4.app.NavUtils;

/**
 * Gadget main activity
 *
 * @author Damien Cauquil
 */

public class Gadget extends Activity {
	
    private IGadgetService mService;
    private boolean mBound = false;
    private TextView mStatus = null;
    private EditText mPort = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gadget);

        /* Get status TextView */
        this.mStatus = (TextView)this.findViewById(R.id.server_status);
        this.mPort = (EditText)this.findViewById(R.id.server_port);

        /* Set start server button click handler */
        Button btn_start = (Button)this.findViewById(R.id.btn_start_server);
        btn_start.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                Gadget.this.onStartServer();
            }
        });

        /* Set stop server button click handler */
        Button btn_stop = (Button)this.findViewById(R.id.btn_stop_server);
        btn_stop.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                Gadget.this.onStopServer();
            }
        });
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    	
    	/* Bind to LocalService */
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
            GadgetServiceBinder binder = (GadgetServiceBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d(TAG, "Service connected");
            
            /* Start server (read port from layout) */
            mService.startServer(null, Integer.parseInt(Gadget.this.mPort.getText().toString()), 0);
            Gadget.this.onServerStarted();
        }

        public void onServiceDisconnected(ComponentName arg0) {
        	Log.d(TAG, "Service disconnected");
            mBound = false;
        }
    };
    
    public void onDestroy() {
    	super.onDestroy();
    	if (mBound)
    		this.getApplicationContext().unbindService(mConnection);
    }


    /**
     * Called on start server button click
     */

    public void onStartServer() {
        if (mBound)
        {
            mService.startServer(null, Integer.parseInt(this.mPort.getText().toString()), 0);
            Gadget.this.onServerStarted();
        }
    }


    /**
     * Called on stop server button click
     */

    public void onStopServer() {
        if (mBound)
        {
            mService.stopServer();
            Gadget.this.onServerStopped();
        }
    }


    /**
     * Update UI on server start
     */

    public void onServerStarted() {
        this.mStatus.setText("Server status: ACTIVE");
    }


    /**
     * Update UI on server stop
     */

    public void onServerStopped() {
        this.mStatus.setText("Server status: INACTIVE");
    }
    
}
