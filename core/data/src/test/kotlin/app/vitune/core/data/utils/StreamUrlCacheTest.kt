package app.vitune.core.data.utils

import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamUrlCacheTest {

    private val now = 1_000_000L

    private fun candidate(url: String, contentLength: Long? = null) =
        StreamUrlCache.Candidate(
            uri = url,
            contentLength = contentLength
        )

    @Test
    fun `put stores the first candidate as the current one`() {
        val cache = StreamUrlCache()
        val first = candidate("https://example.com/1")
        val second = candidate("https://example.com/2", contentLength = 100L)

        cache.put(
            mediaId = "id",
            candidates = listOf(first, second),
            expiresInSeconds = 600,
            now = now
        )

        assertEquals(first, cache.current("id", now))
        assertTrue(cache.hasRemaining("id", now))
    }

    @Test
    fun `put with empty candidates does not replace the entry`() {
        val cache = StreamUrlCache()
        val first = candidate("https://example.com/1")

        cache.put("id", listOf(first), 600, now)
        cache.put("id", emptyList(), 600, now)

        assertEquals(first, cache.current("id", now))
    }

    @Test
    fun `markFailed advances to the next candidate`() {
        val cache = StreamUrlCache()
        val first = candidate("https://example.com/1")
        val second = candidate("https://example.com/2")

        cache.put("id", listOf(first, second), 600, now)
        cache.markFailed("id", first)

        assertEquals(second, cache.current("id", now))
        assertTrue(cache.hasRemaining("id", now))
    }

    @Test
    fun `current returns null when all candidates failed`() {
        val cache = StreamUrlCache()
        val only = candidate("https://example.com/1")

        cache.put("id", listOf(only), 600, now)
        cache.markFailed("id", only)

        assertNull(cache.current("id", now))
        assertFalse(cache.hasRemaining("id", now))
    }

    @Test
    fun `expired entries are not served`() {
        val cache = StreamUrlCache()

        cache.put("id", listOf(candidate("https://example.com/1")), 100, now)

        assertNull(cache.current("id", now + 100_001))
        assertFalse(cache.hasRemaining("id", now + 100_001))
    }

    @Test
    fun `real TTL from expiresInSeconds is honored`() {
        val cache = StreamUrlCache()

        cache.put("id", listOf(candidate("https://example.com/1")), 100, now)

        assertEquals(
            candidate("https://example.com/1"),
            cache.current("id", now + 99_999)
        )
        assertNull(cache.current("id", now + 100_000))
    }

    @Test
    fun `default TTL is one hour when the client does not report expiry`() {
        val cache = StreamUrlCache()

        cache.put("id", listOf(candidate("https://example.com/1")), null, now)

        assertEquals(
            candidate("https://example.com/1"),
            cache.current("id", now + 59 * 60_000L)
        )
        assertNull(cache.current("id", now + StreamUrlCache.DEFAULT_TTL_MILLIS + 1))
    }

    @Test
    fun `a new put replaces the entry and resets failures`() {
        val cache = StreamUrlCache()
        val stale = candidate("https://example.com/1")
        val fresh = candidate("https://example.com/2")

        cache.put("id", listOf(stale), 600, now)
        cache.markFailed("id", stale)
        cache.put("id", listOf(fresh), 600, now)

        assertEquals(fresh, cache.current("id", now))
    }

    @Test
    fun `remove clears one media id while clear removes everything`() {
        val cache = StreamUrlCache()

        cache.put("a", listOf(candidate("https://example.com/a")), 600, now)
        cache.put("b", listOf(candidate("https://example.com/b")), 600, now)
        cache.remove("a")

        assertNull(cache.current("a", now))
        assertEquals(candidate("https://example.com/b"), cache.current("b", now))

        cache.clear()
        assertNull(cache.current("b", now))
    }

    @Test
    fun `entries are independent per media id`() {
        val cache = StreamUrlCache()
        val a = candidate("https://example.com/a")
        val b = candidate("https://example.com/b")

        cache.put("a", listOf(a), 600, now)
        cache.put("b", listOf(b), 600, now)
        cache.markFailed("a", a)

        assertNull(cache.current("a", now))
        assertEquals(b, cache.current("b", now))
    }

    @Test
    fun `concurrent access does not lose candidates`() {
        val cache = StreamUrlCache()

        cache.put(
            "id",
            (0 until 10).map { candidate("https://example.com/$it") },
            600,
            now
        )

        val threads = (0 until 8).map {
            thread {
                while (true) {
                    val current = cache.current("id", now) ?: break
                    cache.markFailed("id", current)
                }
            }
        }

        threads.forEach { it.join() }

        assertNull(cache.current("id", now))
        assertFalse(cache.hasRemaining("id", now))
    }
}
