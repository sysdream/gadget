package com.sysdream.gadget;


public interface IGadgetService {
	public void startServer(String address, int port, int mode);
	public void stopServer();
	public int getMode();
	public String getAddress();
	public int getPort();
}
