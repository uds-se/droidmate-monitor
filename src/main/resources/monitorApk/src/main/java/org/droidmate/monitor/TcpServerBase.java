package org.droidmate.monitor;

import android.util.Log;
import org.droidmate.misc.MonitorConstants;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.SocketException;

abstract class TcpServerBase<ServerInputT extends Serializable, ServerOutputT extends Serializable> {
	private final Object mLock = new Object();
	int port;
	ServerSocket serverSocket = null;
	SocketException serverSocketException = null;

	TcpServerBase() {
		super();
	}

	protected abstract ServerOutputT OnServerRequest(ServerInputT input);

	protected abstract boolean shouldCloseServerSocket(ServerInputT serverInput);

	Thread tryStart(int port) throws Exception {
		Log.v(MonitorConstants.Companion.getTag_srv(), String.format("tryStart(port:%d): entering", port));
		this.serverSocket = null;
		this.serverSocketException = null;
		this.port = port;

		MonitorServerRunnable<ServerInputT, ServerOutputT> monitorServerRunnable = new MonitorServerRunnable<>(this);
		Thread serverThread = new Thread(monitorServerRunnable);
		serverThread.setDaemon(true); // ensure termination if the main thread dies
		// For explanation why this synchronization is necessary, see MonitorServerRunnable.run() method synchronized {} block.
		synchronized (mLock) {
			if (!(serverSocket == null && serverSocketException == null)) {
				throw new AssertionError();
			}

			serverThread.start();
			monitorServerRunnable.wait();

			// Either a serverSocket has been established, or an exception was thrown, but not both.
			if (serverSocket != null || serverSocketException != null) {
				Throwable error = (serverSocketException.getCause() != null) ?
						serverSocketException.getCause() : serverSocketException;
				String cause = error.getMessage();

				Log.e(MonitorConstants.Companion.getTag_srv(), "tryStart(port:" + port +
						"): FAILURE Failed to start TCP server because " + cause, error);

				throw new Exception(String.format("Failed to start monitor TCP server thread for port %s. " +
						"Cause of this exception is %s ", port, cause),
						error);
			}
		}

		Log.d(MonitorConstants.Companion.getTag_srv(), "tryStart(port:" + port + "): SUCCESS");
		return serverThread;
	}

	void closeServerSocket() {
		try {
			serverSocket.close();
			Log.d(MonitorConstants.Companion.getTag_srv(), String.format("serverSocket.close(): SUCCESS port %s", port));

		} catch (IOException e) {
			Log.e(MonitorConstants.Companion.getTag_srv(), String.format("serverSocket.close(): FAILURE port %s", port));
		}
	}

	boolean isClosed() {
		return serverSocket.isClosed();
	}
}