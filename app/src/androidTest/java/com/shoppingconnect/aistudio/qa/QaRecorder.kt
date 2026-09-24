package com.shoppingconnect.aistudio.qa

import android.graphics.Bitmap
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * Records device QA measurements as JSON lines (plus screenshots) under the app's external files
 * dir, where scripts/device-qa.sh pulls them with adb. Nothing here decides PASS/FAIL — assertions
 * in the tests do; this only keeps the evidence (numbers, codec names, images) for the reports.
 */
object QaRecorder {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    val dir: File get() = File(ctx.getExternalFilesDir(null), "qa").apply { mkdirs() }

    fun record(item: String, status: String, vararg data: Pair<String, Any?>) {
        val o = JSONObject().put("item", item).put("status", status).put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("sdk", Build.VERSION.SDK_INT)
        data.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        File(dir, "results.jsonl").appendText(o.toString() + "\n")
    }

    fun saveBitmap(name: String, bmp: Bitmap) {
        File(dir, "screens").apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Real screenshot of the whole display (system bars included), not a software draw. */
    fun screenshot(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { saveBitmap(name, it) }
    }

    /** Luminance variance of a downscaled bitmap: ~0 for blank/black/single-colour frames. */
    fun variance(bmp: Bitmap): Double {
        val s = Bitmap.createScaledBitmap(bmp, 48, 48, true)
        val px = IntArray(48 * 48).also { s.getPixels(it, 0, 48, 0, 0, 48, 48) }
        val lum = px.map { 0.2126 * ((it shr 16) and 255) + 0.7152 * ((it shr 8) and 255) + 0.0722 * (it and 255) }
        val mean = lum.average()
        return lum.sumOf { (it - mean) * (it - mean) } / lum.size
    }
}
