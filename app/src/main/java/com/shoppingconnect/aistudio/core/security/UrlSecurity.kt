package com.shoppingconnect.aistudio.core.security

import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * SSRF guard: only public http/https destinations are reachable from user-provided URLs.
 * Checked twice — syntactically on the URL and again on every DNS resolution
 * (so a public hostname that resolves to a private address is still refused).
 */
object UrlSecurity {
    private val blockedHostSuffixes = listOf("localhost", ".local", ".internal", ".localdomain", ".home.arpa", ".lan")

    sealed interface Verdict {
        data class Allowed(val url: HttpUrl) : Verdict
        data class Blocked(val reason: String) : Verdict
    }

    fun check(raw: String): Verdict {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Verdict.Blocked("주소가 비어 있습니다.")
        val scheme = trimmed.substringBefore("://", "").lowercase()
        if (scheme.isNotEmpty() && scheme != "http" && scheme != "https") {
            return Verdict.Blocked("허용되지 않는 스킴입니다: $scheme")
        }
        if (!trimmed.contains("://") && Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed)) {
            return Verdict.Blocked("허용되지 않는 스킴입니다.")
        }
        val withScheme = if (scheme.isEmpty()) "https://$trimmed" else trimmed
        val url = withScheme.toHttpUrlOrNull() ?: return Verdict.Blocked("올바른 URL 형식이 아닙니다.")
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return Verdict.Blocked("사용자 정보가 포함된 URL은 허용되지 않습니다.")
        val host = url.host.lowercase()
        if (blockedHostSuffixes.any { host == it.trimStart('.') || host.endsWith(it) }) {
            return Verdict.Blocked("내부 네트워크 주소는 허용되지 않습니다.")
        }
        if (!host.contains('.') && !host.contains(':')) return Verdict.Blocked("공개 도메인이 아닙니다.")
        literalIp(host)?.let { if (isPrivate(it)) return Verdict.Blocked("사설/로컬 IP 주소는 허용되지 않습니다.") }
        if (url.port !in setOf(80, 443) && url.port < 1024) return Verdict.Blocked("허용되지 않는 포트입니다.")
        return Verdict.Allowed(url)
    }

    fun requireAllowed(raw: String): HttpUrl = when (val v = check(raw)) {
        is Verdict.Allowed -> v.url
        is Verdict.Blocked -> throw AppException(ErrorKind.InvalidUrl, v.reason)
    }

    private fun literalIp(host: String): InetAddress? {
        val h = host.trim('[', ']')
        val looksV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(h)
        val looksV6 = h.contains(':')
        if (!looksV4 && !looksV6) return null
        return runCatching { InetAddress.getByName(h) }.getOrNull()
    }

    fun isPrivate(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress || addr.isMulticastAddress
        ) return true
        val b = addr.address
        if (addr is Inet4Address) {
            val b0 = b[0].toInt() and 0xff
            val b1 = b[1].toInt() and 0xff
            if (b0 == 0 || b0 == 10 || b0 == 127) return true
            if (b0 == 100 && b1 in 64..127) return true // CGNAT
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 172 && b1 in 16..31) return true
            if (b0 == 192 && b1 == 168) return true
            if (b0 == 192 && b1 == 0 && (b[2].toInt() and 0xff) == 0) return true
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true
            if (b0 >= 224) return true
        }
        if (addr is Inet6Address) {
            val b0 = b[0].toInt() and 0xff
            if (b0 and 0xfe == 0xfc) return true // unique local fc00::/7
            if (addr.isIPv4CompatibleAddress) return true
            // IPv4-mapped ::ffff:a.b.c.d
            if (b.take(10).all { it.toInt() == 0 } && (b[10].toInt() and 0xff) == 0xff && (b[11].toInt() and 0xff) == 0xff) {
                return isPrivate(InetAddress.getByAddress(b.copyOfRange(12, 16)))
            }
        }
        return false
    }

    /** DNS that refuses to hand OkHttp any private/loopback address. */
    val publicOnlyDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val public = all.filterNot { isPrivate(it) }
            if (public.isEmpty()) throw java.net.UnknownHostException("blocked non-public address for $hostname")
            return public
        }
    }
}
