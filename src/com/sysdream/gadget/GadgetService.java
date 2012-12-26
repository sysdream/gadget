package com.sysdream.gadget;

import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;


/*
 * Android imports
 */

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
import android.util.Base64;

/**
 *  Fino's inspection service interface
 */
import com.sysdream.fino.IInspectionService;


/**
 * Gadget service
 * 
 * This service offers a TCP server able to forward API calls to a given
 * remote Fino service running in another Android application. This service
 * must be used with our Python's libfino TCP client.
 */

public class GadgetService extends Service implements IGadgetService {


	private static String TAG = "GadgetService";
	private static ServerSocket server = null;
	public GadgetServiceBinder binder = null;
	private static ServerThread server_thread = null;
	private static ConcurrentHashMap<String, IInspectionService> inspectionServices = new ConcurrentHashMap<String, IInspectionService>();
	private static Handler handler = null;
	private static ServiceConnection connection = null;
	
	/**
	 * ClientThread
	 * 
	 * Process messages coming from a connected client.
	 */
	
	public class ClientThread extends Thread {

		private final static String TAG ="CLIENT";
		private Socket client = null;
		private InputStream sock_in = null;
		private OutputStream sock_out = null;
		private byte[] size_buf = new byte[4];
		private int size = 0;
		private int msg_type = 0;
		private boolean m_running = false;
		private ServerThread m_parent = null;
		
		/**
		 * Constructor
		 * 
		 * @param client the socket client to use
		 * @param parent parent's thread
		 */
		public ClientThread(Socket client, ServerThread parent) {
			this.client = client;
			this.m_parent = parent;
			this.m_running = true;
		}

		
		/**
		 * Check if the thread is running
		 * @return boolean True if running, false otherwise
		 */
		
		public synchronized boolean isRunning() {
			return m_running;
		}

		
		/**
		 * Kill the thread instance
		 */
		
		public synchronized void kill() {
			m_running = false;
			try {
				this.client.close();
			}
			catch (IOException sockerr) {
			}
			this.interrupt();
		}

		
		/**
		 * Read an RPC request from the client socket and unserialize it (JSON based).
		 * 
		 * @param size Size of the raw request
		 * @return Request the request read from the socket.
		 */
		
		private Request readRequest(int size) throws IOException {
			byte[]raw_json = new byte[size];
            int read = 0, got = 0;

            while (read < size) {
                /*Log.d(TAG, "Expecting " + (size-read) + " bytes");*/
                got = this.sock_in.read(raw_json, read, size-read);
                /*Log.d(TAG, "Got " + got + " bytes on " + size);*/
                if (got < 0)
                    return null;
                read += got;
            }

			/* Build the corresponding message based on the serialized data */
			/*Log.d(TAG,"Got JSON: "+new String(raw_json));*/
			Request req = Request.fromJson(new String(raw_json));
            /*
            if (req != null)
    			Log.d(TAG, "Got request "+req.method.toString());
            else
                Log.d(TAG, "Error parsing request");
            */
			return req;
		}
		
		
		/**
		 * Process request
		 * @param req the request to process
		 * @return False if the request cannot be processed, true otherwise
		 */
		
		public boolean processRequest(final Request req) {
			Response resp = new Response(null, false);
			
			if (req == null)
				resp = new Response("Bad request", false);
			else
			{
				/* Special request 'listApps', not implemented in Fino Service */
				if (req.method.equals("listApps"))
				{
					/* Create a list of applications implementing "com.sysdream.fino.inspection" */
					final ArrayList<String> pkgs = new ArrayList<String>();
					final Intent i = new Intent("com.sysdream.fino.inspection");
					for(final ResolveInfo r : getPackageManager()
							.queryIntentServices(i, 0)) {
						pkgs.add(r.serviceInfo.packageName);
					}
					
					/* Send this list to the remote client */
					return this.sendResponse(new Response(pkgs.toArray(new String[]{}), true));
				}
				else if (req.method.equals("connectApp")) {
					/* Special request'connectApp', not implement in Fino Service */
					/* Attach Gadget to the remote application (create it if needed) */
					GadgetService.attachToApp(GadgetService.this.getApplicationContext(), req.app);
					
					/* Send response to the remote client */
					return this.sendResponse(new Response(req.app, true));
				}
				else
				{
					/* Do some inspection to call our remote method */
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
						/* Method not found */
						resp = new Response("Method does not exist", false);
					} catch (IllegalArgumentException e1) {
						/* Bad argument */
						resp = new Response("Illegal argument", false);
					} catch (IllegalAccessException e) {
						/* Access exception */ 
						resp = new Response("Illegal access", false);
					} catch (InvocationTargetException e) {
						/* Invocation error */
						resp = new Response("Invocation error", false);
						e.printStackTrace();
					}			
					
					/* Send response to the remote client */
					return this.sendResponse(resp);
				}
			}
			
			/* Error */
			return false;
		}

		
		/**
		 * Send a response to the remote client
		 * @param resp the response to send
		 * @return Boolean False if the response cannot be send, true otherwise 
		 */
		
		public boolean sendResponse(Response resp) {
			/* Send response */
			try {
				sock_out.write(resp.toRaw());
				return true;
			} catch (IOException e) {
				return false;
			}
		}
		
		
		/**
		 * ClientThread's main loop
		 * 
		 * Read RPC messages from socket, process it and send response to the remote client
		 */
		
		public void run() {  
			int nbread = -1;
        	try {
        		Log.d(TAG, "Handle client connection");
        		this.sock_in = client.getInputStream();
        		this.sock_out = client.getOutputStream();
                while (this.isRunning()) {
                	nbread = this.sock_in.read(size_buf, 0, 4);
            		if (nbread == 4)
            		{
            			/* Convert size bytes to real size int */
                        size = ByteBuffer.wrap(size_buf).getInt();

            			/* Process message */
            			Request req = this.readRequest(size);
            			if (req != null)
            				this.processRequest(req);
            		}
            		else if (nbread < 0)
            			break;
                }
                /* Client socket closed */
                this.client.close();
                Log.d(TAG, "Client disconnected");
                this.m_parent.onClientDisconnect(this);
        	} catch(SocketException sockerr) {
        		/* Socket error */
        		Log.d(TAG, "Client socket closed");
        		this.m_parent.onClientDisconnect(this);
        	}
        	catch(Exception e) {
        		e.printStackTrace();
        	}
        }       

	}
	
	
	/**
	 * Server Thread
	 * 
	 * Manages the server socket. This class only implements a listening socket
	 * 
	 * TODO: Implements a remote connecting socket
	 */
	
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
		
		
		/**
		 * Kill this thread
		 */
		
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
		
		
		/**
		 * Check if this thread is running
		 * @return boolean True if running, false otherwise
		 */
		
		public synchronized boolean isRunning() {
			return this.m_running;
		}
		
		
		/**
		 * Constructor
		 * @param port the port to listen on
		 */
	    public ServerThread(int port) {
	    	this.port = port;
	    }
		
	    
	    /**
	     * ServerThread's main loop
	     * 
	     * Create the socket, accept connections and wrap clients' sockets
	     */
	    
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
	    
	    
	    /**
	     * Handles client disconnection
	     * @param client the client thread
	     */
	    
	    public void onClientDisconnect(ClientThread client) {
	    	if (m_clients.contains(client))
	    		m_clients.remove(client);
	    }
	}
	
	
	/**
	 * GadgetService binder
	 */
	
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

	
	/**
	 * Check if an application's service has already been registered
	 * @param appPkg the target application service name
	 * @return True if registered, false otherwise
	 */
	
	public static synchronized boolean isRegisteredAppService(String appPkg) {
		return inspectionServices.containsKey(appPkg);
	}
	
	
	/**
	 * Register an connected application's service
	 * @param context the target context
	 * @param appPkg the application's name
	 * @param service the remote service interface
	 */
	
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
	
	
	/**
	 * Unregister an existing service
	 * @param appPkg the target application
	 */
	
	public static synchronized void unregisterAppService(String appPkg) {
		if (!GadgetService.inspectionServices.containsKey(appPkg))
			GadgetService.inspectionServices.remove(appPkg);
	}
	
	
	/**
	 * Retrieve the connected service corresponding to a given application
	 * @param appPkg the application name
	 * @return IInspectionService the remote service interface (binder)
	 */
	public static synchronized IInspectionService getAppService(final String appPkg) {
		if (GadgetService.inspectionServices.containsKey(appPkg))
			return GadgetService.inspectionServices.get(appPkg);
		else
			return null;
	}
	
	
	/**
	 * Attach GadgetService to a remote application. 
	 * 
	 * This method launches the remote Fino service implemented in the target application,
	 * then launch the main activity once connected.
	 * 
	 * @param context the target context
	 * @param appPkg the application's name
	 */
	
	public static void attachToApp(final Context context, final String appPkg) {
		ServiceConnection mConnection = new ServiceConnection() {
		    // Called when the connection with the service is established
		    public void onServiceConnected(ComponentName className, IBinder service) {
		        // Following the example above for an AIDL interface,
		        // this gets an instance of the IRemoteInterface, which we can use to call on the service
		    	Log.d(GadgetService.TAG, "Connected to " + appPkg);
		        GadgetService.registerAppService(context, appPkg, IInspectionService.Stub.asInterface(service));
		        
				/* Launch application only when the corresponding service is started */
                /*
				Intent i = new Intent();
				PackageManager manager = context.getPackageManager();
				i = manager.getLaunchIntentForPackage(appPkg);
				if (i != null)
				{
					i.addCategory(Intent.CATEGORY_LAUNCHER);
					context.startActivity(i);
				}
                */
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
	}
	
	
	/**
	 * Start server thread if required.
	 */
	
	public void startServer(String address, int port, int mode) {
		if (this.server_thread == null)
		{
			Log.d("Service", "server_thread == null");
			this.server_thread = new ServerThread(port);
			this.server_thread.start();
		}
	}

	
	/**
	 * Stop server thread.
	 */
	
	public void stopServer() {
		Log.d("Service", "Stop service");
		if (this.server_thread != null)
			this.server_thread.kill();
		this.server_thread = null;
	}


	/**
	 * Get mode. Originally here to get info on the mode used (remote connect or server).
	 */
	
	public int getMode() {
		// TODO Auto-generated method stub
		return 0;
	}


	/**
	 * Get IP address of the remote debugger (when server is in reverse-connect mode)
	 */
	
	public String getAddress() {
		return null;
	}

	/**
	 * Get server remote-connect port
	 */
	public int getPort() {
		return 0;
	}

	/**
	 * Handles service binding.
	 */
	
	@Override
	public IBinder onBind(Intent intent) {
		return this.binder;
	}
}


