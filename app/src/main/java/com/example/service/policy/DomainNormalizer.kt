package com.example.service.policy

/**
 * Pure Kotlin utility for validating, normalizing, and matching domain names
 * across DNS packets, HTTP Host headers, and TLS SNI extensions.
 */
object DomainNormalizer {

    private val LABEL_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    /**
     * Normalizes a raw user input, URL, or host string into a canonical lowercase domain.
     * Strips schemes (`http://`, `https://`), path segments, query parameters, port numbers,
     * wildcard prefixes (`*.`), `www.` prefixes, and surrounding brackets/dots.
     *
     * Returns null if the domain is structurally invalid according to RFC standards.
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var cleaned = raw.trim().lowercase()

        // Strip URI schemes if present
        cleaned = cleaned
            .removePrefix("https://")
            .removePrefix("http://")

        // Strip path, query, and fragment
        cleaned = cleaned
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')

        // Handle bracketed IPv6 literals before stripping :port
        cleaned = if (cleaned.startsWith("[")) {
            val endBracket = cleaned.indexOf(']')
            if (endBracket > 0) {
                cleaned.substring(1, endBracket)
            } else {
                cleaned.removeSurrounding("[", "]")
            }
        } else {
            val firstColon = cleaned.indexOf(':')
            if (firstColon >= 0 && firstColon == cleaned.lastIndexOf(':')) {
                cleaned.substring(0, firstColon)
            } else {
                cleaned
            }
        }

        cleaned = cleaned
            .removePrefix("*.")
            .removePrefix("www.")
            .trim('.')

        if (cleaned.length !in 3..253 || !cleaned.contains('.')) return null

        val labels = cleaned.split('.')
        if (labels.size < 2 || labels.any { !LABEL_REGEX.matches(it) }) return null

        return cleaned
    }

    /**
     * Canonicalizes queried hostnames across DNS packets, HTTP Host/CONNECT headers,
     * TLS SNI extensions, and DoH canaries.
     * Trims whitespace, strips optional URI scheme/path/query/fragment, safely removes
     * IPv6 brackets and single trailing `:port`, removes leading wildcards `*.` and
     * surrounding dots, and lowercases while preserving valid `_`-prefixed DNS service
     * labels (e.g., `_dns.resolver.arpa`).
     */
    fun normalizeHost(rawHost: String): String {
        var cleaned = rawHost.trim().lowercase()
        if (cleaned.isEmpty()) return ""

        val schemeIdx = cleaned.indexOf("://")
        if (schemeIdx in 1..15) {
            cleaned = cleaned.substring(schemeIdx + 3)
        }

        cleaned = cleaned
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')

        cleaned = if (cleaned.startsWith("[")) {
            val endBracket = cleaned.indexOf(']')
            if (endBracket > 0) {
                cleaned.substring(1, endBracket)
            } else {
                cleaned.removeSurrounding("[", "]")
            }
        } else {
            val firstColon = cleaned.indexOf(':')
            if (firstColon >= 0 && firstColon == cleaned.lastIndexOf(':')) {
                cleaned.substring(0, firstColon)
            } else {
                cleaned
            }
        }

        return cleaned
            .removePrefix("*.")
            .trim('.')
    }

    /**
     * Evaluates whether [queriedDomain] matches [ruleDomain].
     * Supports exact match (`example.com` == `example.com`) or subdomain match
     * (`sub.example.com` ends with `.example.com`).
     */
    fun matchesRule(queriedDomain: String, ruleDomain: String): Boolean {
        val q = normalizeHost(queriedDomain)
        val r = normalizeHost(ruleDomain)
        if (q.isEmpty() || r.isEmpty()) return false
        return q == r || q.endsWith(".$r")
    }

    /**
     * Checks if [queriedDomain] matches any domain in [ruleDomains].
     */
    fun matchesAny(queriedDomain: String, ruleDomains: Iterable<String>): Boolean {
        val q = normalizeHost(queriedDomain)
        if (q.isEmpty()) return false
        for (rule in ruleDomains) {
            val r = normalizeHost(rule)
            if (r.isNotEmpty() && (q == r || q.endsWith(".$r"))) {
                return true
            }
        }
        return false
    }

    /**
     * Produces all hierarchical parent domains for a given domain, starting from the domain itself.
     * e.g., "a.b.example.com" -> ["a.b.example.com", "b.example.com", "example.com"]
     */
    fun parentDomainSequence(domain: String): Sequence<String> = sequence {
        val clean = normalizeHost(domain)
        if (clean.isEmpty()) return@sequence
        var current = clean
        while (true) {
            yield(current)
            val dot = current.indexOf('.')
            if (dot < 0 || dot == current.lastIndex) break
            current = current.substring(dot + 1)
        }
    }
}
