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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.droidmate.device.apis.ApiMethodSignature
import org.droidmate.legacy.Resource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Compiler {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty() || args.size > 2) {
                println("Usage instructions: The following configurations are supported:\n" +
                        "-- <DESTINATION DIRECTORY FOR COMPILED APK>\n" +
                        "-- <DESTINATION DIRECTORY FOR COMPILED APK> <API LIST FILE>\n" +
                        "If not file is used, the monitor will be compiled with the default API list")
                return
            }

            val dstDir = Paths.get(args[0])
            val apiFile = if (args.size > 1) {
                Paths.get(args[1])
            } else {
                null
            }

            val dstFile = compile(dstDir, apiFile)
            println("Compiled apk moved to: $dstFile")
        }

        /**
         * Converts a JSON file into a list of APIs
         *
         * @param apiFile File to be read
         * @return List of API signatures
         * @throws IOException if the API file cannot be read
         */
        @JvmStatic
        @Throws(IOException::class)
        fun generateMethods(apiFile: Path): List<ApiMethodSignature> {
            val fileData = Files.readAllLines(apiFile).joinToString(System.lineSeparator())
            val jsonApiList = JsonParser().parse(fileData).asJsonObject

            return jsonApiList.get("apis")
                .asJsonArray
                .map { item ->
                    val objApi = item as JsonObject
                    val className = objApi.get("className").asString
                    val hookedMethod = objApi.get("hookedMethod").asString
                    val signature = objApi.get("signature").asString
                    val invokeAPICode = objApi.get("invokeAPICode").asString
                    val defaultReturnValue = objApi.get("defaultReturnValue").asString
                    val exceptionType = objApi.get("exceptionType").asString
                    val logID = objApi.get("logID").asString
                    val methodName = objApi.get("methodName").asString
                    val paramList = objApi.get("paramList").asJsonArray.map { it.asString }
                    val returnType = objApi.get("returnType").asString
                    val isStatic = objApi.get("isStatic").asBoolean

                    ApiMethodSignature.fromDescriptor(
                        className, returnType, methodName, paramList,
                        isStatic, hookedMethod, signature, logID, invokeAPICode, defaultReturnValue, exceptionType
                    )
                }
        }

        /**
         * Converts a JSON file into a list of APIs
         *
         * @param dstDir Directory where the compile APK will be stored
         * @param apiFile File to be read. If none is chosen, use default API list
         * @throws IOException if the API file cannot be read
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class)
        fun compile(dstDir: Path, apiFile: Path? = null): Path {
            val methods = if (apiFile != null) {
                generateMethods(apiFile)
            } else {
                Resource("monitored_apis.json").withExtractedPath {
                    generateMethods(it)
                }
            }
            return compile(dstDir, methods)
        }

        /**
         * Converts a JSON file into a list of APIs
         *
         * @param dstDir Directory where the compile APK will be stored
         * @param methods List of APIs to inject into to monitor
         * @throws IOException if the API file cannot be read
         */
        @JvmStatic
        fun compile(dstDir: Path, methods: List<ApiMethodSignature>): Path {
            return MonitorProject(methods).use {
                it.instrument(dstDir)
            }
        }
    }
}
