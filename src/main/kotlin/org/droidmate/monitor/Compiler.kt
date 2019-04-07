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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Compiler {
	companion object {
		@JvmStatic
		fun main(args: Array<String>) {
			val apiFile = Paths.get("./input/monitored_apis.json")
			val dstDir = Paths.get("./tmp")
			val dstFile = compile(apiFile, dstDir)
			println("Compiled apk moved to: $dstFile")
		}

		@JvmStatic
		private fun generateMethods(apiFile: Path): List<ApiMethodSignature> {
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

					ApiMethodSignature.fromDescriptor(className, returnType, methodName, paramList,
						isStatic, hookedMethod, signature, logID, invokeAPICode, defaultReturnValue, exceptionType)
				}
		}

		@JvmStatic
		fun compile(apiFile: Path, dstDir: Path): Path {
			val methods = generateMethods(apiFile)
			return compile(methods, dstDir)
		}

		@JvmStatic
		fun compile(methods: List<ApiMethodSignature>, dstDir: Path): Path {
			return MonitorProject(methods).use {
				it.instrument(dstDir)
			}
		}
	}
}
