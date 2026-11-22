package app.vitune.desktop.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves playable audio stream URLs with yt-dlp, the same tool the Android app
 * runs internally. The Windows build of yt-dlp is downloaded on first launch and
 * kept up to date, because YouTube changes often and old versions stop working.
 */
class YtDlp(private val dataDir: Path) {
    private val exe: Path = dataDir.resolve("yt-dlp.exe")
    private val urlCache = ConcurrentHashMap<String, CachedUrl>()
    private val ready = CompletableDeferred<Unit>()

    private data class CachedUrl(val url: String, val createdAt: Long)

    /** Downloads (if needed) and updates yt-dlp. Call once at startup. */
    suspend fun prepare(onStatus: (String) -> Unit) {
        try {
            withContext(Dispatchers.IO) {
                if (!Files.exists(exe)) {
                    onStatus("Downloading yt-dlp (first launch only)…")
                    download()
                } else {
                    onStatus("Checking for yt-dlp updates…")
                    // Failing to update isn't fatal: the installed version may still work.
                    runCatching { exec(listOf("-U"), timeoutSeconds = 90) }
                }
            }
            ready.complete(Unit)
        } catch (e: Exception) {
            ready.completeExceptionally(e)
            throw e
        }
    }

    /** True if a fresh URL for this song is already cached (it will start instantly). */
    fun isCached(videoId: String): Boolean =
        urlCache[videoId]?.let { System.currentTimeMillis() - it.createdAt < URL_CACHE_MS } == true

    /** Forgets a URL that turned out not to play, so the next request fetches a fresh one. */
    fun invalidate(videoId: String) {
        urlCache.remove(videoId)
    }

    /**
     * Resolves in the background so the song starts instantly when clicked.
     * Limited to a few at once so prefetching never slows down the song you actually picked.
     */
    fun prefetch(videoId: String) {
        if (isCached(videoId) || inFlight.containsKey(videoId)) return
        if (inFlight.size >= MAX_PARALLEL_PREFETCH) return
        backgroundScope.launch { runCatching { streamUrl(videoId) } }
    }

    /**
     * Returns a playable URL. Concurrent requests for the same song share one yt-dlp run,
     * so clicking a song that is already being prefetched doesn't start a second, slower lookup.
     */
    suspend fun streamUrl(videoId: String): String {
        ready.await()

        urlCache[videoId]?.let { cached ->
            if (System.currentTimeMillis() - cached.createdAt < URL_CACHE_MS) return cached.url
        }

        val job = inFlight.computeIfAbsent(videoId) { id ->
            backgroundScope.async {
                try {
                    resolve(id)
                } finally {
                    inFlight.remove(id)
                }
            }
        }
        return job.await()
    }

    private val inFlight = ConcurrentHashMap<String, Deferred<String>>()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun resolve(videoId: String): String {
        val args = buildList {
            // Fall back to any format with audio if YouTube offers no audio-only stream
            add("-f"); add("bestaudio/best")
            add("-g")
            add("--no-playlist")
            add("--no-warnings")
            add("--socket-timeout"); add("20")
            addAll(jsRuntimeArgs)
            add("https://music.youtube.com/watch?v=$videoId")
        }

        val output = runInterruptible(Dispatchers.IO) { exec(args, timeoutSeconds = 90) }
        val url = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { it.startsWith("http") }
            ?: error("yt-dlp didn't return a valid stream URL for video: $videoId")

        if (url.isBlank() || !url.startsWith("http")) {
            error("Invalid stream URL returned: $url")
        }

        urlCache[videoId] = CachedUrl(url, System.currentTimeMillis())
        return url
    }

    private fun download() {
        Files.createDirectories(dataDir)
        val partial = dataDir.resolve("yt-dlp.exe.part")
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
        val request = HttpRequest.newBuilder(URI.create(DOWNLOAD_URL)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial))
        if (response.statusCode() != 200) {
            Files.deleteIfExists(partial)
            error("Couldn't download yt-dlp (HTTP ${response.statusCode()})")
        }
        Files.move(partial, exe, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun exec(args: List<String>, timeoutSeconds: Long): String {
        val process = ProcessBuilder(listOf(exe.toString()) + args).start()
        // Read both streams concurrently so a full pipe can't block the process.
        val stdout = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        val stderr = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }

        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("yt-dlp timed out")
            }
        } catch (e: InterruptedException) {
            // The coroutine was cancelled (e.g. the user skipped the song).
            process.destroyForcibly()
            throw e
        }

        if (process.exitValue() != 0) {
            val message = stderr.get().lineSequence().lastOrNull { it.isNotBlank() }
            error(message ?: "yt-dlp failed with exit code ${process.exitValue()}")
        }
        return stdout.get()
    }

    /**
     * yt-dlp needs a JavaScript runtime for YouTube. It uses Deno by default;
     * if only Node.js or Bun is installed, tell yt-dlp to use that instead.
     */
    private val jsRuntimeArgs: List<String> by lazy {
        fun onPath(name: String) = System.getenv("PATH").orEmpty()
            .split(File.pathSeparator)
            .any { dir -> dir.isNotBlank() && File(dir, name).isFile }

        when {
            onPath("deno.exe") -> emptyList()
            onPath("node.exe") -> listOf("--js-runtimes", "node")
            onPath("bun.exe") -> listOf("--js-runtimes", "bun")
            else -> emptyList()
        }
    }

    private companion object {
        const val DOWNLOAD_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"

        // YouTube stream URLs expire after roughly six hours
        const val URL_CACHE_MS = 4 * 60 * 60 * 1000L

        // Each yt-dlp run is a separate process; more than this at once just slows everything down
        const val MAX_PARALLEL_PREFETCH = 3
    }
}
