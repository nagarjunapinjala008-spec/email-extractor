package com.example.emailextractor

import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs one extraction: keyword -> search -> pages, or URL -> page (+ optional
 * same-site faculty/contact pages). Call [run] from a background thread.
 */
class ExtractionJob(
    private val input: String,
    private val maxPages: Int,
    private val followLinks: Boolean,
    private val academicOnly: Boolean,
    private val onEmail: (email: String, sourceUrl: String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onDone: (pagesScanned: Int) -> Unit
) {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    private val visited = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val scanned = AtomicInteger(0)
    private val pending = AtomicInteger(0)
    private val finished = CountDownLatch(1)
    private val pool = Executors.newFixedThreadPool(4)

    private val followHints = Regex(
        "faculty|staff|people|contact|directory|department|team|professor|member|about|teaching",
        RegexOption.IGNORE_CASE
    )
    private val hrefRegex = Regex("href=[\"']([^\"'#]+)[\"']", RegexOption.IGNORE_CASE)

    fun run() {
        try {
            val seeds: List<String> = if (looksLikeUrl(input)) {
                listOf(normalizeUrl(input))
            } else {
                onStatus("Searching the web for \"$input\"…")
                Search.find(input, maxPages)
            }

            if (seeds.isEmpty()) {
                onStatus("No pages to scan. The search engine returned nothing - try a different keyword or paste a URL.")
                return
            }

            seeds.forEach { submit(it, 0) }
            if (pending.get() == 0) finished.countDown()
            finished.await(10, TimeUnit.MINUTES)
        } finally {
            pool.shutdownNow()
            onDone(scanned.get())
        }
    }

    private fun submit(url: String, depth: Int) {
        if (cancelled || !visited.add(url)) return
        pending.incrementAndGet()
        pool.execute {
            try {
                process(url, depth)
            } finally {
                if (pending.decrementAndGet() == 0) finished.countDown()
            }
        }
    }

    private fun process(url: String, depth: Int) {
        if (cancelled) return
        if (scanned.incrementAndGet() > maxPages) return
        Thread.sleep(250) // be polite to servers

        val html = Http.get(url) ?: return
        onStatus("Scanned ${scanned.get()} page(s) - latest: ${Search.hostOf(url)}")

        EmailExtractor.extract(html).forEach { email ->
            if (!academicOnly || EmailExtractor.isAcademic(email)) onEmail(email, url)
        }

        if (followLinks && depth == 0) {
            val host = Search.hostOf(url)
            var count = 0
            for (m in hrefRegex.findAll(html)) {
                if (count >= 6 || cancelled) break
                val link = try {
                    URL(URL(url), m.groupValues[1]).toString()
                } catch (e: Exception) {
                    continue
                }
                if (!link.startsWith("http")) continue
                if (Search.hostOf(link) != host) continue
                if (link.substringAfterLast('.').lowercase() in setOf("pdf", "jpg", "png", "zip", "doc", "docx")) continue
                if (!followHints.containsMatchIn(link)) continue
                submit(link, 1)
                count++
            }
        }
    }

    private fun looksLikeUrl(s: String): Boolean {
        val t = s.trim()
        if (t.contains(' ')) return false
        return t.startsWith("http://") || t.startsWith("https://") ||
            Regex("^[\\w.-]+\\.[a-zA-Z]{2,}(/.*)?$").matches(t)
    }

    private fun normalizeUrl(s: String): String {
        val t = s.trim()
        return if (t.startsWith("http")) t else "https://$t"
    }
}
