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

import org.droidmate.legacy.Resource
import org.droidmate.misc.Dex
import org.droidmate.misc.EnvironmentConstants
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.Jar
import org.droidmate.misc.JarsignerWrapper
import org.droidmate.misc.SysCmdExecutor
import org.slf4j.LoggerFactory
import java.nio.file.Files

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.streams.toList

class ApkInliner @JvmOverloads constructor(
    private val resourceDir: Path,
    private val sysCmdExecutor: ISysCmdExecutor = SysCmdExecutor()
) {

    private val jarsignerWrapper by lazy {
        JarsignerWrapper(sysCmdExecutor,
            EnvironmentConstants.jarsigner.toAbsolutePath(),
            Resource("debug.keystore").extractTo(resourceDir))
    }

    private val inlinerJar by lazy { Jar(Resource("appguard-inliner.jar").extractTo(resourceDir)) }

    private val appGuardLoader by lazy { Dex(Resource("appguard-loader.dex").extractTo(resourceDir)) }

    private val monitorClassName: String = "org.droidmate.monitor.Monitor"

    private val pathToMonitorApkOnAndroidDevice: String =
        EnvironmentConstants.AVD_dir_for_temp_files + EnvironmentConstants.monitor_apk_name

    fun instrumentApk(inputPath: Path, outputDir: Path) {
        if (!Files.exists(inputPath))
            Files.createDirectories(inputPath)
        if (!Files.isDirectory(inputPath))
            assert(Files.exists(inputPath))
        assert(Files.isDirectory(outputDir))

        if (Files.isDirectory(inputPath)) {
            val fileList = Files.list(inputPath)
                .toList()
                .filter { p -> p.fileName.toString().endsWith(".apk") }
                .filterNot { p -> p.fileName.toString().endsWith("-inlined.apk") }

            if (fileList.isEmpty()) {
                log.warn("No target apks for inlining found. Searched directory: $inputPath.\nAborting inlining.")
                return
            }

            fileList.forEach { apk -> inlineApkIntoDir(apk, outputDir) }
        } else {
            inlineApkIntoDir(inputPath, outputDir)
        }
    }

    /**
     * <p>
     * Inlines apk at path {@code apkPath} and puts its inlined version in {@code outputDir}.
     *
     * </p><p>
     * For example, if {@code apkPath} is:
     *
     *   /abc/def/calc.apk
     *
     * and {@code outputDir} is:
     *
     *   /abc/def/out/
     *
     * then the output inlined apk will have path
     *
     *   /abc/def/out/calc-inlined.apk
     *
     * </p>
     *
     * @param apk
     * @param outputDir
     * @return
     */
    private fun inlineApkIntoDir(apk: Path, outputDir: Path): Path {
        val unsignedInlinedApk = executeInlineApk(apk)
        assert(unsignedInlinedApk.fileName.toString().endsWith("-inlined.apk"))

        val signedInlinedApk = jarsignerWrapper.signWithDebugKey(unsignedInlinedApk)

        return Files.move(signedInlinedApk, outputDir.resolve(signedInlinedApk.fileName.toString()),
            StandardCopyOption.REPLACE_EXISTING)
    }

    private fun executeInlineApk(targetApk: Path): Path {
        val newFileName = targetApk.fileName.toString().replace(".apk", "-inlined.apk")
        val inlinedApkPath = targetApk.resolveSibling(newFileName)
        assert(Files.notExists(inlinedApkPath))

        sysCmdExecutor.execute(
            "Inlining $targetApk",
            "java",
            "-jar",
            inlinerJar.path.toString(),
            targetApk.toString(),
            appGuardLoader.path.toString(),
            pathToMonitorApkOnAndroidDevice,
            monitorClassName)

        assert(Files.exists(inlinedApkPath))
        return inlinedApkPath
    }

    companion object {
        private val log by lazy { LoggerFactory.getLogger(this::class.java) }

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 2) {
                println("Usage instructions: The following configurations are supported:\n" +
                        "-- <APK FILE OR SOURCE DIR> <OUTPUT DIR>")
                return
            }

            val srcPath = Paths.get(args[0])
            val dstDir = Paths.get(args[1])
            val resourceDir = Paths.get("tmp")
            ApkInliner(resourceDir).instrumentApk(srcPath, dstDir)
            println("Inlined APK from $srcPath into $dstDir")
        }
    }
}
