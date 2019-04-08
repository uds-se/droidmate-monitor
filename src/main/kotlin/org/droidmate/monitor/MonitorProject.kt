// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and", "or modify
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
// along with this program.  If not, see <http://www.gnu.org", "licenses", ">.
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
import org.droidmate.legacy.OS
import org.droidmate.legacy.Resource
import org.droidmate.legacy.deleteDirectoryRecursively
import org.droidmate.legacy.replaceText
import org.droidmate.misc.EnvironmentConstants
import org.droidmate.misc.EnvironmentConstants.Companion.monitor_apk_name
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.SysCmdExecutor
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class MonitorProject constructor(
    private val methods: List<ApiMethodSignature>,
    private val executor: ISysCmdExecutor = SysCmdExecutor()
) : Closeable {
    companion object {
        private val log by lazy { LoggerFactory.getLogger(SysCmdExecutor::class.java) }

        @JvmStatic
        private val unpackedMonitorRepository by lazy {
            val tempDir = Files.createTempDirectory("monitorToCompile")
            Resource("monitorApk/").extractTo(tempDir).toAbsolutePath()
        }

        @JvmStatic
        private val monitorFile by lazy {
            unpackedMonitorRepository
                .resolve("src")
                .resolve("main")
                .resolve("java")
                .resolve("org")
                .resolve("droidmate")
                .resolve("monitor")
                .resolve("Monitor.java")
        }

        @JvmStatic
        private val compiledApk by lazy {
            unpackedMonitorRepository
                .resolve("build")
                .resolve("outputs")
                .resolve("apk")
                .resolve("release")
                .resolve("monitorApk-release-unsigned.apk")
        }
    }

    override fun close() {
        try {
            unpackedMonitorRepository.deleteDirectoryRecursively()
            log.debug("Temporary monitor compilation directories cleaned up")
        } catch (e: Exception) {
            log.error("Unable to clean up temporary monitor apk directory: ${e.message}", e)
        }
    }

    /**
     * Generate code for API redirection
     */
    private fun injectRedirectionCode() {
        log.debug("Injecting API redirection code into monitor class")
        val methodCode = methods.joinToString(System.lineSeparator()) { it.toRedirectCode() }

        monitorFile.replaceText("GENERATED_CODE_INJECTION_POINT:METHOD_REDIR_TARGETS",
            "${System.lineSeparator()}$methodCode")
    }

    /**
     * Generate code for Port and policies file path
     */
    private fun injectFilePaths() {
        monitorFile.replaceText(
            "#POLICIES_FILE_PATH",
            EnvironmentConstants.AVD_dir_for_temp_files + EnvironmentConstants.api_policies_file_name
        )
        monitorFile.replaceText(
            "#PORT_FILE_PATH",
            EnvironmentConstants.AVD_dir_for_temp_files + EnvironmentConstants.monitor_port_file_name
        )
    }

    /**
     * Move compiled APK from temporary directory to destination directory
     *
     * @param dstDir Directory to copy the compiled APK to
     * @return Path of the compiled APK within the [dstDir]
     */
    private fun copyApkTo(dstDir: Path): Path {
        if (!Files.exists(compiledApk)) {
            throw IllegalStateException("Compiled Monitor APK not found under $compiledApk")
        }

        Files.createDirectories(dstDir)
        val dstFile = dstDir.resolve(monitor_apk_name)

        Files.copy(compiledApk, dstFile, StandardCopyOption.REPLACE_EXISTING)

        return dstFile
    }

    /**
     * Execute gradle build task on the temporary project to build the monitor
     */
    private fun buildApk() {
        executor.executeWithoutTimeout(
            "Building monitor apk",
            "$unpackedMonitorRepository/gradlew${if (OS.isWindows) ".bat" else "" }",
            "clean",
            "build",
            "-p", "$unpackedMonitorRepository"
        )
    }

    /**
     * Build the redirection APK and copy it to the (destination directory)[dstDir]
     *
     * @param dstDir Directory to copy the compiled APK to
     * @return Path of the compiled APK within the [dstDir]
     */
    fun instrument(dstDir: Path): Path {
        injectRedirectionCode()
        injectFilePaths()

        buildApk()

        return copyApkTo(dstDir)
    }
}