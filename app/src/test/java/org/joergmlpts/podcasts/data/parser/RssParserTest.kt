package org.joergmlpts.podcasts.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {

    @Test
    fun parseRss_validXml_parsesPodcastAndEpisodes() {
        val sampleFeed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>The Tech Show</title>
                <itunes:author>Jane Doe</itunes:author>
                <description>&lt;p&gt;A podcast about tech and &amp;amp; code.&lt;/p&gt;</description>
                <itunes:image href="https://example.com/cover.jpg"/>
                <item>
                  <guid>ep-101</guid>
                  <title>Episode 101: Kotlin &amp; Compose</title>
                  <description>Discussion on Android UI development.</description>
                  <pubDate>Mon, 01 Jan 2024 12:00:00 +0000</pubDate>
                  <enclosure url="https://example.com/ep101.mp3" type="audio/mpeg"/>
                  <itunes:duration>01:15:30</itunes:duration>
                </item>
                <item>
                  <guid>ep-102</guid>
                  <title>Episode 102: Architecture Patterns</title>
                  <pubDate>Mon, 08 Jan 2024 12:00:00 +0000</pubDate>
                  <enclosure url="https://example.com/ep102.mp3" type="audio/mpeg"/>
                  <itunes:duration>45:00</itunes:duration>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feedUrl = "https://example.com/feed.xml"
        val parsed = RssParser.parseRss(feedUrl, sampleFeed)

        assertNotNull(parsed)
        assertEquals("The Tech Show", parsed.podcast.title)
        assertEquals("Jane Doe", parsed.podcast.author)
        assertEquals("A podcast about tech and & code.", parsed.podcast.description)
        assertEquals("https://example.com/cover.jpg", parsed.podcast.imageUrl)
        assertEquals(2, parsed.episodes.size)

        val ep1 = parsed.episodes[0]
        assertEquals("Episode 101: Kotlin & Compose", ep1.title)
        assertEquals("Discussion on Android UI development.", ep1.description)
        assertEquals("https://example.com/ep101.mp3", ep1.audioUrl)
        assertEquals("1:15:30", ep1.duration)
        assertEquals(4530L, ep1.durationSec)

        val ep2 = parsed.episodes[1]
        assertEquals("Episode 102: Architecture Patterns", ep2.title)
        assertEquals("45:00", ep2.duration)
        assertEquals(2700L, ep2.durationSec)
    }

    @Test
    fun parseRss_emptyFeed_returnsDefaultPodcastAndEmptyEpisodes() {
        val sampleFeed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
              </channel>
            </rss>
        """.trimIndent()

        val feedUrl = "https://example.com/empty.xml"
        val parsed = RssParser.parseRss(feedUrl, sampleFeed)

        assertEquals("Untitled Podcast", parsed.podcast.title)
        assertEquals("Unknown Author", parsed.podcast.author)
        assertEquals("", parsed.podcast.description)
        assertTrue(parsed.episodes.isEmpty())
    }

    @Test
    fun sha256_producesConsistentHash() {
        val input = "https://example.com/feed.xml"
        val hash1 = RssParser.sha256(input)
        val hash2 = RssParser.sha256(input)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 hex string length
    }

    @Test
    fun parseRss_differentDurationFormats_parsedCorrectly() {
        val xmlWithDurations = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Duration Test</title>
                <item>
                  <title>Ep Secs</title>
                  <enclosure url="https://example.com/1.mp3"/>
                  <itunes:duration>300</itunes:duration>
                </item>
                <item>
                  <title>Ep MinSec</title>
                  <enclosure url="https://example.com/2.mp3"/>
                  <itunes:duration>12:34</itunes:duration>
                </item>
                <item>
                  <title>Ep HourMinSec</title>
                  <enclosure url="https://example.com/3.mp3"/>
                  <itunes:duration>02:05:10</itunes:duration>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = RssParser.parseRss("https://example.com/feed.xml", xmlWithDurations)
        assertEquals(3, parsed.episodes.size)

        assertEquals(300L, parsed.episodes[0].durationSec)
        assertEquals("5:00", parsed.episodes[0].duration)

        assertEquals(754L, parsed.episodes[1].durationSec)
        assertEquals("12:34", parsed.episodes[1].duration)

        assertEquals(7510L, parsed.episodes[2].durationSec)
        assertEquals("2:05:10", parsed.episodes[2].duration)
    }

    @Test
    fun parseRss_xmlWithBomAndLeadingWhitespace_parsesSuccessfully() {
        val xmlWithBom = "\uFEFF  \n  <?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>BOM Test</title></channel></rss>"
        val parsed = RssParser.parseRss("https://example.com/bom.xml", xmlWithBom)

        assertEquals("BOM Test", parsed.podcast.title)
    }
}
