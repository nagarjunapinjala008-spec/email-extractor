package com.example.emailextractor

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

object Http {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    /** GET a page as text (HTML only, max ~1.5 MB). Returns null on any failure. */
    fun get(urlStr: String, maxBytes: Int = 1_500_000): String? {
        var current = urlStr
        for (hop in 0 until 5) {
            val conn = try {
                URL(current).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return null
            }
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 12_000
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.5")
                conn.setRequestProperty("Accept-Language", "en")

                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    current = URL(URL(current), loc).toString()
                    continue
                }
                if (code >= 400) return null

                val ct = conn.contentType ?: ""
                if (ct.isNotEmpty() && !ct.contains("text", true) &&
                    !ct.contains("html", true) && !ct.contains("xml", true)
                ) return null

                val buf = ByteArrayOutputStream()
                val tmp = ByteArray(8192)
                var total = 0
                conn.inputStream.use { ins ->
                    while (true) {
                        val n = ins.read(tmp)
                        if (n < 0) break
                        buf.write(tmp, 0, n)
                        total += n
                        if (total >= maxBytes) break
                    }
                }
                val cs = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                    .find(ct)?.groupValues?.get(1)
                val charset = try {
                    if (cs != null) Charset.forName(cs) else Charsets.UTF_8
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
                return String(buf.toByteArray(), charset)
            } catch (e: Exception) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }
}

/** Turns a keyword query into a list of result page URLs (DuckDuckGo HTML endpoint). */
object Search {
    private val BLOCKED_HOSTS = listOf(
        "facebook.com", "youtube.com", "instagram.com", "twitter.com", "x.com",
        "linkedin.com", "pinterest.com", "wikipedia.org", "duckduckgo.com", "google.com"
    )
    private val RESULT_LINK = Regex("class=\"result__a\"[^>]*href=\"([^\"]+)\"")

    fun find(query: String, limit: Int): List<String> {
        val variants = listOf(query, "$query faculty directory contact", "$query email \"@\"")
        val urls = LinkedHashSet<String>()
        for (q in variants) {
            if (urls.size >= limit) break
            val html = Http.get(
                "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8")
            ) ?: continue
            RESULT_LINK.findAll(html).forEach { m ->
                val u = resolve(m.groupValues[1].replace("&amp;", "&"))
                if (u != null && BLOCKED_HOSTS.none { b -> hostOf(u).endsWith(b) }) urls.add(u)
            }
            Thread.sleep(400)
        }
        return urls.take(limit)
    }

    private fun resolve(href: String): String? {
        val full = if (href.startsWith("//")) "https:$href" else href
        val uddg = Regex("[?&]uddg=([^&]+)").find(full)?.groupValues?.get(1)
        val target = if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else full
        return if (target.startsWith("http")) target else null
    }

    fun hostOf(url: String): String = try {
        URL(url).host.lowercase().removePrefix("www.")
    } catch (e: Exception) {
        ""
    }
}
