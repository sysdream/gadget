package com.sysdream.gadget;

import android.content.Context;

public interface IGadgetService {
	public void startServer(String address, int port, int mode);
	public void stopServer();
	public int getMode();
	public String getAddress();
	public int getPort();
}
