package app.vitune.core.data.utils

class StreamUrlCache {
    private class Entry(
        val candidates: List<Candidate>,
        val expiresAtMillis: Long
    ) {
        private val failed = mutableSetOf<Candidate>()

        fun current(now: Long): Candidate? =
            if (now >= expiresAtMillis) null else candidates.firstOrNull { it !in failed }

        fun hasRemaining(now: Long): Boolean =
            now < expiresAtMillis && failed.size < candidates.size

        fun markFailed(candidate: Candidate) {
            failed += candidate
        }
    }

    data class Candidate(
        val uri: String,
        val contentLength: Long?
    )

    private val entries = HashMap<String, Entry>()
    private val lock = Any()

    companion object {
        const val DEFAULT_TTL_MILLIS = 60 * 60 * 1000L
    }

    fun put(
        mediaId: String,
        candidates: List<Candidate>,
        expiresInSeconds: Long?,
        now: Long
    ) {
        if (candidates.isEmpty()) return

        val ttl = expiresInSeconds?.takeIf { it > 0 }?.times(1000) ?: DEFAULT_TTL_MILLIS

        synchronized(lock) {
            entries[mediaId] = Entry(candidates, now + ttl)
        }
    }

    fun current(mediaId: String, now: Long): Candidate? =
        synchronized(lock) {
            entries[mediaId]?.current(now)
        }

    fun hasRemaining(mediaId: String, now: Long): Boolean =
        synchronized(lock) {
            entries[mediaId]?.hasRemaining(now) ?: false
        }

    fun markFailed(mediaId: String, candidate: Candidate) {
        synchronized(lock) {
            entries[mediaId]?.markFailed(candidate)
        }
    }

    fun remove(mediaId: String) {
        synchronized(lock) {
            entries.remove(mediaId)
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }
}
