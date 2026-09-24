package com.shoppingconnect.aistudio.device

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-device QA results. Every instrumented test appends a row here; scripts/device_qa.sh pulls
 * /sdcard/Android/data/<pkg>/files/qa/ back to docs/device-qa/. Nothing here is ever simulated:
 * a row is written only after the step actually ran on the device.
 */
object QaLog {
    val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val dir: File get() = File(target.getExternalFilesDir(null), "qa").apply { mkdirs() }
    private val file get() = File(dir, "results.md")

    @Synchronized
    fun row(test: String, result: String, notes: String) {
        if (!file.exists()) file.writeText("| Test | Result | Notes |\n|---|---|---|\n")
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        file.appendText("| $test | $result | ${notes.replace("|", "/").replace("\n", " ")} ($ts) |\n")
    }

    fun arg(name: String): String? = InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }
}
