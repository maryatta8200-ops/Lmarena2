package com.localmed.tools.websearch

import com.localmed.core.common.Hashing
import com.localmed.tools.api.ToolSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

interface SearchProvider {
    fun search(query: String, maximumResults: Int): PubMedSearchResult
}

data class PubMedArticle(
    val pmid: String,
    val title: String,
    val journal: String,
    val publicationDate: String,
    val abstractText: String,
    val url: String,
    val contentSha256: String
)

data class PubMedSearchResult(
    val queryHash: String,
    val retrievedAt: Instant,
    val articles: List<PubMedArticle>,
    val sources: List<ToolSource>
)

/** Only calls NCBI E-utilities over HTTPS. Returned content is untrusted and citation-bearing. */
class PubMedEutilsSearchProvider(
    private val connectTimeoutMillis: Int = 7_000,
    private val readTimeoutMillis: Int = 10_000
) : SearchProvider {
    override fun search(query: String, maximumResults: Int): PubMedSearchResult {
        require(query.length in 3..500) { "Search query must be 3–500 characters." }
        require(maximumResults in 1..5) { "Search results are limited to five records." }
        val retrievedAt = Instant.now()
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val searchJson = getJson("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=pubmed&term=$encodedQuery&retmode=json&retmax=$maximumResults")
        val ids = searchJson["esearchresult"]?.jsonObject?.get("idlist")?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
            .filter { it.matches(Regex("\\d{1,12}")) }.distinct().take(maximumResults)
        if (ids.isEmpty()) return PubMedSearchResult(Hashing.sha256(query.trim().toByteArray()), retrievedAt, emptyList(), emptyList())

        val idList = ids.joinToString(",")
        val summary = getJson("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id=$idList&retmode=json")
        val abstracts = getAbstracts(ids)
        val result = ids.mapNotNull { id ->
            val record = summary["result"]?.jsonObject?.get(id)?.jsonObject ?: return@mapNotNull null
            val title = record.string("title")?.cleanText()?.take(MAX_TITLE_CHARS) ?: return@mapNotNull null
            val journal = record.string("fulljournalname")?.cleanText()?.take(MAX_JOURNAL_CHARS).orEmpty()
            val date = record.string("pubdate")?.cleanText()?.take(80).orEmpty()
            val abstract = abstracts[id].orEmpty().cleanText().take(MAX_ABSTRACT_CHARS)
            val url = "$PUBMED_BASE/$id/"
            val hash = Hashing.sha256("$id\n$title\n$journal\n$date\n$abstract".toByteArray(Charsets.UTF_8))
            PubMedArticle(id, title, journal, date, abstract, url, hash)
        }
        val sources = result.map { article ->
            ToolSource(article.url, article.title, "PubMed / U.S. National Library of Medicine", retrievedAt, article.contentSha256)
        }
        return PubMedSearchResult(Hashing.sha256(query.trim().toByteArray()), retrievedAt, result, sources)
    }

    private fun getJson(url: String): JsonObject {
        val body = get(url)
        return runCatching { JSON.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject }
            .getOrElse { throw IllegalStateException("PubMed returned an invalid JSON response.") }
    }

    private fun get(urlValue: String): ByteArray {
        val url = URL(urlValue)
        require(url.protocol == "https" && url.host == NCBI_HOST) { "Search endpoint is not on the configured allowlist." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, application/xml;q=0.9")
            setRequestProperty("User-Agent", "LocalMedResearch/0.1.0 (user-authorized PubMed research search)")
            useCaches = false
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("PubMed request failed with HTTP $status.")
            return connection.inputStream.use { input -> readBounded(input, MAX_RESPONSE_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    private fun getAbstracts(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val encodedIds = URLEncoder.encode(ids.joinToString(","), Charsets.UTF_8.name())
        val xml = get("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&id=$encodedIds&retmode=xml")
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isXIncludeAware = false
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        val handler = AbstractHandler()
        xml.inputStream().use { factory.newSAXParser().parse(it, handler) }
        return handler.abstracts
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maximum) throw IllegalStateException("PubMed response exceeded the size limit.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun String.cleanText(): String = replace(Regex("<[^>]*>"), " ")
        .replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun JsonObject.string(key: String): String? = this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    private class AbstractHandler : DefaultHandler() {
        val abstracts = linkedMapOf<String, String>()
        private var currentPmid: String? = null
        private var captureAbstract = false
        private var inArticleId = false
        private val currentAbstract = StringBuilder()
        private val currentValue = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            when (qName?.lowercase(Locale.ROOT)) {
                "pmid" -> { inArticleId = true; currentValue.setLength(0) }
                "abstracttext" -> { captureAbstract = true; currentAbstract.setLength(0) }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (captureAbstract) currentAbstract.append(ch, start, length)
            if (inArticleId) currentValue.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (qName?.lowercase(Locale.ROOT)) {
                "pmid" -> { currentPmid = currentValue.toString().trim(); inArticleId = false }
                "abstracttext" -> {
                    val value = currentAbstract.toString().trim()
                    if (currentPmid != null && value.isNotBlank()) {
                        val old = abstracts[currentPmid!!].orEmpty()
                        abstracts[currentPmid!!] = listOf(old, value).filter(String::isNotBlank).joinToString(" ")
                    }
                    captureAbstract = false
                }
            }
        }
    }

    companion object {
        private const val NCBI_HOST = "eutils.ncbi.nlm.nih.gov"
        private const val PUBMED_BASE = "https://pubmed.ncbi.nlm.nih.gov"
        private const val MAX_RESPONSE_BYTES = 1 * 1024 * 1024
        private const val MAX_ABSTRACT_CHARS = 8_000
        private const val MAX_TITLE_CHARS = 400
        private const val MAX_JOURNAL_CHARS = 250
        private const val COPY_BUFFER_BYTES = 16 * 1024
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = false }
    }
}
