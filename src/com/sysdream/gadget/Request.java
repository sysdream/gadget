package com.sysdream.gadget;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Provide a small RPC message format required to communicate
 * with Gadget's RPC service  
 * 
 * @author <a href="mailto:d.cauquil@sysdream.com>Damien Cauquil</a>
 * @version 1.0
 */

/**
 * Available RPC methods
 */
enum RequestMethod {
	LIST_APPS,			/* List Fino-enabled applications */
	GET_EP,				/* Get entry points */
	FILTER_EP,			/* Filter entry points */
	GET_FIELDS,			/* Get fields */
	GET_METHODS,		/* Get methods */
	GET_PATH,			/* Get path */
	GET_TYPE,			/* Get type */
	GET_VALUE,			/* Get value */
	SET_VALUE,			/* Set value */
	GET_METHOD_NAME,	/* Get a method's name */
	GET_METHOD_PARAMS,	/* Get a method's parameters list (name and type) */
	INVOKE_METHOD,		/* Remote method invocation */
	IS_ITERABLE,		/* Check if entrypoint is iterable */
	GET_ITERABLE,		/* Retrieve an interable */
	GET_ITERABLE_ITEM,	/* Retrieve an item from an iterable */
	PUSH_STRING,		/* Push a string as a new entrypoint */
	PUSH_INT,			/* Push an integer as a new entrypoint */
	PUSH_BOOLEAN,		/* Push a boolean as a new entrypoint */
	PUSH,				/* Push an existing entrypoint as a new entrypoint */
	LIST_MACROS,		/* List deployed macros */
	FILTER_MACROS,		/* Filter macros based on a given object type */
	GET_MACRO_PARAMS,	/* Retrieve a macro's parameters list (types and names) */
	RUN_MACRO,			/* Run a macro in a remote application */
	LOAD_MACRO			/* Dynamically load a macro in a remote application */
};

/**
 * RPC Message class
 * 
 * This class is used by Gson to deserialize client's requests.
 */

public class Request {
	
	/**
	 * Request's parameters. Not all of them will be deserialized. 
	 */
	
	public class Parameters {
		public String entrypoint;
		public int[] path;
		public int macro;
		public int[] params;
		public int intval;
		public String strval;
		public int item;
		public byte[] dex;
	}
	
	public RequestMethod method;
	public Parameters parameters;
	
	/**
	 * Retrieve request's method.
	 * 
	 * @return Request's method
	 */
	
	public RequestMethod getMethod() {
		return this.method;
	}
	
	/**
	 * Retrieve request parameters
	 * 
	 * @return a Request.Parameters instance
	 */
	
	public Parameters getParameters() {
		return this.parameters;
	}
	
	/**
	 * Create an instance of Request 
	 * @param json Serialized data required to build the request
	 * @return a Request instance built from the provided json data
	 */
	public static Request fromJson(String json) {
		GsonBuilder gsonb = new GsonBuilder();
		Gson gson = gsonb.create();
		return gson.fromJson(json, Request.class);
	}
}
