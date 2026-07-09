package app.vitune.android.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

interface TimerJob {
    val millisLeft: StateFlow<Long?>
    fun cancel()
}

fun CoroutineScope.timer(delayMillis: Long, onCompletion: () -> Unit): TimerJob {
    val end = System.currentTimeMillis() + delayMillis
    val millisLeft = MutableStateFlow<Long?>(delayMillis)
    val job = launch {
        while (isActive && millisLeft.value != null) {
            delay(1000.milliseconds)
            millisLeft.emit((end - System.currentTimeMillis()).takeIf { it > 0 })
        }
    }
    val disposableHandle = job.invokeOnCompletion { if (it == null) onCompletion() }

    return object : TimerJob {
        override val millisLeft get() = millisLeft.asStateFlow()

        override fun cancel() {
            millisLeft.value = null
            disposableHandle.dispose()
            job.cancel()
        }
    }
}
