package com.sysdream.gadget;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.sysdream.fino.IInspectionService;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import android.util.Log;

public class GadgetService extends Service implements IGadgetService {

	private static String TAG = "GadgetService";
	private static ServerSocket server = null;
	public GadgetServiceBinder binder = null;
	private static ServerThread server_thread = null;
	private static ConcurrentHashMap<String, IInspectionService> inspectionServices = new ConcurrentHashMap<String, IInspectionService>();
	private static Handler handler = null;
	private static ServiceConnection connection = null;
	
	public class ClientThread extends Thread {

		private final static String TAG ="CLIENT";
		private Socket client = null;
		private BufferedReader sock_in = null;
		private OutputStream sock_out = null;
		private char[] size_buf = new char[4];
		private int size = 0;
		private int msg_type = 0;
		private boolean m_running = false;
		private ServerThread m_parent = null;
		
		public class AsyncInvoke extends AsyncTask<String, Integer, String> {

			private Request req;
			
			public AsyncInvoke(Request req) {
				this.req = req;
			}
			
			protected void onPostExecute(String result) {
				Method m;
				Log.d(TAG,"Post execute");
				final IInspectionService service = GadgetService.getAppService(this.req.app);
				try {
					m = IInspectionService.class.getMethod(req.method, req.paramTypes);
					if (req.parameters.length == 0)
						ClientThread.this.sendResponse(new Response(m.invoke(service), true));
					else
						ClientThread.this.sendResponse(new Response(m.invoke(service, req.parameters), true));
				} catch (NoSuchMethodException e) {
					ClientThread.this.sendResponse(new Response("Method does not exist", false));
				} catch (IllegalArgumentException e) {
					ClientThread.this.sendResponse(new Response("Illegal argument", false));
				} catch (IllegalAccessException e) {
					ClientThread.this.sendResponse(new Response("Illegal access", false));
				} catch (InvocationTargetException e) {
					ClientThread.this.sendResponse(new Response("Invocation error", false));
				}
			}


			@Override
			protected String doInBackground(String... params) {
				Log.d(TAG,"Backgroung ...");
				return null;
			}
			
		}
		
		public ClientThread(Socket client, ServerThread parent) {
			this.client = client;
			this.m_parent = parent;
			this.m_running = true;
		}

		public synchronized boolean isRunning() {
			return m_running;
		}

		public synchronized void kill() {
			m_running = false;
			try {
				this.client.close();
			}
			catch (IOException sockerr) {
			}
			this.interrupt();
		}

		
		private Request readRequest(int msg_type, int size) throws IOException {
			int nbread = -1;
			char[]raw_json = new char[size];
			if (this.sock_in.read(raw_json, 0, size) == size)
			{
				/* Build the corresponding message based on the serialized data */
				Log.d(TAG,"Got JSON: "+new String(raw_json));
				Request req = Request.fromJson(new String(raw_json));
				Log.d(TAG, "Got request "+req.method.toString());
				return req;
			}
			return null;
		}
		
		public boolean processRequest(final Request req) {
			Response resp = new Response(null, false);
			
			if (req == null)
				resp = new Response("Bad request", false);
			else
			{
				if (req.method.equals("listApps"))
				{
					final ArrayList<String> pkgs = new ArrayList<String>();
					final Intent i = new Intent("com.sysdream.fino.inspection");
					for(final ResolveInfo r : getPackageManager()
							.queryIntentServices(i, 0)) {
						pkgs.add(r.serviceInfo.packageName);
					}
					return this.sendResponse(new Response(pkgs.toArray(new String[]{}), true));
				}
				else if (req.method.equals("connectApp")) {
					GadgetService.attachToApp(GadgetService.this.getApplicationContext(), req.app);
					return this.sendResponse(new Response(req.app, true));
				}
				else
				{
					/* Do some introspection to call our remote method */
					final Method m;
					try {
						m = IInspectionService.class.getMethod(req.method, req.paramTypes);
						final IInspectionService service = GadgetService.getAppService(req.app);
						if (service != null)
						{
							if (req.parameters.length == 0)
								this.sendResponse(new Response(m.invoke(service), true));
							else
								this.sendResponse(new Response(m.invoke(service, req.parameters), true));
							return true;
						}
						else
							resp = new Response("Service not found", false);
					} catch (NoSuchMethodException e) {
						resp = new Response("Method does not exist", false);
					} catch (IllegalArgumentException e1) {
						resp = new Response("Illegal argument", false);
					} catch (IllegalAccessException e) {
						resp = new Response("Illegal access", false);
					} catch (InvocationTargetException e) {
						resp = new Response("Invocation error", false);
						e.printStackTrace();
					}			
					
					if (resp != null)
						return this.sendResponse(resp);
					else
						return false;
				}
			}
			return false;
		}

		public boolean sendResponse(Response resp) {
			/* Send response */
			try {
				sock_out.write(resp.toRaw());
				return true;
			} catch (IOException e) {
				return false;
			}
		}
		
		public void run() {  
			int nbread = -1;
        	try {
        		Log.d(TAG, "Handle client connection");
        		this.sock_in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        		this.sock_out = client.getOutputStream();
                while (this.isRunning()) {
                	nbread = this.sock_in.read(size_buf, 0, 4);
            		if (nbread == 4)
            		{
            			/* Convert size bytes to real size int */
            			size = ByteBuffer.wrap(new String(size_buf).getBytes()).getInt();
            			/* Process message */
            			Request req = this.readRequest(0, size);
            			if (req != null)
            				this.processRequest(req);
            		}
            		else if (nbread < 0)
            			break;
                }
                this.client.close();
                Log.d(TAG, "Client disconnected");
                this.m_parent.onClientDisconnect(this);
        	} catch(SocketException sockerr) {
        		Log.d(TAG, "Client socket closed");
        		this.m_parent.onClientDisconnect(this);
        	}
        	catch(Exception e) {
        		e.printStackTrace();
        	}
        }       

	}
	
	public class ServerThread extends Thread {

		private int port = -1;
		private boolean m_running = false;
		private ServerSocket server = null;
		private ArrayList<ClientThread> m_clients = new ArrayList<ClientThread>();

		@Override
		public void start() {
			super.start();
			m_running = true;
		}
		
		public synchronized void kill() {
			m_running = false;
			try {
				/* Kill all clients */
				for (ClientThread client : m_clients)
					client.kill();
				this.server.close();
			}
			catch (IOException sockerr) {
			}
			this.interrupt();
		}
		
		public synchronized boolean isRunning() {
			return this.m_running;
		}
		
	    public ServerThread(int port) {
	    	this.port = port;
	    }
		
	    public void run() {
	    	ClientThread client = null;
	    	
	         try {
	             this.server = new ServerSocket(this.port);
	             while (this.isRunning()) {
	            	 Socket client_sock = this.server.accept();
	            	 client = new ClientThread(client_sock, this);
	            	 m_clients.add(client);
	            	 client.start();
	             }
	             
	         }
	         catch (SocketException e)
	         {
	        	 Log.d("Service", "Service socket closed");
	         }
	         catch (Exception e) {
	             e.printStackTrace();
	         }
	    }
	    
	    public void onClientDisconnect(ClientThread client) {
	    	if (m_clients.contains(client))
	    		m_clients.remove(client);
	    }
	}
	
	public class GadgetServiceBinder extends Binder {
		private IGadgetService service = null;
		
		public GadgetServiceBinder(IGadgetService service) {
			this.service = service;
		}
		
		public void startServer(String address, int port, int mode){
			if (service != null)
				service.startServer(address, port, mode);
		}
		
		public void stopServer() {
			if (service != null)
				service.stopServer();
		}
		
		public int getMode() {
			if (service != null)
				return service.getMode();
			return -1;
		}
		
		public String getAddress() {
			if (service != null)
				return service.getAddress();
			return null;
		}
		
		public int getPort() {
			if (service != null)
				return service.getPort();
			return -1;
		}
				
		public IGadgetService getService() {
			return service;
		}
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		this.binder = new GadgetServiceBinder(this); 
		handler = new Handler(Looper.getMainLooper());
	}
	
	public void OnDestroy() {
		super.onDestroy();
		this.unbindService(connection);
	}

	public static synchronized boolean isRegisteredAppService(String appPkg) {
		return inspectionServices.containsKey(appPkg);
	}
	
	public static synchronized void registerAppService(Context context, String appPkg, IInspectionService service) {
		if (GadgetService.inspectionServices.containsKey(appPkg))
		{
			try {
				if (connection != null)
					context.unbindService(connection);
			} catch (Exception e) {
			}
		}
		GadgetService.inspectionServices.put(appPkg, service);
	}
	
	public static synchronized void unregisterAppService(String appPkg) {
		if (!GadgetService.inspectionServices.containsKey(appPkg))
			GadgetService.inspectionServices.remove(appPkg);
	}
	
	public static synchronized IInspectionService getAppService(final String appPkg) {
		if (GadgetService.inspectionServices.containsKey(appPkg))
			return GadgetService.inspectionServices.get(appPkg);
		else
			return null;
	}
	
	public static void attachToApp(final Context context, final String appPkg) {
		ServiceConnection mConnection = new ServiceConnection() {
		    // Called when the connection with the service is established
		    public void onServiceConnected(ComponentName className, IBinder service) {
		        // Following the example above for an AIDL interface,
		        // this gets an instance of the IRemoteInterface, which we can use to call on the service
		    	Log.d(GadgetService.TAG, "Connected to " + appPkg);
		        GadgetService.registerAppService(context, appPkg, IInspectionService.Stub.asInterface(service));
		    }

		    // Called when the connection with the service disconnects unexpectedly
		    public void onServiceDisconnected(ComponentName className) {
		    	Log.d(GadgetService.TAG, "Disconnected from " + appPkg);
		        GadgetService.unregisterAppService(appPkg);
		    }
		};
		
		/* First bind to service */
		Intent intent = new Intent("com.sysdream.fino.inspection");
		intent.setPackage(appPkg);
		Log.d(TAG, "Connecting to application "+appPkg);
		context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		/* Then launch application */
		Intent i = new Intent();
		PackageManager manager = context.getPackageManager();
		i = manager.getLaunchIntentForPackage(appPkg);
		if (i != null)
		{
			i.addCategory(Intent.CATEGORY_LAUNCHER);
			context.startActivity(i);
		}
	}
	
	public void startServer(String address, int port, int mode) {
		if (this.server_thread == null)
		{
			Log.d("Service", "server_thread == null");
			this.server_thread = new ServerThread(port);
			this.server_thread.start();
		}
	}

	public void stopServer() {
		Log.d("Service", "Stop service");
		if (this.server_thread != null)
			this.server_thread.kill();
		this.server_thread = null;
	}


	public int getMode() {
		// TODO Auto-generated method stub
		return 0;
	}


	public String getAddress() {
		// TODO Auto-generated method stub
		return null;
	}


	public int getPort() {
		// TODO Auto-generated method stub
		return 0;
	}

	
	@Override
	public IBinder onBind(Intent intent) {
		return this.binder;
	}

}


