package app.vitune.android.utils

import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val executor = Executors.newCachedThreadPool()
private val coroutineScope = CoroutineScope(
    executor.asCoroutineDispatcher() + SupervisorJob() + CoroutineName("androidx-credentials-util")
)

@Suppress("CoroutineContextWithJob") // SupervisorJob is intentional here
private suspend inline fun <T> wrapper(
    crossinline block: (cont: CancellableContinuation<T>) -> Unit
): T = withContext(coroutineScope.coroutineContext) {
    runCatching {
        suspendCancellableCoroutine { cont ->
            runCatching {
                block(cont)
            }.exceptionOrNull()?.let {
                if (it is CancellationException) cont.cancel() else cont.resumeWithException(it)
            }
        }
    }.let {
        it.exceptionOrNull()?.printStackTrace()
        it.getOrThrow()
    }
}

private inline fun <
    reified Response,
    reified Exception : Throwable,
    reified CancellationException : Exception
    > callback(
    cont: CancellableContinuation<Response?>
) = object : CredentialManagerCallback<Response, Exception> {
    override fun onError(e: Exception) {
        when (e) {
            is CancellationException -> cont.cancel(e)
            is NoCredentialException -> cont.resume(null)
            else -> cont.resumeWithException(e)
        }
    }

    override fun onResult(result: Response) = cont.resume(result)
}

suspend fun CredentialManager.upsert(
    context: Context,
    username: String,
    password: String
) = wrapper { cont ->
    createCredentialAsync(
        context = context,
        request = CreatePasswordRequest(
            id = username,
            password = password
        ),
        cancellationSignal = cont.asCancellationSignal,
        executor = executor,
        callback = callback<_, _, CreateCredentialCancellationException>(cont)
    )
}

suspend fun CredentialManager.get(context: Context) = wrapper { cont ->
    getCredentialAsync(
        context = context,
        request = GetCredentialRequest(listOf(GetPasswordOption())),
        cancellationSignal = cont.asCancellationSignal,
        executor = executor,
        callback = callback<_, _, GetCredentialCancellationException>(cont)
    )
}.let { runCatching { it?.credential as? PasswordCredential }.getOrNull() }
