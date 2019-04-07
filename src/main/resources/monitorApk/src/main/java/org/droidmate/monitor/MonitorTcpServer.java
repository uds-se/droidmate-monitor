package org.droidmate.monitor;

import android.content.Context;
import android.util.Log;
import org.droidmate.misc.MonitorConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

class MonitorTcpServer extends TcpServerBase<String, LinkedList<ArrayList<String>>> {

	private final Object mLock = new Object();

	Context context;

	MonitorTcpServer() {
		super();
	}

	@Override
	protected LinkedList<ArrayList<String>> OnServerRequest(String input) {
		synchronized (mLock) {
			validateLogsAreNotFromMonitor();

			if (MonitorConstants.Companion.getSrvCmd_connCheck().equals(input)) {
				final ArrayList<String> payload = new ArrayList<>(Arrays.asList(Monitor.getPid(), getPackageName(), ""));
				return new LinkedList<>(Collections.singletonList(payload));

			} else if (MonitorConstants.Companion.getSrvCmd_get_logs().equals(input)) {
				LinkedList<ArrayList<String>> logsToSend = new LinkedList<>(Monitor.currentLogs);
				Monitor.currentLogs.clear();

				return logsToSend;

			} else if (MonitorConstants.Companion.getSrvCmd_get_time().equals(input)) {
				final String time = Monitor.getNowDate();

				final ArrayList<String> payload = new ArrayList<>(Arrays.asList(time, null, null));

				Log.d(MonitorConstants.Companion.getTag_srv(), "getTime: " + time);
				return new LinkedList<>(Collections.singletonList(payload));

			} else if (MonitorConstants.Companion.getSrvCmd_close().equals(input)) {
				// In addition to the logic above, this command is handled in
				// org.droidmate.monitor.MonitorJavaTemplate.MonitorTcpServer.shouldCloseServerSocket

				return new LinkedList<>();

			} else {
				Log.e(MonitorConstants.Companion.getTag_srv(), "! Unexpected command from DroidMate TCP client. The command: " + input);
				return new LinkedList<>();
			}
		}
	}

	private String getPackageName() {
		if (this.context != null)
			return this.context.getPackageName();
		else
			return "package name unavailable: context is null";
	}

	/**
	 * <p>
	 * This method ensures the logs do not come from messages logged by the MonitorTcpServer or
	 * MonitorJavaTemplate itself. This would be a bug and thus it will cause an assertion failure in this method.
	 * <p>
	 * </p>
	 */
	private void validateLogsAreNotFromMonitor() {
		for (ArrayList<String> log : Monitor.currentLogs) {
			// ".get(2)" gets the payload. For details, see the doc of the param passed to this method.
			String msgPayload = log.get(2);
			failOnLogsFromMonitorTCPServerOrMonitorJavaTemplate(msgPayload);

		}
	}

	private void failOnLogsFromMonitorTCPServerOrMonitorJavaTemplate(String msgPayload) {
		if (msgPayload.contains(MonitorConstants.Companion.getTag_srv()) || msgPayload.contains(MonitorConstants.Companion.getTag_mjt()))
			throw new AssertionError(
					"Attempt to logcat a message whose payload contains " +
							MonitorConstants.Companion.getTag_srv() + " or " + MonitorConstants.Companion.getTag_mjt() + ". The message payload: " + msgPayload);
	}

	@Override
	protected boolean shouldCloseServerSocket(String serverInput) {
		return MonitorConstants.Companion.getSrvCmd_close().equals(serverInput);
	}
}