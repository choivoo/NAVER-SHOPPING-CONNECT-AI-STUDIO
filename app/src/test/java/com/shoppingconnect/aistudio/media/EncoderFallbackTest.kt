package com.shoppingconnect.aistudio.media

import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.media.video.CodecSupport
import com.shoppingconnect.aistudio.media.video.EncoderChoice
import org.junit.Assert.assertThrows
import org.junit.Test

/** Regression: a hardware encoder that fails in configure()/start() must fall back to the next (software) one. */
class EncoderFallbackTest {
    private val hw = EncoderChoice("video/avc", 1080, 1920, "c2.vendor.avc.encoder", hardware = true)
    private val sw = EncoderChoice("video/avc", 1080, 1920, "c2.android.avc.encoder", hardware = false)

    @Test fun usesFirstEncoderThatOpens() {
        val (choice, opened) = CodecSupport.openFirst(listOf(hw, sw)) { it.codecName }
        assertThat(choice).isEqualTo(hw)
        assertThat(opened).isEqualTo("c2.vendor.avc.encoder")
    }

    @Test fun failingHardwareEncoderFallsBackToSoftware() {
        val fallbacks = mutableListOf<String>()
        val (choice, _) = CodecSupport.openFirst(listOf(hw, sw), onFallback = { c, _ -> fallbacks += c.codecName }) {
            if (it.hardware) throw IllegalStateException("configure failed: 0x80001001") else "ok"
        }
        assertThat(choice).isEqualTo(sw)
        assertThat(fallbacks).containsExactly("c2.vendor.avc.encoder")
    }

    @Test fun reportsRenderFailureWithCauseWhenNothingOpens() {
        val cause = IllegalStateException("no codec instances left")
        val e = assertThrows(AppException::class.java) { CodecSupport.openFirst(listOf(hw, sw)) { throw cause } }
        assertThat(e.kind).isEqualTo(ErrorKind.RenderFailure)
        assertThat(e.cause).isSameInstanceAs(cause)
    }

    @Test fun noCandidatesIsARenderFailure() {
        val e = assertThrows(AppException::class.java) { CodecSupport.openFirst(emptyList<EncoderChoice>()) { it } }
        assertThat(e.kind).isEqualTo(ErrorKind.RenderFailure)
    }
}
