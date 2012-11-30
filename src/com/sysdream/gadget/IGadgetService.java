package com.sysdream.gadget;

<<<<<<< HEAD
import android.content.Context;

=======
>>>>>>> 78f85900803ae936624f4738ce906b73a729268e
public interface IGadgetService {
	public void startServer(String address, int port, int mode);
	public void stopServer();
	public int getMode();
	public String getAddress();
	public int getPort();
}
