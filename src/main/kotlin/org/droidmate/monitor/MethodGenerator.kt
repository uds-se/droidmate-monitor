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

package org.droidmate.monitor

import org.droidmate.device.apis.ApiMethodSignature
import org.droidmate.misc.MonitorConstants

/**
 * <p>
 * Class that add the instrumentation code to {@link MonitorJavaTemplate}
 *
 * </p><p>
 * To diagnose method signatures here that cannot be handled by ArtHook (which is used for Android 6), observe logcat output
 * during launch of main activity of an inlined app containing monitor generated using this class.
 *
 * A logcat similar to the following one will appear on it:
 * <pre>
 * 06-29 19:17:21.637 16375-16375/org.droidmate.fixtures.apks.monitored W/ArtHook: java.lang.RuntimeException: Can't find original method (redir_android_net_wifi_WifiManager_startScan1)
 * </pre>
 *
 * </p><p>
 * Information about update to Android 6.0:
 *
 * </p><p>
 * Using AAR on ANT Script:<br/>
 *    http://community.openfl.org/t/integrating-aar-files/6837/2
 *    http://stackoverflow.com/questions/23777423/aar-in-eclipse-ant-project
 *
 * Using legacy org.apache.http package on Android 6.0<br/>
 *    http://stackoverflow.com/questions/33357561/compiling-google-download-library-targing-api-23-android-marshmallow
 *    http://stackoverflow.com/questions/32064633/how-to-include-http-library-in-android-project-using-m-preview-in-eclipse-ant-bu
 *    (Not working, just for information) http://stackoverflow.com/questions/31653002/how-to-use-the-legacy-apache-http-client-on-android-marshmallow
 * </p>
 *
 */
private val nl = System.lineSeparator()

private val ind4 = "    "

fun ApiMethodSignature.toRedirectCode(): String {
    return if (this.objectClass.startsWith("android.test."))
        ""
    else {
        val out = StringBuilder()

        out.append(String.format("@Hook(\"%s\")", this.hook) + nl)
        out.append(String.format("public static %s %s", this.returnClass, this.name) + nl)
        out.append("{$nl")

        /**
         * MonitorJavaTemplate and MonitorTcpServer have calls to Log.i() and Log.v() in them, whose tag starts with
         * MonitorConstants.tag_prefix. This conditional ensures
         * such calls are not being monitored,
         * as they are DroidMate's monitor internal code, not the behavior of the app under exploration.
         */
        if (this.objectClass == "android.util.Log" && (this.methodName in arrayListOf(
                "v",
                "d",
                "i",
                "w",
                "e"
            )) && paramClasses.size in arrayListOf(2, 3)
        ) {
            out.append(ind4 + ind4 + "if (p0.startsWith(\"${MonitorConstants.tag_prefix}\"))" + nl)
            when {
                paramClasses.size == 2 -> out.append("$ind4$ind4  return OriginalMethod.by(new \$() {}).invokeStatic(p0, p1);$nl")
                paramClasses.size == 3 -> out.append("$ind4$ind4  return OriginalMethod.by(new \$() {}).invokeStatic(p0, p1, p2);$nl")
                else -> assert(false) { "paramClasses.size() is not in [2,3]. It is ${paramClasses.size}" }
            }
        }

        out.append(ind4 + "String stackTrace = getStackTrace();" + nl)
        out.append(ind4 + "long threadId = getThreadId();" + nl)
        out.append(ind4 + String.format("String logSignature = %s;", this.logId) + nl)
        out.append(
            ind4 + String.format(
                "Log.%s(\"%s\", logSignature);",
                MonitorConstants.loglevel,
                MonitorConstants.tag_api
            ) + nl
        )
        out.append(ind4 + "addCurrentLogs(logSignature);" + nl)

        out.append(ind4 + "List<Uri> uriList = new ArrayList<>();" + nl)

        (0 until this.paramClasses.size).forEach { x ->
            if (this.paramClasses[x] == "android.net.Uri")
                out.append(ind4 + "uriList.add(p$x);" + nl)
        }
        out.append(ind4 + "ApiPolicy policy = getPolicy(\"${this.getShortSignature()}\", uriList);" + nl)
        // Currently, when denying, the method is not being called
        out.append(ind4 + "switch (policy){ " + nl)
        out.append(ind4 + ind4 + "case Allow: " + nl)
        // has an embedded return, no need for break
        out.append(ind4 + ind4 + ind4 + this.invokeCode + nl)
        out.append(ind4 + ind4 + "case Mock: " + nl)
        out.append(ind4 + ind4 + ind4 + String.format("return %s;", this.defaultValue) + nl)
        out.append(ind4 + ind4 + "case Deny: " + nl)
        out.append(ind4 + ind4 + ind4 + "${this.exceptionType} e = new ${this.exceptionType}(\"API ${this.objectClass}->${this.methodName} was blocked by DroidMate\");" + nl)
        out.append(
            ind4 + ind4 + ind4 + String.format(
                "Log.e(\"%s\", e.getMessage());",
                MonitorConstants.tag_api
            ) + nl
        )
        out.append(ind4 + ind4 + ind4 + "throw e;" + nl)
        out.append(ind4 + ind4 + "default:" + nl)
        out.append(ind4 + ind4 + ind4 + "throw new RuntimeException(\"Policy for api ${this.objectClass}->${this.methodName} cannot be determined.\");" + nl)
        out.append("$ind4}$nl")

        out.append("}$nl")
        out.append(ind4 + nl)

        out.toString()
    }
}
