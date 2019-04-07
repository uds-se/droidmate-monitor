// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

// org.droidmate.monitor.MonitorSrcTemplate:REMOVE_LINES
// org.droidmate.monitor.MonitorSrcTemplate:UNCOMMENT_LINES
// package org.droidmate.monitor;
// org.droidmate.monitor.MonitorSrcTemplate:KEEP_LINES
package org.droidmate.monitor;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import org.droidmate.misc.MonitorConstants;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import de.larma.arthook.*;

/**
 * <p>
 * This class will be used by {@code MonitorGenerator} to create {@code Monitor.java} deployed on the device. This class will be
 * first copied by appropriate gradle task of monitor-generator project to its resources dir. Then it will be handled to
 * {@code org.droidmate.monitor.MonitorSrcTemplate} for further processing.
 *
 * </p><p>
 * Note that the final generated version of this file, after running {@code :project:pcComponents:monitor-generator:build}, will be placed in
 * <pre><code>
 *   [repo root]\dev\droidmate\projects\monitor-generator\monitor-apk-scaffolding\src\org\droidmate\monitor_generator\generated\Monitor.java
 * </code></pre>
 *
 * </p><p>
 * To check if the process of converting this file to a proper {@code Monitor.java} works correctly, see:
 * {@code org.droidmate.monitor.MonitorGeneratorFrontendTest#Generates DroidMate monitor()}.
 *
 * </p><p>
 * Note: The resulting class deployed to the device will be compiled with legacy ant script from Android SDK that supports only
 * Java 5.
 *
 * </p><p>
 * See also:<br/>
 * {@code org.droidmate.monitor.Monitor}<br/>
 * {@code org.droidmate.monitor.MethodGenerator}
 * </p>
 */
@SuppressLint("NewApi")
@SuppressWarnings("Convert2Diamond")
public class Monitor
{
	/**
	 * <p> Contains API logs gathered by monitor, to be transferred to the host machine when appropriate command is read by the
	 * TCP server.
	 * <p>
	 * </p><p>
	 * Each logcat is a 3 element array obeying following contract:<br/>
	 * logcat[0]: process ID of the logcat<br/>
	 * logcat[1]: timestamp of the logcat<br/>
	 * logcat[2]: the payload of the logcat (method name, parameter values, stack trace, etc.)
	 * <p>
	 * </p>
	 *
	 * @see org.droidmate.monitor.Monitor#addCurrentLogs(String)
	 */
	final static List<ArrayList<String>> currentLogs = new ArrayList<ArrayList<String>>();
	private final static String ESCAPE_CHAR = "\\";
	private final static String VALUE_STRING_ENCLOSING_CHAR = "'";
	private static final String FORMAT_STRING = "TId:%s;objCls:'%s';mthd:'%s';retCls:'void';params:'java.lang.String' '%s' 'java.lang.Object[]' %s;stacktrace:'%s'";
	private static final SimpleDateFormat monitor_time_formatter = new SimpleDateFormat(MonitorConstants.Companion.getMonitor_time_formatter_pattern(), MonitorConstants.Companion.getMonitor_time_formatter_locale());
	//endregion

	//region TCP server code
	/**
	 * @see #getNowDate()
	 */
	private static final Date startDate = new Date();
	/**
	 * @see #getNowDate()
	 */
	private static final long startNanoTime = System.nanoTime();
	private final static HashMap<ApiPolicyId, ApiPolicy> apiPolicies = new HashMap<>();
	private static MonitorTcpServer server;

	//endregion

	public Monitor(boolean skip)
	{
		if (skip)
			return;

		Log.v(MonitorConstants.Companion.getTag_mjt(), MonitorConstants.Companion.getMsg_ctor_start());
		try {
			server = startMonitorTCPServer();
			Log.i(MonitorConstants.Companion.getTag_mjt(), MonitorConstants.Companion.getMsg_ctor_success() + server.port);

		} catch (Throwable e) {
			Log.e(MonitorConstants.Companion.getTag_mjt(), MonitorConstants.Companion.getMsg_ctor_failure(), e);
		}
	}

	private static MonitorTcpServer startMonitorTCPServer() throws Throwable {
		Log.v(MonitorConstants.Companion.getTag_mjt(), "startMonitorTCPServer(): entering");

		MonitorTcpServer tcpServer = new MonitorTcpServer();

		final int port = getPort();
		Thread serverThread = tcpServer.tryStart(port);

		if (serverThread == null) {
			throw new Exception("startMonitorTCPServer(): Port is not available.");
		}

		if (tcpServer.isClosed()) {
			throw new Exception("startMonitorTCPServer(): Server is closed.");
		}

		Log.d(MonitorConstants.Companion.getTag_mjt(), "startMonitorTCPServer(): SUCCESS port: " + port + " PID: " + getPid());
		return tcpServer;
	}

	private static String escapeEnclosing(String paramString) {
		return paramString.replace(VALUE_STRING_ENCLOSING_CHAR, ESCAPE_CHAR + VALUE_STRING_ENCLOSING_CHAR);
	}

	private static String trimToLogSize(String paramString) {
        /*
        Logcat buffer size is 4096 [1]. I have encountered a case in which intent's string extra has eaten up entire logcat line,
        preventing the remaining parts of the logcat (in particular, stack trace) to be transferred to DroidMate,
        causing regex match fail. This is how the offending intent value looked like:

          intent:#Intent;action=com.picsart.studio.notification.action;S.extra.result.string=%7B%22response%22%3A%5B%7B%...
          ...<and_so_on_until_entire_line_buffer_was_eaten>

        [1] http://stackoverflow.com/questions/6321555/what-is-the-size-limit-for-logcat
        */
		if (paramString.length() > 1024) {
			return paramString.substring(0, 1024 - 24) + "_TRUNCATED_TO_1000_CHARS";
		}
		return paramString;
	}

	static String objectToString(Object param) {
		String result = "";
		if (param == null)
			result = "null";
		else if (param instanceof android.content.Intent) {
			String paramStr = ((android.content.Intent) param).toUri(Intent.URI_INTENT_SCHEME);
			if (!paramStr.endsWith("end")) throw new AssertionError();
			result = paramStr;
		} else if (param.getClass().isArray()) {
			result = Arrays.deepToString(convertToObjectArray(param));
		} else {
			result = param.toString();
		}

		return escapeEnclosing(result);
	}

	// Copied from http://stackoverflow.com/a/16428065/986533
	private static Object[] convertToObjectArray(Object array) {
		if (array == null) {
			return null;
		}

		Class ofArray = array.getClass().getComponentType();
		assert ofArray != null;
		if (ofArray.isPrimitive()) {
			ArrayList<Object> ar = new ArrayList<>();
			int length = Array.getLength(array);
			for (int i = 0; i < length; i++) {
				ar.add(Array.get(array, i));
			}
			return ar.toArray();
		} else {
			return (Object[]) array;
		}
	}

	private static String getStackTrace() {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < stackTrace.length; i++) {
			sb.append(stackTrace[i].toString());
			if (i < stackTrace.length - 1)
				sb.append("->");
		}
		return sb.toString();
	}

	private static long getThreadId() {
		return Thread.currentThread().getId();
	}

	/**
	 * <p>
	 * Called by monitor code to logcat Android API calls. Calls to this methods are generated in:
	 * <pre>
	 * org.droidmate.monitor.RedirectionsGenerator#generateCtorCallsAndTargets(java.util.List)
	 * org.droidmate.monitor.RedirectionsGenerator#generateMethodTargets(java.util.List)</pre>
	 * </p>
	 * This method has to be accessed in a synchronized manner to ensure proper access to the {@code currentLogs} list and also
	 * to ensure calls to {@code SimpleDateFormat.format(new Date())} return correct results.
	 * If there was interleaving between threads, the calls non-deterministically returned invalid dates,
	 * which caused {@code LocalDateTime.parse()} on the host machine, called by
	 * {@code org.droidmate.exploration.device.ApiLogsReader.extractLogcatMessagesFromTcpMessages()}
	 * to fail with exceptions like
	 * <pre>java.time.format.DateTimeParseException: Text '2015-08-21 019:15:43.607' could not be parsed at index 13</pre>
	 * <p>
	 * Examples of two different values returned by two consecutive calls to the faulty method,
	 * first bad, second good:
	 * <pre>
	 * 2015-0008-0021 0019:0015:43.809
	 * 2015-08-21 19:15:43.809
	 *
	 * 2015-08-21 19:015:43.804
	 * 2015-08-21 19:15:43.804</pre>
	 * More examples of faulty output:
	 * <pre>
	 *   2015-0008-05 09:24:12.163
	 *   2015-0008-19 22:49:50.492
	 *   2015-08-21 18:50:047.169
	 *   2015-08-21 19:03:25.24
	 *   2015-08-28 23:03:28.0453</pre>
	 */
	@SuppressWarnings("unused") // See javadoc
	private static void addCurrentLogs(String payload) {
		synchronized (currentLogs) {
//      Log.v(tag_mjt, "addCurrentLogs(" + payload + ")");
			String now = getNowDate();

//      Log.v(tag_mjt, "currentLogs.add(new ArrayList<String>(Arrays.asList(getPid(), now, payload)));");
			currentLogs.add(new ArrayList<String>(Arrays.asList(getPid(), now, payload)));

//      Log.v(tag_mjt, "addCurrentLogs(" + payload + "): DONE");
		}
	}

	/**
	 * <p>
	 * We use this more complex solution instead of simple {@code new Date()} because the simple solution uses
	 * {@code System.currentTimeMillis()} which is imprecise, as described here:
	 * http://stackoverflow.com/questions/2978598/will-sytem-currenttimemillis-always-return-a-value-previous-calls<br/>
	 * http://stackoverflow.com/a/2979239/986533
	 * <p>
	 * </p><p>
	 * Instead, we construct Date only once ({@link #startDate}), on startup, remembering also its time offset from last boot
	 * ({@link #startNanoTime}) and then we add offset to it in {@code System.nanoTime()},  which is precise.
	 * <p>
	 * </p>
	 */
	static String getNowDate() {
//    Log.v(tag_mjt, "final Date nowDate = new Date(startDate.getTime() + (System.nanoTime() - startNanoTime) / 1000000);");
		final Date nowDate = new Date(startDate.getTime() + (System.nanoTime() - startNanoTime) / 1000000);

//    Log.v(tag_mjt, "final String formattedDate = monitor_time_formatter.format(nowDate);");

//    Log.v(tag_mjt, "return formattedDate;");
		return monitor_time_formatter.format(nowDate);
	}

	static String getPid() {
		return String.valueOf(android.os.Process.myPid());
	}

	private static boolean skipLine(String line) {
		return (line.trim().length() == 0) ||
						!line.contains("\t") ||
						line.startsWith("#");
	}

	private static void processLine(String line) {
		if (skipLine(line))
			return;

		// first field is method signature
		// last field is policy
		// anything in between are URIs
		String[] lineData = line.split("\t");

		String methodName = lineData[0].replaceAll("\\s+", "");
		String policyStr = lineData[lineData.length - 1].trim();

		ApiPolicy policy = ApiPolicy.valueOf(policyStr);
		List<String> uriList = new ArrayList<>(Arrays.asList(lineData).subList(1, lineData.length - 1));

		apiPolicies.put(new ApiPolicyId(methodName, uriList.toArray(new String[0])), policy);
	}

	private static void initializeApiPolicies() throws Exception {
		// loads every time to allow restrictions to be dynamically changed
		apiPolicies.clear();

		File policiesFile = new File("#POLICIES_FILE_PATH");
		if (policiesFile.exists()) {
			try (BufferedReader reader = new BufferedReader(new FileReader(policiesFile))) {
				String line;
				while ((line = reader.readLine()) != null) {
					processLine(line);
				}
			}
		}
		//else
		//  Log.w(MonitorConstants.Companion.getTag_srv(), "Api policies file not found. Continuing with default behavior (Allow)");
	}

	/**
	 * Check is the API call should be allowed or not
	 *
	 * @param methodName Method that should have its policy checked
	 * @param uriList    List of resources being accessed by the method (if any)
	 * @return How how DroidMate behave regarding the policy. Default return is ApiPolicy.Allow
	 */
	@SuppressWarnings("unused")
	private static ApiPolicy getPolicy(String methodName, List<Uri> uriList) {
		try {
			initializeApiPolicies();

			for (ApiPolicyId apiId : apiPolicies.keySet()) {
				List<String> uriListStr = new ArrayList<>();
				for (Uri uri : uriList) {
					uriListStr.add(uri.toString());
				}

				if (apiId.affects(methodName, uriListStr))
					return apiPolicies.get(apiId);
			}
		} catch (Exception e) {
			// Default behavior is to allow
			return ApiPolicy.Allow;
		}

		return ApiPolicy.Allow;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static int getPort() throws Exception {
		File file = new File("#PORT_FILE_PATH");
		FileInputStream fis = new FileInputStream(file);
		byte[] data = new byte[(int) file.length()];
		fis.read(data);
		fis.close();

		return Integer.parseInt(new String(data, StandardCharsets.UTF_8));
	}

	/**
	 * Called by the inlined Application class when the inlined AUE launches activity, as done by
	 * org.droidmate.exploration.device.IRobustDevice#launchApp(org.droidmate.device.android_sdk.IApk)
	 */
	@SuppressWarnings("unused")
	public void init(android.content.Context initContext) {
		Log.v(MonitorConstants.Companion.getTag_mjt(), "init(): entering");
		//region Helper code
		if (server == null) {
			Log.w(MonitorConstants.Companion.getTag_mjt(), "init(): didn't set context for MonitorTcpServer, as the server is null.");
		} else {
			server.context = initContext;
		}

		ArtHook.hook(Monitor.class);

		Log.d(MonitorConstants.Companion.getTag_mjt(), MonitorConstants.Companion.getMsgPrefix_init_success() + initContext.getPackageName());
	}

	//endregion

	//region Generated code

	// GENERATED_CODE_INJECTION_POINT:METHOD_REDIR_TARGETS

	//endregion
}

