package org.droidmate.monitor

import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ApkInlinerTest {
    val baseDir = "src/test/kotlin/org/droidmate/monitor/resources/"
    val input = baseDir + "in/"
    val output = baseDir + "out/"
    val workingDir = baseDir + "workingdir/"

    val workingPath = Paths.get(workingDir)
    val inputPath = Paths.get(input)
    val outputPath = Paths.get(output)

    @Before
    fun setUp() {
        assert(Files.exists(inputPath) && Files.isDirectory(inputPath))
        assert(Files.exists(outputPath) && Files.isDirectory(outputPath))
        assert(Files.exists(workingPath) && Files.isDirectory(workingPath))

        // Make sure no files from previous test are present
        // --> Recursively delete files in output directory
        Files.walk(outputPath)
            .sorted(Comparator.reverseOrder())
            .filter { p -> !p.equals(outputPath) }
            .map(Path::toFile)
            // .peek(System.out::println)
            .forEach { it.delete() }
    }

    @Test
    fun `Test instrumentApk method with multiple apks`() {
        val fileList = Files.list(inputPath)
            .toList()
            .filter { p -> p.fileName.toString().endsWith(".apk") }

        val instrFileList = mutableListOf<Path>()

        // Generate list of corresponding *-inlined.apk's paths that *should* be generated
        for (p in fileList) {
            instrFileList.add(Paths.get(output + p.fileName.toString().substring(0, p.fileName.toString().length - 4) + "-inlined.apk"))
        }

        // Call method under test
        var AI = ApkInliner(workingPath)
        AI.instrumentApk(inputPath, outputPath)

        // Check 1: All *-inlined.apk's were generated
        instrFileList.forEach { p -> assert(Files.exists(p)) }

        // Check 2: File size of *-inlined.apk's is > 0
        // instrFileList.forEach { p -> println(p.toFile().length()) }
        instrFileList.forEach { p -> assert(p.toFile().length() > 0) }
    }
}