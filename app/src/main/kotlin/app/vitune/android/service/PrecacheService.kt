package app.vitune.android.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import app.vitune.android.Database
import app.vitune.android.R
import app.vitune.android.transaction
import app.vitune.android.utils.ActionReceiver
import app.vitune.android.utils.download
import app.vitune.android.utils.intent
import app.vitune.android.utils.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.NavigableSet
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private val executor = Executors.newCachedThreadPool()
private val coroutineScope = CoroutineScope(
    executor.asCoroutineDispatcher() +
        SupervisorJob() +
        CoroutineName("PrecacheService-Worker-Scope")
)

// While the class is not a singleton (lifecycle), there should only be one download state at a time
private val mutableDownloadState = MutableStateFlow(false)
val downloadState = mutableDownloadState.asStateFlow()

private const val DOWNLOAD_NOTIFICATION_UPDATE_INTERVAL = 1000L // default
private const val DOWNLOAD_WORK_NAME = "precacher-work"

@OptIn(UnstableApi::class)
class PrecacheService : DownloadService(
    /* foregroundNotificationId             = */ ServiceNotifications.download.notificationId!!,
    /* foregroundNotificationUpdateInterval = */ DOWNLOAD_NOTIFICATION_UPDATE_INTERVAL,
    /* channelId                            = */ ServiceNotifications.download.id,
    /* channelNameResourceId                = */ R.string.pre_cache,
    /* channelDescriptionResourceId         = */ 0
) {
    private val downloadQueue =
        Channel<DownloadManager>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val downloadNotificationHelper by lazy {
        DownloadNotificationHelper(
            /* context = */ this,
            /* channelId = */ ServiceNotifications.download.id
        )
    }

    private val notificationActionReceiver = NotificationActionReceiver()

    private val waiters = mutableListOf<() -> Unit>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is PlayerService.Binder) return
            bound = true
            binder = service
            waiters.forEach { it() }
            waiters.clear()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            binder = null
            waiters.forEach { it() }
            waiters.clear()
        }
    }

    inner class NotificationActionReceiver : ActionReceiver("app.vitune.android.precache") {
        val cancel by action { context, _ ->
            runCatching {
                sendPauseDownloads(
                    /* context         = */ context,
                    /* clazz           = */ PrecacheService::class.java,
                    /* foreground      = */ true
                )
            }.recoverCatching {
                sendPauseDownloads(
                    /* context         = */ context,
                    /* clazz           = */ PrecacheService::class.java,
                    /* foreground      = */ false
                )
            }
        }
    }

    @get:Synchronized
    @set:Synchronized
    private var bound = false
    private var binder: PlayerService.Binder? = null

    private var progressUpdaterJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        notificationActionReceiver.register(flags = ContextCompat.RECEIVER_EXPORTED)
        mutableDownloadState.update { false }
    }

    @kotlin.OptIn(FlowPreview::class)
    override fun getDownloadManager(): DownloadManager {
        runCatching {
            if (bound) unbindService(serviceConnection)
            bindService(intent<PlayerService>(), serviceConnection, BIND_AUTO_CREATE)
        }.exceptionOrNull()?.let {
            it.printStackTrace()
            toast(getString(R.string.error_pre_cache))
        }

        val cache = BlockingDeferredCache {
            suspendCancellableCoroutine {
                waiters += { it.resume(Unit) }
            }
            binder?.cache ?: run {
                toast(getString(R.string.error_pre_cache))
                error("PlayerService failed to start, crashing...")
            }
        }

        progressUpdaterJob?.cancel()
        progressUpdaterJob = coroutineScope.launch {
            downloadQueue
                .receiveAsFlow()
                .debounce(100.milliseconds)
                .collect { downloadManager ->
                    mutableDownloadState.update { !downloadManager.isIdle }
                }
        }

        return DownloadManager(
            /* context = */ this,
            /* databaseProvider = */ PlayerService.createDatabaseProvider(this),
            /* cache = */ cache,
            /* upstreamFactory = */ PlayerService.createYouTubeDataSourceResolverFactory(
                findMediaItem = { null },
                context = this,
                cache = cache,
                chunkLength = null
            ),
            /* executor = */ executor
        ).apply {
            maxParallelDownloads = 3
            minRetryCount = 1
            requirements = Requirements(Requirements.NETWORK)

            addListener(
                object : DownloadManager.Listener {
                    override fun onIdle(downloadManager: DownloadManager) =
                        mutableDownloadState.update { false }

                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?
                    ) = downloadQueue.trySend(downloadManager).let { }

                    override fun onDownloadRemoved(
                        downloadManager: DownloadManager,
                        download: Download
                    ) = downloadQueue.trySend(downloadManager).let { }
                }
            )
        }
    }

    override fun getScheduler() = WorkManagerScheduler(this, DOWNLOAD_WORK_NAME)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ) = NotificationCompat
        .Builder(
            /* context = */ this,
            /* notification = */ downloadNotificationHelper.buildProgressNotification(
                /* context            = */ this,
                /* smallIcon          = */ R.drawable.download,
                /* contentIntent      = */ null,
                /* message            = */ null,
                /* downloads          = */ downloads,
                /* notMetRequirements = */ notMetRequirements
            )
        )
        .setChannelId(ServiceNotifications.download.id)
        .addAction(
            NotificationCompat.Action.Builder(
                /* icon = */ R.drawable.close,
                /* title = */ getString(R.string.cancel),
                /* intent = */ notificationActionReceiver.cancel.pendingIntent
            ).build()
        )
        .build()

    override fun onDestroy() {
        super.onDestroy()

        runCatching {
            if (bound) unbindService(serviceConnection)
        }

        unregisterReceiver(notificationActionReceiver)
        mutableDownloadState.update { false }
    }

    companion object {
        fun scheduleCache(context: Context, mediaItem: MediaItem) {
            if (mediaItem.isLocal) return

            val downloadRequest = DownloadRequest
                .Builder(
                    /* id      = */ mediaItem.mediaId,
                    /* uri     = */ mediaItem.requestMetadata.mediaUri
                        ?: "https://youtube.com/watch?v=${mediaItem.mediaId}".toUri()
                )
                .setCustomCacheKey(mediaItem.mediaId)
                .setData(mediaItem.mediaId.encodeToByteArray())
                .build()

            transaction {
                runCatching {
                    Database.insert(mediaItem)
                }.also { if (it.isFailure) return@transaction }

                coroutineScope.launch {
                    context.download<PrecacheService>(downloadRequest).exceptionOrNull()?.let {
                        if (it is CancellationException) throw it

                        it.printStackTrace()
                        context.toast(context.getString(R.string.error_pre_cache))
                    }
                }
            }
        }
    }
}

@Suppress("TooManyFunctions")
@OptIn(UnstableApi::class)
class BlockingDeferredCache(private val cache: Deferred<Cache>) : Cache {
    constructor(init: suspend () -> Cache) : this(coroutineScope.async { init() })

    private val resolvedCache by lazy { runBlocking { cache.await() } }

    override fun getUid() = resolvedCache.uid
    override fun release() = resolvedCache.release()
    override fun addListener(key: String, listener: Cache.Listener): NavigableSet<CacheSpan> =
        resolvedCache.addListener(key, listener)

    override fun removeListener(key: String, listener: Cache.Listener) =
        resolvedCache.removeListener(key, listener)

    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> =
        resolvedCache.getCachedSpans(key)

    override fun getKeys(): MutableSet<String> = resolvedCache.keys
    override fun getCacheSpace() = resolvedCache.cacheSpace
    override fun startReadWrite(key: String, position: Long, length: Long): CacheSpan =
        resolvedCache.startReadWrite(key, position, length)

    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long) =
        resolvedCache.startReadWriteNonBlocking(key, position, length)

    override fun startFile(key: String, position: Long, length: Long): File =
        resolvedCache.startFile(key, position, length)

    override fun commitFile(file: File, length: Long) = resolvedCache.commitFile(file, length)
    override fun releaseHoleSpan(holeSpan: CacheSpan) = resolvedCache.releaseHoleSpan(holeSpan)
    override fun removeResource(key: String) = resolvedCache.removeResource(key)
    override fun removeSpan(span: CacheSpan) = resolvedCache.removeSpan(span)
    override fun isCached(key: String, position: Long, length: Long) =
        resolvedCache.isCached(key, position, length)

    override fun getCachedLength(key: String, position: Long, length: Long) =
        resolvedCache.getCachedLength(key, position, length)

    override fun getCachedBytes(key: String, position: Long, length: Long) =
        resolvedCache.getCachedBytes(key, position, length)

    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) =
        resolvedCache.applyContentMetadataMutations(key, mutations)

    override fun getContentMetadata(key: String) = resolvedCache.getContentMetadata(key)
}
