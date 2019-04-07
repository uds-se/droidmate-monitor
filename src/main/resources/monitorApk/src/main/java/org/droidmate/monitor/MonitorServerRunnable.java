package org.droidmate.monitor;

import org.droidmate.misc.MonitorConstants;

import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

class MonitorServerRunnable<ServerInputT extends Serializable, ServerOutputT extends Serializable> implements Runnable {
	private TcpServerBase<ServerInputT, ServerOutputT> server;

	MonitorServerRunnable(TcpServerBase<ServerInputT, ServerOutputT> server) {
		this.server = server;
	}

	public void run() {

		Log.v(MonitorConstants.Companion.getTag_run(), String.format("run(): entering port:%d", server.port));
		try {

			// Synchronize to ensure the parent thread (the one which started this one) will continue only after one of these two
			// is true:
			// - serverSocket was successfully initialized
			// - exception was thrown and assigned to a field and  this thread exited
			synchronized (this) {
				try {
					Log.v(MonitorConstants.Companion.getTag_run(), String.format("serverSocket = new ServerSocket(%d)", server.port));
					server.serverSocket = new ServerSocket(server.port);
					Log.v(MonitorConstants.Companion.getTag_run(), String.format("serverSocket = new ServerSocket(%d): SUCCESS", server.port));
				} catch (SocketException e) {
					server.serverSocketException = e;
				}

				if (server.serverSocketException != null) {
					Log.d(MonitorConstants.Companion.getTag_run(), "serverSocket = new ServerSocket(" + server.port + "): FAILURE " +
							"aborting further thread execution.");
					this.notify();
					return;
				} else {
					this.notify();
				}
			}

			if (server.serverSocket == null) {
				throw new AssertionError();
			}

			if (server.serverSocketException != null) {
				throw new AssertionError();
			}

			while (!server.serverSocket.isClosed()) {
				Log.v(MonitorConstants.Companion.getTag_run(), String.format("clientSocket = serverSocket.accept() / port:%d", server.port));
				Socket clientSocket = server.serverSocket.accept();
				Log.v(MonitorConstants.Companion.getTag_run(), String.format("clientSocket = serverSocket.accept(): SUCCESS / port:%d", server.port));

				////ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
				DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

				/*
				 * Flushing done to prevent client blocking on creation of input stream reading output from this stream. See:
				 * org.droidmate.device.SerializableTCPClient.queryServer
				 *
				 * References:
				 * 1. http://stackoverflow.com/questions/8088557/getinputstream-blocks
				 * 2. Search for: "Note - The ObjectInputStream constructor blocks until" in:
				 * http://docs.oracle.com/javase/7/docs/platform/serialization/spec/input.html
				 */
				////output.flush();

				DataInputStream input = new DataInputStream(clientSocket.getInputStream());
				ServerInputT serverInput;

				try {
					@SuppressWarnings("unchecked")
					// Without this var here, there is no place to put the "unchecked" suppression warning.
					ServerInputT localVarForSuppressionAnnotation = (ServerInputT) SerializationHelper.readObjectFromStream(input);
					serverInput = localVarForSuppressionAnnotation;

				} catch (Exception e) {
					Log.e(MonitorConstants.Companion.getTag_run(), "! serverInput = input.readObject(): FAILURE " +
							"while reading from clientSocket on port " + server.port + ". Closing server socket.", e);
					server.closeServerSocket();
					break;
				}

				ServerOutputT serverOutput;
				Log.d(MonitorConstants.Companion.getTag_run(), String.format("OnServerRequest(%s) / port:%d", serverInput, server.port));
				serverOutput = server.OnServerRequest(serverInput);
				SerializationHelper.writeObjectToStream(output, serverOutput);
				clientSocket.close();

				if (server.shouldCloseServerSocket(serverInput)) {
					Log.v(MonitorConstants.Companion.getTag_run(), String.format("shouldCloseServerSocket(): true / port:%d", server.port));
					server.closeServerSocket();
				}
			}

			if (!server.serverSocket.isClosed()) {
				throw new AssertionError();
			}

			Log.v(MonitorConstants.Companion.getTag_run(), String.format("serverSocket.isClosed() / port:%d", server.port));

		} catch (SocketTimeoutException e) {
			Log.e(MonitorConstants.Companion.getTag_run(), "! Closing monitor TCP server due to a timeout.", e);
			server.closeServerSocket();
		} catch (IOException e) {
			Log.e(MonitorConstants.Companion.getTag_run(), "! Exception was thrown while operating monitor TCP server.", e);
		}
	}
}