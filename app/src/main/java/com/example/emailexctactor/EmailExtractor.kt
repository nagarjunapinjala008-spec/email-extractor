package com.example.emailextractor

/** Pulls e-mail addresses out of raw HTML / text, including common obfuscations. */
object EmailExtractor {

    private val EMAIL = Regex(
        "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,24}"
    )

    // Cloudflare "email protection": <a data-cfemail="hex">
    private val CF = Regex("data-cfemail=\"([0-9a-fA-F]+)\"")

    // name [at] college [dot] edu   /  name (at) college (dot) edu   /  {at} {dot}
    private val AT = Regex("\\s*[\\[({]\\s*(?:at|AT)\\s*[\\])}]\\s*")
    private val DOT = Regex("\\s*[\\[({]\\s*(?:dot|DOT)\\s*[\\])}]\\s*")

    private val BAD_TLDS = setOf(
        "png", "jpg", "jpeg", "gif", "svg", "webp", "css", "js", "ico",
        "woff", "woff2", "ttf", "eot", "mp4", "pdf", "map"
    )
    private val BAD_DOMAINS = setOf(
        "example.com", "domain.com", "email.com", "yourdomain.com",
        "sentry.io", "wixpress.com", "sentry-next.wixpress.com"
    )

    fun extract(raw: String): Set<String> {
        val out = LinkedHashSet<String>()

        CF.findAll(raw).forEach { m -> decodeCloudflare(m.groupValues[1])?.let { out.add(it) } }

        var text = raw
            .replace("&#64;", "@")
            .replace("&#x40;", "@", ignoreCase = true)
            .replace("&commat;", "@", ignoreCase = true)
            .replace("%40", "@")
            .replace("&nbsp;", " ", ignoreCase = true)
        text = AT.replace(text, "@")
        text = DOT.replace(text, ".")

        EMAIL.findAll(text).forEach { m ->
            clean(m.value)?.let { out.add(it) }
        }
        return out
    }

    private fun clean(candidate: String): String? {
        val e = candidate.trim().trim('.', '-', '_').lowercase()
        val at = e.indexOf('@')
        if (at <= 0) return null
        val local = e.substring(0, at)
        val domain = e.substring(at + 1)
        if (local.length > 64 || local.startsWith(".") || local.endsWith(".")) return null
        if (".." in e) return null
        val tld = domain.substringAfterLast('.')
        if (tld in BAD_TLDS) return null
        if (domain in BAD_DOMAINS) return null
        return e
    }

    private fun decodeCloudflare(hex: String): String? = try {
        val key = hex.substring(0, 2).toInt(16)
        val sb = StringBuilder()
        var i = 2
        while (i + 1 < hex.length) {
            sb.append((hex.substring(i, i + 2).toInt(16) xor key).toChar())
            i += 2
        }
        clean(sb.toString())
    } catch (e: Exception) {
        null
    }

    /** True for .edu, .ac.in, .edu.in, .ac.uk, ... */
    fun isAcademic(email: String): Boolean {
        val domain = email.substringAfter('@')
        return Regex(".*\\.(edu|ac)(\\.[a-z]{2})?$").matches(domain)
    }
}
