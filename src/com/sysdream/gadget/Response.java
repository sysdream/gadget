package com.sysdream.gadget;

import java.nio.ByteBuffer;
import java.util.Collection;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Response {
	
	public boolean success;
	public Object response;
	
	public Response(Object response, boolean success) {
		this.response = response;
		this.success = success;
	}
	
	public byte[] toJson() {
		GsonBuilder builder=new GsonBuilder();
		Gson gson = builder.create();
		return gson.toJson(this).getBytes();
	}
	
	public byte[] toRaw() {
		byte[] response = this.toJson();
		Log.d("Response", "Size of response: "+String.valueOf(response.length));
		ByteBuffer b = ByteBuffer.allocate(4+response.length);
		b.putInt(0, response.length);
		b.position(4);
		b.put(response);
		return b.array();
	}
}
