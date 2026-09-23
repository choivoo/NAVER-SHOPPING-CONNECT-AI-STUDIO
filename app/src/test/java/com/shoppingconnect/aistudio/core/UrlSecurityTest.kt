package com.shoppingconnect.aistudio.core

import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.core.security.UrlSecurity
import org.junit.Test
import java.net.InetAddress

class UrlSecurityTest {
    private fun blocked(u: String) = UrlSecurity.check(u) is UrlSecurity.Verdict.Blocked
    private fun allowed(u: String) = UrlSecurity.check(u) is UrlSecurity.Verdict.Allowed

    @Test fun allowsPublicHttpsAndHttp() {
        assertThat(allowed("https://smartstore.naver.com/shop/products/123")).isTrue()
        assertThat(allowed("http://shopping.naver.com/x")).isTrue()
        assertThat(allowed("naver.me/abc")).isTrue() // bare domain gets https
    }

    @Test fun blocksNonHttpSchemes() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "content://x/y", "ftp://example.com/a", "intent://x", "data:text/html,hi").forEach {
            assertThat(blocked(it)).isTrue()
        }
    }

    @Test fun blocksLocalAndPrivateTargets() {
        listOf(
            "http://localhost:8080", "http://127.0.0.1", "http://10.0.0.5/admin", "http://192.168.1.1", "http://172.16.3.4",
            "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://printer.local/", "http://intranet/", "http://100.64.1.1",
        ).forEach { assertThat(blocked(it)).isTrue() }
    }

    @Test fun blocksUserInfoAndPrivilegedPorts() {
        assertThat(blocked("https://user:pass@example.com")).isTrue()
        assertThat(blocked("https://example.com:22/")).isTrue()
    }

    @Test fun privateAddressClassification() {
        assertThat(UrlSecurity.isPrivate(InetAddress.getByName("10.1.2.3"))).isTrue()
        assertThat(UrlSecurity.isPrivate(InetAddress.getByName("::ffff:192.168.0.1"))).isTrue()
        assertThat(UrlSecurity.isPrivate(InetAddress.getByName("fd00::1"))).isTrue()
        assertThat(UrlSecurity.isPrivate(InetAddress.getByName("8.8.8.8"))).isFalse()
    }
}
