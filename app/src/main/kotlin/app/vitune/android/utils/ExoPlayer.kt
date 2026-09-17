@file:OptIn(UnstableApi::class)

package app.vitune.android.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import app.vitune.android.service.isLocal
import app.vitune.core.data.utils.StreamUrlCache
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

class RangeHandlerDataSourceFactory(private val parent: DataSource.Factory) : DataSource.Factory {
    class Source(private val parent: DataSource) : DataSource by parent {
        override fun open(dataSpec: DataSpec) = runCatching {
            parent.open(dataSpec)
        }.getOrElse { e ->
            if (
                e.findCause<EOFException>() != null ||
                e.findCause<InvalidResponseCodeException>()?.responseCode == 416
            ) parent.open(
                dataSpec
                    .buildUpon()
                    .setHttpRequestHeaders(
                        dataSpec.httpRequestHeaders.filter {
                            it.key.equals("range", ignoreCase = true)
                        }
                    )
                    .setLength(C.LENGTH_UNSET.toLong())
                    .build()
            )
            else throw e
        }

        override fun getResponseHeaders(): Map<String, List<String>> = parent.responseHeaders
    }

    override fun createDataSource() = Source(parent.createDataSource())
}

class CatchingDataSourceFactory(
    private val parent: DataSource.Factory,
    private val onError: ((Throwable) -> Unit)?
) : DataSource.Factory {
    inner class Source(private val parent: DataSource) : DataSource by parent {
        override fun open(dataSpec: DataSpec) = runCatching {
            parent.open(dataSpec)
        }.getOrElse { ex ->
            ex.printStackTrace()

            if (ex is PlaybackException) throw ex
            else throw PlaybackException(
                /* message = */ "Unknown playback error",
                /* cause = */ ex,
                /* errorCode = */ PlaybackException.ERROR_CODE_UNSPECIFIED
            ).also { onError?.invoke(it) }
        }

        override fun getResponseHeaders(): Map<String, List<String>> = parent.responseHeaders
    }

    override fun createDataSource() = Source(parent.createDataSource())
}

fun DataSource.Factory.handleRangeErrors(): DataSource.Factory = RangeHandlerDataSourceFactory(this)
fun DataSource.Factory.handleUnknownErrors(
    onError: ((Throwable) -> Unit)? = null
): DataSource.Factory = CatchingDataSourceFactory(
    parent = this,
    onError = onError
)

class FallbackDataSourceFactory(
    private val upstream: DataSource.Factory,
    private val fallback: DataSource.Factory
) : DataSource.Factory {
    inner class Source(private val parent: DataSource) : DataSource by parent {
        override fun open(dataSpec: DataSpec) = runCatching {
            parent.open(dataSpec)
        }.getOrElse { ex ->
            ex.printStackTrace()

            runCatching {
                fallback.createDataSource().open(dataSpec)
            }.getOrElse { fallbackEx ->
                fallbackEx.printStackTrace()

                throw ex
            }
        }

        override fun getResponseHeaders(): Map<String, List<String>> = parent.responseHeaders
    }

    override fun createDataSource() = Source(upstream.createDataSource())
}

fun DataSource.Factory.withFallback(
    fallbackFactory: DataSource.Factory
): DataSource.Factory = FallbackDataSourceFactory(this, fallbackFactory)

fun DataSource.Factory.withFallback(
    context: Context,
    resolver: ResolvingDataSource.Resolver
) = withFallback(ResolvingDataSource.Factory(DefaultDataSource.Factory(context), resolver))

class StreamCandidateDataSourceFactory(
    private val base: DataSource.Factory,
    private val streamUrlCache: StreamUrlCache
) : DataSource.Factory {
    override fun createDataSource() = object : DataSource {
        private var source: DataSource? = null
        private val transferListeners = mutableListOf<TransferListener>()
        private val open = AtomicBoolean(false)

        private fun openSource(dataSpec: DataSpec): Long {
            val s = base.createDataSource()

            source = s
            transferListeners.forEach { s.addTransferListener(it) }
            open.set(true)

            return s.open(dataSpec)
        }

        override fun open(dataSpec: DataSpec): Long {
            val mediaId = dataSpec.key?.removePrefix("https://youtube.com/watch?v=")

            if (mediaId == null || dataSpec.isLocal) return openSource(dataSpec)

            var lastError: IOException? = null

            while (true) {
                try {
                    return openSource(dataSpec)
                } catch (e: IOException) {
                    lastError = e

                    Log.w(TAG, "Stream candidate failed for $mediaId, trying next: $e")

                    source?.close()

                    val now = System.currentTimeMillis()
                    streamUrlCache.current(mediaId, now)?.let { candidate ->
                        streamUrlCache.markFailed(mediaId, candidate)
                    }

                    if (streamUrlCache.hasRemaining(mediaId, now)) continue

                    throw lastError
                }
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int) =
            requireNotNull(source).read(buffer, offset, length)

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
            source?.addTransferListener(transferListener)
        }

        override fun getResponseHeaders(): Map<String, List<String>> =
            requireNotNull(source).responseHeaders

        override fun getUri(): Uri? = source?.uri

        override fun close() {
            if (open.compareAndSet(true, false)) {
                source?.close()
                source = null
            }
        }
    }
}

class RetryingDataSourceFactory(
    private val parent: DataSource.Factory,
    private val maxRetries: Int,
    private val printStackTrace: Boolean,
    private val exponential: Boolean,
    private val predicate: (Throwable) -> Boolean
) : DataSource.Factory {
    inner class Source(private val parent: DataSource) : DataSource by parent {
        override fun open(dataSpec: DataSpec): Long {
            var lastException: Throwable? = null
            var retries = 0
            while (retries < maxRetries) {
                if (retries > 0) Log.d(TAG, "Retry $retries of $maxRetries fetching datasource")

                @Suppress("TooGenericExceptionCaught")
                return try {
                    parent.open(dataSpec)
                } catch (ex: Throwable) {
                    lastException = ex
                    if (printStackTrace) Log.e(
                        /* tag = */ TAG,
                        /* msg = */ "Exception caught by retry mechanism",
                        /* tr = */ ex
                    )
                    if (predicate(ex)) {
                        val time = if (exponential) 1000L * 2.0.pow(retries).toLong() else 2500L
                        Log.d(TAG, "Retry policy accepted retry, sleeping for $time milliseconds")
                        Thread.sleep(time)
                        retries++
                        continue
                    }
                    Log.e(
                        TAG,
                        "Retry policy declined retry, throwing the last exception..."
                    )
                    throw ex
                }
            }
            Log.e(
                TAG,
                "Max retries $maxRetries exceeded, throwing the last exception..."
            )
            throw lastException!!
        }

        override fun getResponseHeaders(): Map<String, List<String>> = parent.responseHeaders
    }

    override fun createDataSource() = Source(parent.createDataSource())
}

// TODO: fix this mess
inline fun <reified T : Throwable> DataSource.Factory.retryIf(
    maxRetries: Int = 5,
    printStackTrace: Boolean = false,
    exponential: Boolean = true
) = retryIf(maxRetries, printStackTrace, exponential) { ex -> ex.findCause<T>() != null }

private const val TAG = "DataSource.Factory"

fun DataSource.Factory.retryIf(
    maxRetries: Int = 5,
    printStackTrace: Boolean = false,
    exponential: Boolean = true,
    predicate: (Throwable) -> Boolean
): DataSource.Factory = RetryingDataSourceFactory(this, maxRetries, printStackTrace, exponential, predicate)

val Cache.asDataSource: CacheDataSource.Factory get() = CacheDataSource.Factory().setCache(this)

val Context.defaultDataSource
    get() = DefaultDataSource.Factory(
        this,
        DefaultHttpDataSource.Factory().setConnectTimeoutMs(16000)
            .setReadTimeoutMs(8000)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0")
    )
