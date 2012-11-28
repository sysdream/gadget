package com.sysdream.gadget;

import java.lang.reflect.Method;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sysdream.fino.IInspectionService;

/**
 * Provide a small RPC message format required to communicate
 * with Gadget's RPC service  
 * 
 * @author <a href="mailto:d.cauquil@sysdream.com>Damien Cauquil</a>
 * @version 1.0
 */

/**
 * RPC Message class
 * 
 * This class is used by Gson to deserialize client's requests.
 */

public class Request {
	
	/**
	 * Request's parameters. Not all of them will be deserialized. 
	 */

	public String app;
	public String method;
	public Object[] parameters;
	public Class[] paramTypes;
		
	public Request(String app, String method, Object[] parameters, Class[] paramTypes) {
		this.app = app;
		this.method = method;
		this.parameters = parameters;
		this.paramTypes = paramTypes;
	}
	
	/**
	 * Create an instance of Request 
	 * @param json Serialized data required to build the request
	 * @return a Request instance built from the provided json data
	 */
	public static Request fromJson(String json) {
		int i;
		Gson gson = new Gson();
		
		/* Parse message */
		JsonParser parser = new JsonParser();
		
		try {
		    JsonArray array = parser.parse(json).getAsJsonArray();
		    
		    /* Must have at least 2 parameters */
		    if (array.size()<2)
		    	return null;
		    
		    /* Extract method */
		    Log.d("Request", "Parameters:"+String.valueOf(array.size()-2));
		    String pkg = gson.fromJson(array.get(0), String.class);
		    String method = gson.fromJson(array.get(1), String.class);
		    Object[] parameters = null;
		    
		    if (method.equals("listApps"))
		    	return new Request(pkg, "listApps", new Object[0], new Class[0]);
		    else if (method.equals("connectApp"))
		    	return new Request(pkg, "connectApp", new Object[0], new Class[0]);
		    else
		    {
			    /* Check if method exists and retrieve parameters type */
			    Method[] methods = IInspectionService.class.getMethods();
			    for (Method m : methods)
			    {
			    	if ( m.getName().equals(method) && (m.getParameterTypes().length == (array.size()-2)))
			    	{
			    		/* Allocate memory */
			    		parameters = new Object[array.size()-2];
			    		
			    		/* Try to unserialize */
			    		try
			    		{
			    			for (i=0; i<(array.size()-2); i++)
			    				parameters[i] = gson.fromJson(array.get(i+2), m.getParameterTypes()[i]);
			    			return new Request(pkg, method, parameters, m.getParameterTypes());
			    		}
			    		catch (JsonSyntaxException e)
			    		{
			    		}
			    	}
			    }
		    }
		    
		    /* Unable to find a method */
		    return null;
			    
		}
		catch (JsonSyntaxException e)
		{
			/* Syntax error, return null */
			return null;
		}
	}
}
