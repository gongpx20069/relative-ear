package io.github.gongpx20069.relativeear

import android.os.SystemClock
import android.util.Xml
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.File

class UiReportListener : RunListener() {
    private data class Entry(val description: Description, val start: Long = SystemClock.elapsedRealtime(),
        var durationMs: Long = 0, val failures: MutableList<Failure> = mutableListOf(), var skipped: Boolean = false)
    private val entries = linkedMapOf<Description, Entry>()
    override fun testStarted(description: Description) { entries[description] = Entry(description) }
    override fun testFailure(failure: Failure) {
        entries.getOrPut(failure.description) { Entry(failure.description) }.failures.add(failure)
    }
    override fun testAssumptionFailure(failure: Failure) {
        entries.getOrPut(failure.description) { Entry(failure.description) }.skipped = true
    }
    override fun testIgnored(description: Description) { entries[description] = Entry(description, skipped = true) }
    override fun testFinished(description: Description) {
        entries.getValue(description).let { it.durationMs = SystemClock.elapsedRealtime() - it.start }
    }
    override fun testRunFinished(result: Result) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("ui-snapshots"))
        File(directory, "instrumentation-results.xml").outputStream().use { output ->
            val xml = Xml.newSerializer()
            xml.setOutput(output, "UTF-8")
            xml.startDocument("UTF-8", true)
            xml.startTag(null, "testsuite").attribute(null, "name", "Android instrumentation")
                .attribute(null, "tests", entries.size.toString())
                .attribute(null, "failures", entries.values.count { it.failures.isNotEmpty() }.toString())
                .attribute(null, "errors", "0")
                .attribute(null, "skipped", entries.values.count { it.skipped }.toString())
            entries.values.forEach { entry ->
                xml.startTag(null, "testcase").attribute(null, "classname", entry.description.className ?: "")
                    .attribute(null, "name", entry.description.methodName ?: entry.description.displayName)
                    .attribute(null, "time", (entry.durationMs / 1000.0).toString())
                if (entry.skipped) xml.startTag(null, "skipped").endTag(null, "skipped")
                entry.failures.forEach {
                    xml.startTag(null, "failure").attribute(null, "type", it.exception.javaClass.name)
                        .text(it.trace).endTag(null, "failure")
                }
                xml.endTag(null, "testcase")
            }
            xml.endTag(null, "testsuite")
            xml.endDocument()
        }
    }
}
