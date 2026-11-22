package app.vitune.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.vitune.desktop.model.MusicRepository
import app.vitune.desktop.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RepeatMode {
    OFF, ALL, ONE
}

/** A pending "stop playing" request. */
sealed interface SleepTimer {
    data class At(val endsAtMs: Long) : SleepTimer
    data object EndOfTrack : SleepTimer
}

/**
 * Owns the play queue and playback state. All public functions and state are meant
 * to be used from the UI thread; VLC callbacks are forwarded there.
 */
class PlayerController(
    private val scope: CoroutineScope,
    private val engine: AudioEngine,
    private val ytDlp: YtDlp,
    var onSongStarted: ((Song) -> Unit)? = null
) {
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var currentIndex by mutableStateOf(-1)
        private set
    val current: Song? get() = queue.getOrNull(currentIndex)
    val upcoming: List<Song> get() = if (currentIndex < 0) queue else queue.drop(currentIndex + 1)
    val hasNext: Boolean get() = currentIndex < queue.lastIndex || repeatMode == RepeatMode.ALL

    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var volume by mutableStateOf(80)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var isShuffled by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(RepeatMode.OFF)
        private set
    var playbackSpeed by mutableStateOf(1f)
        private set
    var equalizerPreset by mutableStateOf<String?>(null)
        private set
    var sleepTimer by mutableStateOf<SleepTimer?>(null)
        private set

    /** When the queue runs out, keep going with similar songs. */
    var autoplay by mutableStateOf(true)

    val equalizerPresets: List<String> get() = engine.equalizerPresets

    private var loadJob: Job? = null
    private var radioJob: Job? = null
    private var sleepJob: Job? = null
    private var reachedEnd = false

    /** How many times VLC failed to play each song's current stream link. */
    private val playbackFailures = mutableMapOf<String, Int>()

    /** Where to continue after reloading a stream that failed mid-song. */
    private var resumeAtMs: Long? = null

    private companion object {
        const val MAX_LOAD_ATTEMPTS = 3
        const val MAX_PLAYBACK_RETRIES = 2
    }

    /** Queue order before shuffling, so turning shuffle off restores it. */
    private var unshuffledQueue: List<Song>? = null

    init {
        engine.listener = object : AudioEngine.Listener {
            override fun onPlaying() {
                onUi {
                    isPlaying = true
                    isLoading = false
                    applyAudioSettings()
                    resumeAtMs?.let { engine.seek(it) }
                    resumeAtMs = null
                }
            }

            override fun onPaused() {
                onUi { isPlaying = false }
            }

            override fun onFinished() {
                onUi { onTrackEnded() }
            }

            override fun onError() {
                onUi {
                    val song = current ?: return@onUi
                    // Usually an expired or rejected stream link: quietly fetch a fresh one and try again
                    ytDlp.invalidate(song.id)
                    val attempts = (playbackFailures[song.id] ?: 0) + 1
                    playbackFailures[song.id] = attempts
                    if (attempts <= MAX_PLAYBACK_RETRIES) {
                        val resumeAt = positionMs
                        startCurrent(isRetry = true)
                        resumeAtMs = resumeAt.takeIf { it > 2_000 }
                        return@onUi
                    }
                    error = "Couldn't play \"${song.title}\" — skipping to the next song."
                    isLoading = false
                    isPlaying = false
                    val failedIndex = currentIndex
                    scope.launch {
                        delay(1500)
                        // Don't skip if the user picked another song in the meantime
                        if (currentIndex == failedIndex && !isPlaying && !isLoading) skipFailed()
                    }
                }
            }

            override fun onTime(ms: Long) {
                onUi { positionMs = ms }
            }

            override fun onLength(ms: Long) {
                onUi { if (ms > 0) durationMs = ms }
            }
        }
    }

    // Starting playback

    /** Plays [song] and fills the queue with YouTube Music's radio for it, like tapping a song in ViTune. */
    fun playRadio(song: Song) {
        unshuffledQueue = null
        isShuffled = false
        queue = listOf(song)
        currentIndex = 0
        startCurrent()
        appendRadio(song)
    }

    /** Plays a list (playlist, liked songs, search results) starting at [startIndex]. */
    fun playCollection(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        radioJob?.cancel()
        val start = startIndex.coerceIn(songs.indices)
        if (shuffle) {
            val first = if (startIndex == 0) songs.random() else songs[start]
            unshuffledQueue = songs
            queue = listOf(first) + songs.filter { it.id != first.id }.shuffled()
            currentIndex = 0
            isShuffled = true
        } else {
            unshuffledQueue = null
            isShuffled = false
            queue = songs.distinctBy { it.id }
            currentIndex = queue.indexOfFirst { it.id == songs[start].id }.coerceAtLeast(0)
        }
        startCurrent()
    }

    fun playAt(index: Int) {
        if (index !in queue.indices) return
        currentIndex = index
        startCurrent()
    }

    // Queue editing

    fun playNext(song: Song) {
        val playing = current ?: return playRadio(song)
        if (playing.id == song.id) return
        val withoutSong = queue.filter { it.id != song.id }
        val insertAt = withoutSong.indexOf(playing) + 1
        queue = withoutSong.toMutableList().apply { add(insertAt, song) }
        currentIndex = queue.indexOf(playing)
    }

    fun addToQueue(song: Song) {
        if (current == null) return playRadio(song)
        if (queue.none { it.id == song.id }) queue = queue + song
    }

    fun addAllToQueue(songs: List<Song>) {
        if (current == null) return playCollection(songs)
        val known = queue.map { it.id }.toMutableSet()
        queue = queue + songs.filter { known.add(it.id) }
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices || index == currentIndex) return
        queue = queue.toMutableList().apply { removeAt(index) }
        if (index < currentIndex) currentIndex--
    }

    /** Moves a queue entry; the playing song keeps playing wherever it ends up. */
    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val playing = current
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        currentIndex = playing?.let { p -> queue.indexOfFirst { it.id == p.id } } ?: -1
    }

    fun clearUpcoming() {
        if (currentIndex < 0) return
        radioJob?.cancel()
        queue = queue.take(currentIndex + 1)
    }

    // Transport

    fun togglePlayPause() {
        when {
            current == null || isLoading -> Unit
            reachedEnd -> startCurrent()
            isPlaying -> engine.pause()
            else -> engine.resume()
        }
    }

    fun pause() {
        if (isPlaying) engine.pause()
    }

    fun next() {
        when {
            currentIndex < queue.lastIndex -> {
                currentIndex++
                startCurrent()
            }
            repeatMode == RepeatMode.ALL && queue.isNotEmpty() -> {
                currentIndex = 0
                startCurrent()
            }
        }
    }

    fun previous() {
        if (positionMs > 3_000 || currentIndex <= 0) {
            seekTo(0)
        } else {
            currentIndex--
            startCurrent()
        }
    }

    fun seekTo(ms: Long) {
        if (current == null) return
        val target = if (durationMs > 0) ms.coerceIn(0, durationMs) else ms.coerceAtLeast(0)
        engine.seek(target)
        positionMs = target
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    // Audio settings

    fun changeVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        if (volume > 0) isMuted = false
        applyAudioSettings()
    }

    fun toggleMute() {
        isMuted = !isMuted
        applyAudioSettings()
    }

    fun changePlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2f)
        engine.setRate(playbackSpeed)
    }

    fun changeEqualizerPreset(name: String?) {
        equalizerPreset = name
        engine.setEqualizerPreset(name)
    }

    private fun applyAudioSettings() {
        engine.setVolume(if (isMuted) 0 else volume)
        engine.setRate(playbackSpeed)
    }

    // Shuffle & repeat

    fun toggleShuffle() {
        val playing = current
        if (!isShuffled) {
            unshuffledQueue = queue
            val head = queue.take(currentIndex + 1)
            queue = head + queue.drop(currentIndex + 1).shuffled()
        } else {
            val original = unshuffledQueue
            if (original != null) {
                val present = queue.map { it.id }.toSet()
                val restored = original.filter { it.id in present }
                val added = queue.filter { song -> original.none { it.id == song.id } }
                queue = restored + added
            }
            unshuffledQueue = null
        }
        currentIndex = playing?.let { p -> queue.indexOfFirst { it.id == p.id } } ?: -1
        isShuffled = !isShuffled
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    // Sleep timer

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        sleepTimer = SleepTimer.At(endsAt)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            pause()
            sleepTimer = null
        }
    }

    fun sleepAtEndOfTrack() {
        sleepJob?.cancel()
        sleepTimer = SleepTimer.EndOfTrack
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepTimer = null
    }

    // Internals

    private fun appendRadio(seed: Song, thenPlayNext: Boolean = false) {
        radioJob?.cancel()
        radioJob = scope.launch {
            val related = runCatching { MusicRepository.radio(seed.id) }.getOrDefault(emptyList())
            // Only append if the seed is still part of the queue (the user hasn't started something else)
            if (queue.none { it.id == seed.id }) return@launch
            val known = queue.map { it.id }.toMutableSet()
            val fresh = related.filter { known.add(it.id) }
            queue = queue + if (isShuffled) fresh.shuffled() else fresh
            if (thenPlayNext && fresh.isNotEmpty()) next() else prefetchNext()
        }
    }

    private fun onTrackEnded() {
        if (sleepTimer == SleepTimer.EndOfTrack) {
            sleepTimer = null
            isPlaying = false
            reachedEnd = true
            return
        }
        when {
            repeatMode == RepeatMode.ONE -> startCurrent()
            currentIndex < queue.lastIndex -> next()
            repeatMode == RepeatMode.ALL && queue.isNotEmpty() -> {
                currentIndex = 0
                startCurrent()
            }
            autoplay && current != null -> {
                isPlaying = false
                reachedEnd = true
                appendRadio(current!!, thenPlayNext = true)
            }
            else -> {
                isPlaying = false
                reachedEnd = true
            }
        }
    }

    private fun startCurrent(isRetry: Boolean = false) {
        val song = current ?: return
        loadJob?.cancel()
        engine.stop()

        reachedEnd = false
        isLoading = true
        isPlaying = false
        positionMs = 0
        durationMs = song.durationMs
        error = null

        if (!isRetry) {
            resumeAtMs = null
            playbackFailures.remove(song.id)
            onSongStarted?.invoke(song)

            // Keep a little runway of similar songs when nearing the end of the queue
            if (autoplay && currentIndex >= queue.lastIndex - 1 && radioJob?.isActive != true) {
                appendRadio(song)
            }
        }

        loadJob = scope.launch {
            var lastException: Exception? = null

            // Retries are silent: the spinner on the play button already shows we're working on it
            repeat(MAX_LOAD_ATTEMPTS) { attempt ->
                try {
                    val url = ytDlp.streamUrl(song.id)
                    engine.play(url)
                    prefetchNext()
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                    System.err.println("yt-dlp failed for ${song.id} (attempt ${attempt + 1}): ${e.message}")
                    ytDlp.invalidate(song.id)
                    if (attempt < MAX_LOAD_ATTEMPTS - 1) delay(700L * (attempt + 1))
                }
            }

            isLoading = false
            error = "Can't play \"${song.title}\": ${lastException?.message ?: "unknown error"}"
            delay(2500)
            if (current?.id == song.id && !isPlaying && !isLoading) skipFailed()
        }
    }

    /** Starts resolving a song's stream in the background (e.g. when the pointer hovers it). */
    fun prefetch(song: Song) {
        if (song.id != current?.id) ytDlp.prefetch(song.id)
    }

    /** Moves past a song that couldn't be played (never repeats it). */
    private fun skipFailed() {
        if (currentIndex < queue.lastIndex) next() else reachedEnd = true
    }

    /** Resolves the next songs' stream URLs early so skipping or autoplay starts instantly. */
    private fun prefetchNext() {
        queue.drop(currentIndex + 1).take(2).forEach { ytDlp.prefetch(it.id) }
    }

    private fun onUi(block: suspend () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }
}
