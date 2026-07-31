package org.wikitide.wikiportal.util.offline

/**
 * A URL sitting inside an HTML attribute is written with & as &amp;,
 * completely normal, valid HTML: a real browser decodes that back to
 * & the moment it reads the attribute. Regex-based text scanning
 * doesn't do that automatically; it just grabs whatever literal text
 * sits between the quotes. Handed straight to an HTTP client, a URL
 * with &amp; still in it isn't the same address at all: & is what
 * actually separates query parameters, and "&amp;" starts with a real
 * & of its own, so a URL left undecoded doesn't lose its parameters so
 * much as rename them. "&amp;modules=..." parses as a parameter
 * literally called "amp;modules", not "modules" at all, same for only
 * and skin, on every single query string like that. The request still
 * goes out and still gets a response, just never anything close to
 * what was actually being asked for, which is what made this so easy
 * to miss: nothing ever throws or fails loudly.
 *
 * Nearly every load.php URL a real page has more than one query
 * parameter, modules, only, and skin at minimum, so this single
 * decoding step is what was actually behind CSS and scripts not
 * applying at all rather than partially. Plain image URLs usually
 * have no query string to begin with, which is why those kept working
 * the whole time this was broken.
 */
internal fun String.decodeHtmlEntities(): String =
    replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
