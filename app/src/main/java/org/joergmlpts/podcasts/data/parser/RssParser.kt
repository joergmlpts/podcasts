package org.joergmlpts.podcasts.data.parser

import org.joergmlpts.podcasts.data.local.EpisodeEntity
import org.joergmlpts.podcasts.data.local.PodcastEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ParsedFeed(
    val podcast: PodcastEntity,
    val episodes: List<EpisodeEntity>,
)

object RssParser {

    private val rfc822Formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
    ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }

    fun parseRss(feedUrl: String, xmlContent: String): ParsedFeed {
        var sanitizedXml = xmlContent.trim().removePrefix("\uFEFF")
        val xmlDeclIndex = sanitizedXml.indexOf("<?xml")
        if (xmlDeclIndex > 0) {
            sanitizedXml = sanitizedXml.substring(xmlDeclIndex)
        } else if (xmlDeclIndex < 0) {
            val rssIndex = sanitizedXml.indexOf("<rss")
            if (rssIndex > 0) {
                sanitizedXml = sanitizedXml.substring(rssIndex)
            }
        }

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(sanitizedXml))

        val podcastId = sha256(feedUrl)
        var podcastTitle = ""
        var podcastAuthor = ""
        var podcastDescription = ""
        var podcastImageUrl = ""

        val episodes = mutableListOf<EpisodeEntity>()

        var inChannel = false
        var inItem = false
        var inImage = false

        // Temp episode state
        var epGuid = ""
        var epTitle = ""
        var epDesc = ""
        var epPubDateStr = ""
        var epAudioUrl = ""
        var epDuration = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase() ?: ""
            val prefix = parser.prefix?.lowercase() ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tagName == "channel" -> inChannel = true
                        tagName == "item" -> {
                            inItem = true
                            epGuid = ""
                            epTitle = ""
                            epDesc = ""
                            epPubDateStr = ""
                            epAudioUrl = ""
                            epDuration = ""
                        }
                        (tagName == "image" && !inItem) -> {
                            inImage = true
                            val href = parser.getAttributeSafe("href")
                            if (!href.isNullOrBlank()) {
                                podcastImageUrl = href.trim()
                            }
                        }

                        // Podcast metadata
                        inChannel && !inItem -> {
                            when {
                                tagName == "title" && podcastTitle.isEmpty() -> {
                                    podcastTitle = parser.nextTextSafe().trim()
                                }
                                tagName == "author" && podcastAuthor.isEmpty() -> {
                                    podcastAuthor = parser.nextTextSafe().trim()
                                }
                                (tagName == "description" || (prefix == "itunes" && tagName == "summary")) && podcastDescription.isEmpty() -> {
                                    podcastDescription = parser.nextTextSafe().trim()
                                }
                                inImage && tagName == "url" && podcastImageUrl.isEmpty() -> {
                                    podcastImageUrl = parser.nextTextSafe().trim()
                                }
                            }
                        }

                        // Episode metadata
                        inItem -> {
                            when {
                                tagName == "guid" -> {
                                    epGuid = parser.nextTextSafe().trim()
                                }
                                tagName == "title" -> {
                                    epTitle = parser.nextTextSafe().trim()
                                }
                                tagName == "description" || (prefix == "content" && tagName == "encoded") -> {
                                    val text = parser.nextTextSafe().trim()
                                    if (epDesc.isEmpty() || text.length > epDesc.length) {
                                        epDesc = text
                                    }
                                }
                                tagName == "pubdate" -> {
                                    epPubDateStr = parser.nextTextSafe().trim()
                                }
                                tagName == "enclosure" -> {
                                    val url = parser.getAttributeSafe("url")
                                    if (!url.isNullOrBlank()) {
                                        epAudioUrl = url.trim()
                                    }
                                }
                                prefix == "itunes" && tagName == "duration" -> {
                                    epDuration = parser.nextTextSafe().trim()
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when {
                        tagName == "image" -> inImage = false
                        tagName == "item" -> {
                            inItem = false
                            if (epAudioUrl.isNotBlank() || epTitle.isNotBlank()) {
                                val pubDateMillis = parseDateToMillis(epPubDateStr)
                                val durationSec = parseDurationToSeconds(epDuration)
                                val finalId = if (epGuid.isNotBlank()) sha256(epGuid) else sha256(epAudioUrl + epTitle)

                                val formattedDuration = if (epDuration.isNotBlank()) {
                                    formatDurationPretty(durationSec)
                                } else {
                                    "Audio"
                                }

                                episodes.add(
                                    EpisodeEntity(
                                        id = finalId,
                                        podcastId = podcastId,
                                        title = epTitle.ifBlank { "Untitled Episode" },
                                        description = cleanHtml(epDesc),
                                        pubDate = pubDateMillis,
                                        audioUrl = epAudioUrl,
                                        duration = formattedDuration,
                                        durationSec = durationSec,
                                    )
                                )
                            }
                        }
                        tagName == "channel" -> inChannel = false
                    }
                }
            }
            eventType = parser.next()
        }

        val podcast = PodcastEntity(
            id = podcastId,
            title = podcastTitle.ifBlank { "Untitled Podcast" },
            author = podcastAuthor.ifBlank { "Unknown Author" },
            description = cleanHtml(podcastDescription),
            feedUrl = feedUrl,
            imageUrl = podcastImageUrl,
            hasNewEpisodes = false,
            lastRefreshedAt = System.currentTimeMillis(),
            episodeCount = episodes.size
        )

        return ParsedFeed(podcast, episodes)
    }

    private fun XmlPullParser.nextTextSafe(): String {
        return try {
            nextText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseDateToMillis(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        for (format in rfc822Formats) {
            try {
                val parsed = format.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    private fun parseDurationToSeconds(duration: String): Long {
        if (duration.isBlank()) return 0L
        if (duration.toLongOrNull() != null) {
            return duration.toLong()
        }
        val parts = duration.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private fun formatDurationPretty(seconds: Long): String {
        if (seconds <= 0) return "Audio"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, secs)
        }
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun XmlPullParser.getAttributeSafe(attributeName: String): String? {
        val count = try { attributeCount } catch (_: Exception) { -1 }
        if (count > 0) {
            for (i in 0 until count) {
                val name = try { getAttributeName(i) } catch (_: Exception) { null }
                if (name != null && name.equals(attributeName, ignoreCase = true)) {
                    val value = try { getAttributeValue(i) } catch (_: Exception) { null }
                    if (!value.isNullOrBlank()) return value
                }
            }
        }
        return getAttributeValue(null, attributeName) ?: getAttributeValue("", attributeName)
    }
}
